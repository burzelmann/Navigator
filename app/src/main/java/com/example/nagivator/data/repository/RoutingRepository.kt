package com.example.nagivator.data.repository

import android.location.Location
import android.util.Log
import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.data.remote.BRouterApi
import com.example.nagivator.data.remote.GeocodingApi
import com.example.nagivator.data.remote.OsrmRoute
import com.example.nagivator.data.remote.RoutingApi
import com.example.nagivator.domain.model.GermanManeuverTranslator
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.RouteStep
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

import com.example.nagivator.domain.model.SpeedPreference

class RoutingRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val mediaType = MediaType.get("application/json")
    private val okHttpClient = OkHttpClient.Builder().build()

    private val brouterApi: BRouterApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://brouter.de/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(mediaType))
            .build()
            .create(BRouterApi::class.java)
    }

    private val routingApi: RoutingApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://router.project-osrm.org/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(mediaType))
            .build()
            .create(RoutingApi::class.java)
    }

    private val geocodingApi: GeocodingApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://photon.komoot.io/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(mediaType))
            .build()
            .create(GeocodingApi::class.java)
    }

    suspend fun getRoute(
        waypoints: List<LocationPoint>,
        mode: TravelMode,
        footSpeedPref: SpeedPreference = SpeedPreference.NORMAL,
        carSpeedPref: SpeedPreference = SpeedPreference.NORMAL
    ): Result<RouteResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (waypoints.size < 2) {
                throw IllegalArgumentException("Mindestens 2 Wegpunkte erforderlich")
            }

            // 1. For FOOT mode, try BRouter Hiking engine first
            if (mode == TravelMode.FOOT) {
                val brouterResult = getBRouterRoute(waypoints, footSpeedPref)
                if (brouterResult != null && brouterResult.geometryPoints.size >= 2) {
                    return@runCatching brouterResult
                }
            }

            // 2. Try OSRM engine
            try {
                val osrmProfile = if (mode == TravelMode.FOOT) "foot" else "driving"
                val coordsStr = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }

                val response = routingApi.getRoute(
                    profile = osrmProfile,
                    coordinates = coordsStr
                )
                val firstRoute = response.routes.firstOrNull()
                if (firstRoute != null) {
                    return@runCatching parseOsrmRoute(firstRoute, waypoints, mode, footSpeedPref, carSpeedPref)
                }
            } catch (_: Exception) {
                // proceed to fallback
            }

            // 3. Fail-safe Geodesic fallback
            getGeodesicFallbackRoute(waypoints, mode, footSpeedPref, carSpeedPref)
        }
    }

    /**
     * Attempts to compute an alternative driving route that avoids [blockedPoint] (e.g. an Autobahn closure).
     * First evaluates OSRM `alternatives=true`; if all alternatives pass the closure, computes a lateral detour via-point.
     */
    suspend fun getAlternativeRouteAvoiding(
        waypoints: List<LocationPoint>,
        blockedPoint: LocationPoint,
        mode: TravelMode = TravelMode.CAR,
        carSpeedPref: SpeedPreference = SpeedPreference.NORMAL,
        avoidanceRadiusMeters: Float = 220f
    ): Result<RouteResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (waypoints.size < 2) throw IllegalArgumentException("Mindestens 2 Wegpunkte erforderlich")
            val osrmProfile = if (mode == TravelMode.FOOT) "foot" else "driving"
            val coordsStr = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }

            // 1. Try OSRM alternatives=true
            val response = routingApi.getRoute(
                profile = osrmProfile,
                coordinates = coordsStr,
                alternatives = true
            )
            val parsedAlternatives = response.routes.map {
                parseOsrmRoute(it, waypoints, mode, SpeedPreference.NORMAL, carSpeedPref)
            }
            val cleanAlternative = selectRouteAvoidingPoint(parsedAlternatives, blockedPoint, avoidanceRadiusMeters)
            if (cleanAlternative != null) {
                return@runCatching cleanAlternative
            }

            // 2. Fallback: lateral detour via-point (~3.5 km perpendicular to start->dest at blockedPoint)
            val start = waypoints.first()
            val dest = waypoints.last()
            val detourVia = computeDetourViaPoint(start, dest, blockedPoint)
            val detourWaypoints = listOf(start, detourVia, dest)
            val detourCoords = detourWaypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
            val detourResp = routingApi.getRoute(profile = osrmProfile, coordinates = detourCoords)
            val detourRoute = detourResp.routes.firstOrNull()
                ?: throw IllegalStateException("Keine Ausweichroute verfügbar")
            parseOsrmRoute(detourRoute, detourWaypoints, mode, SpeedPreference.NORMAL, carSpeedPref)
        }
    }

    private fun parseOsrmRoute(
        osrmRoute: OsrmRoute,
        waypoints: List<LocationPoint>,
        mode: TravelMode,
        footSpeedPref: SpeedPreference,
        carSpeedPref: SpeedPreference
    ): RouteResult {
        val geomPoints = osrmRoute.geometry?.coordinates?.map { coord ->
            LocationPoint(latitude = coord[1], longitude = coord[0])
        } ?: emptyList()

        val steps = osrmRoute.legs.flatMap { leg ->
            leg.steps.map { step ->
                val maneuverName = GermanManeuverTranslator.translate(
                    type = step.maneuver?.type ?: "",
                    modifier = step.maneuver?.modifier ?: "",
                    rawName = step.name
                )
                val maneuverLocCoords = step.maneuver?.location
                val maneuverPoint = if (maneuverLocCoords != null && maneuverLocCoords.size >= 2) {
                    LocationPoint(latitude = maneuverLocCoords[1], longitude = maneuverLocCoords[0])
                } else null

                RouteStep(
                    instruction = maneuverName,
                    distanceMeters = step.distance,
                    durationSeconds = step.duration,
                    maneuverType = step.maneuver?.type ?: "",
                    maneuverModifier = step.maneuver?.modifier ?: "",
                    maneuverLocation = maneuverPoint
                )
            }
        }

        val calcDur = if (mode == TravelMode.FOOT) {
            osrmRoute.distance / footSpeedPref.footSpeedMps
        } else {
            osrmRoute.duration * carSpeedPref.carTimeFactor
        }

        return RouteResult(
            waypoints = waypoints,
            geometryPoints = geomPoints,
            totalDistanceMeters = osrmRoute.distance,
            totalDurationSeconds = calcDur,
            mode = mode,
            steps = steps
        )
    }

    companion object {
        fun selectRouteAvoidingPoint(
            candidates: List<RouteResult>,
            blockedPoint: LocationPoint,
            avoidanceRadiusMeters: Float = 220f
        ): RouteResult? {
            return candidates
                .filter { route ->
                    route.geometryPoints.size >= 2 &&
                        CarNavigationHelper.distanceToPolyline(blockedPoint, route.geometryPoints).first > avoidanceRadiusMeters
                }
                .minByOrNull { it.totalDurationSeconds }
        }

        fun computeDetourViaPoint(
            start: LocationPoint,
            dest: LocationPoint,
            blocked: LocationPoint,
            offsetDeg: Double = 0.032
        ): LocationPoint {
            val dLat = dest.latitude - start.latitude
            val dLon = dest.longitude - start.longitude
            val len = kotlin.math.hypot(dLat, dLon).coerceAtLeast(1e-5)
            // Perpendicular unit vector
            val perpLat = -dLon / len
            val perpLon = dLat / len
            return LocationPoint(
                latitude = blocked.latitude + perpLat * offsetDeg,
                longitude = blocked.longitude + perpLon * offsetDeg,
                name = "Ausweichpunkt"
            )
        }
    }

    private suspend fun getBRouterRoute(
        waypoints: List<LocationPoint>,
        footSpeedPref: SpeedPreference = SpeedPreference.NORMAL
    ): RouteResult? {
        val lonlatsStr = waypoints.joinToString("|") { "${it.longitude},${it.latitude}" }
        Log.d("NagivatorRouting", "Calling BRouter with lonlats: $lonlatsStr")
        
        for (profileName in listOf("trekking", "foot")) {
            try {
                val response = brouterApi.getRoute(lonlats = lonlatsStr, profile = profileName)
                val feature = response.features.firstOrNull() ?: continue
                
                val rawCoords = feature.geometry.coordinates
                val geomPoints = rawCoords.map { coord ->
                    LocationPoint(latitude = coord[1], longitude = coord[0])
                }
                if (geomPoints.size < 2) continue

                var ascent = 0.0
                var descent = 0.0
                var accumulatedDist = 0.0
                val elePoints = mutableListOf<Pair<Double, Double>>()
                val distResults = FloatArray(1)

                for (i in rawCoords.indices) {
                    val coord = rawCoords[i]
                    val ele = if (coord.size >= 3) coord[2] else 0.0

                    if (i > 0) {
                        val prev = rawCoords[i - 1]
                        Location.distanceBetween(
                            prev[1], prev[0],
                            coord[1], coord[0],
                            distResults
                        )
                        accumulatedDist += distResults[0]

                        if (coord.size >= 3 && prev.size >= 3) {
                            val diff = coord[2] - prev[2]
                            if (diff > 0) ascent += diff else descent += -diff
                        }
                    }

                    elePoints.add(Pair(accumulatedDist, ele))
                }

                val dist = feature.properties.trackLength?.toDoubleOrNull() ?: accumulatedDist
                val dur = dist / footSpeedPref.footSpeedMps

                Log.d("NagivatorRouting", "BRouter SUCCESS ($profileName): ${geomPoints.size} geometry points, dist=${dist}m, dur=${dur}s, ascent=${ascent}m")

                val midPt = geomPoints[geomPoints.size / 2]
                val endPt = geomPoints.last()

                val steps = listOf(
                    RouteStep(
                        instruction = "Dem Weg folgen",
                        distanceMeters = dist / 2.0,
                        durationSeconds = dur / 2.0,
                        maneuverType = "depart",
                        maneuverModifier = "straight",
                        maneuverLocation = midPt
                    ),
                    RouteStep(
                        instruction = "Weiter auf dem Weg bis zum Ziel",
                        distanceMeters = dist / 2.0,
                        durationSeconds = dur / 2.0,
                        maneuverType = "arrive",
                        maneuverModifier = "straight",
                        maneuverLocation = endPt
                    )
                )

                return RouteResult(
                    waypoints = waypoints,
                    geometryPoints = geomPoints,
                    totalDistanceMeters = dist,
                    totalDurationSeconds = dur,
                    mode = TravelMode.FOOT,
                    steps = steps,
                    totalAscentMeters = ascent,
                    totalDescentMeters = descent,
                    elevationPoints = elePoints
                )
            } catch (e: Exception) {
                Log.e("NagivatorRouting", "BRouter profile '$profileName' failed: ${e.message}")
            }
        }
        return null
    }

    private fun getGeodesicFallbackRoute(
        waypoints: List<LocationPoint>,
        mode: TravelMode,
        footSpeedPref: SpeedPreference = SpeedPreference.NORMAL,
        carSpeedPref: SpeedPreference = SpeedPreference.NORMAL
    ): RouteResult {
        val geomPoints = waypoints.toMutableList()
        var totalDist = 0.0
        val results = FloatArray(1)
        for (i in 0 until waypoints.size - 1) {
            val p1 = waypoints[i]
            val p2 = waypoints[i + 1]
            Location.distanceBetween(
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude,
                results
            )
            totalDist += results[0]
        }
        val speedMps = if (mode == TravelMode.FOOT) footSpeedPref.footSpeedMps else (12.0 / carSpeedPref.carTimeFactor)
        val totalDur = totalDist / speedMps

        return RouteResult(
            waypoints = waypoints,
            geometryPoints = geomPoints,
            totalDistanceMeters = totalDist,
            totalDurationSeconds = totalDur,
            mode = mode,
            steps = listOf(
                RouteStep(
                    instruction = "Direktverbindung zum Ziel",
                    distanceMeters = totalDist,
                    durationSeconds = totalDur,
                    maneuverType = "straight",
                    maneuverModifier = "straight",
                    maneuverLocation = waypoints.firstOrNull()
                )
            )
        )
    }

    suspend fun searchPlaces(
        query: String,
        biasLat: Double? = null,
        biasLon: Double? = null,
        limit: Int = 10
    ): Result<List<LocationPoint>> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank()) return@runCatching emptyList()
            val response = geocodingApi.searchPlaces(
                query = query,
                limit = limit,
                lat = biasLat,
                lon = biasLon
            )
            response.features.mapNotNull { feature ->
                val coords = feature.geometry.coordinates
                if (coords.size >= 2) {
                    val nameStr = feature.properties.name ?: feature.properties.city ?: query
                    val addrParts = listOfNotNull(
                        feature.properties.street,
                        feature.properties.housenumber,
                        feature.properties.city,
                        feature.properties.country
                    )
                    LocationPoint(
                        latitude = coords[1],
                        longitude = coords[0],
                        name = nameStr,
                        address = addrParts.joinToString(", ")
                    )
                } else null
            }
        }
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): Result<LocationPoint> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                val response = geocodingApi.reverseGeocode(lat = lat, lon = lon)
                val feature = response.features.firstOrNull()
                val props = feature?.properties

                val nameStr = props?.name
                    ?: props?.street?.let { street ->
                        val num = props.housenumber ?: ""
                        "$street $num".trim()
                    }
                    ?: props?.city
                    ?: "Markierter Punkt"

                val addrParts = listOfNotNull(
                    props?.street,
                    props?.housenumber,
                    props?.city,
                    props?.country
                )

                LocationPoint(
                    latitude = lat,
                    longitude = lon,
                    name = nameStr,
                    address = addrParts.joinToString(", ")
                )
            } catch (_: Exception) {
                LocationPoint(
                    latitude = lat,
                    longitude = lon,
                    name = "Markierter Ort (%.3f, %.3f)".format(lat, lon),
                    address = "Offline (Kein Netzwerk)"
                )
            }
        }
    }
}
