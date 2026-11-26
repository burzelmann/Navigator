package com.example.nagivator.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.data.repository.OfflineSearchIndexRepository
import com.example.nagivator.data.repository.RoutingRepository
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.model.GermanManeuverTranslator
import com.example.nagivator.domain.model.LaneInfo
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RoadSignal
import com.example.nagivator.domain.model.RoadSignalType
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.RouteStep
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Debug-only drive simulator for automated Android Auto DHU video verification.
 * Never included in release builds (`app/src/debug/`).
 */
class DebugDriveSimulatorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scenario = intent.getStringExtra("scenario") ?: "city"
        Log.i(TAG, "Starting debug drive simulation scenario=$scenario")

        simulationJob?.cancel()
        simulationJob = scope.launch {
            when (scenario) {
                "highway" -> runHighwayScenario()
                "real_route" -> runRealOsrmRouteWithOffRouteScenario()
                "redlight_arrival" -> runRedLightDriftAndArrivalScenario()
                "kalkar_weeze_realtime" -> runRealTimeRoute(
                    context,
                    LocationPoint(51.7732232, 6.2923022, "Scholtenweg 15, Kalkar"),
                    LocationPoint(51.6194709, 6.1990334, "Holtumsweg 26, Weeze")
                )
                "kalkar_rheurdt_realtime" -> runRealTimeRoute(
                    context,
                    LocationPoint(51.7732232, 6.2923022, "Scholtenweg 15, Kalkar"),
                    LocationPoint(51.4661307, 6.4675776, "Im Bergwinkel 9, Rheurdt")
                )
                "index_kalkar_offline_test" -> {
                    val repo = OfflineSearchIndexRepository.getInstance(context)
                    val count = repo.buildIndexForRegion(
                        regionName = "Kalkar",
                        minLat = 51.720,
                        maxLat = 51.790,
                        minLon = 6.240,
                        maxLon = 6.350
                    )
                    RoutingRepository.debugSimulateOfflineSearch = true
                    Log.i(TAG, "OFFLINE_INDEX_READY count=$count")
                }
                "offline_routing_test" -> {
                    val offlineRepo = com.example.nagivator.data.repository.OfflineRoutingRepository.getInstance(context)
                    if (!offlineRepo.hasSegment("E5_N50.rd5")) {
                        Log.i(TAG, "OFFLINE_ROUTING_DOWNLOAD_START E5_N50.rd5")
                        offlineRepo.downloadSegment("E5_N50.rd5")
                    }
                    RoutingRepository.debugSimulateOfflineRouting = true
                    val start = LocationPoint(51.7732232, 6.2923022, "Scholtenweg 15, Kalkar")
                    val dest = LocationPoint(51.4661307, 6.4675776, "Im Bergwinkel 9, Rheurdt")
                    val route = RoutingRepository().getRoute(listOf(start, dest), TravelMode.CAR).getOrNull()
                    if (route != null) {
                        NavigationStateHolder.setSimulatedLocationActive(true)
                        NavigationStateHolder.updateLocation(start)
                        NavigationStateHolder.startNavigation(route)
                        Log.i(
                            TAG,
                            "OFFLINE_ROUTING_READY fallback=${route.isOfflineFallback} " +
                                "pts=${route.geometryPoints.size} steps=${route.steps.size} " +
                                "distKm=${"%.1f".format(route.totalDistanceMeters / 1000.0)} " +
                                "durMin=${"%.0f".format(route.totalDurationSeconds / 60.0)}"
                        )
                    } else {
                        Log.e(TAG, "OFFLINE_ROUTING_FAILED")
                    }
                }
                "stop" -> {
                    RoutingRepository.debugSimulateOfflineSearch = false
                    RoutingRepository.debugSimulateOfflineRouting = false
                    NavigationStateHolder.setSimulatedLocationActive(false)
                    NavigationStateHolder.stopNavigation()
                }
                else -> runCityScenario()
            }
        }
    }

    private suspend fun runCityScenario() {
        // Simulated city route in Kalkar / Niederrhein with:
        // 1) Multi-lane left turn at traffic light + compound right turn 95m later
        // 2) Roundabout taking 2nd exit
        val p0 = LocationPoint(51.7375, 6.2920, "Start")
        val p1 = LocationPoint(51.7405, 6.2920, "Kreuzung Ampel")      // ~333m north
        val p2 = LocationPoint(51.7405, 6.2906, "Abzweig Marktplatz")  // ~96m west
        val p3 = LocationPoint(51.7435, 6.2906, "Kreisverkehr")        // ~333m north
        val p4 = LocationPoint(51.7455, 6.2906, "Ziel Bahnhofstraße")  // ~222m north

        val step1Lanes = listOf(
            LaneInfo(listOf("left"), isValid = true),
            LaneInfo(listOf("straight"), isValid = false),
            LaneInfo(listOf("right"), isValid = false)
        )
        val step2Lanes = listOf(
            LaneInfo(listOf("straight"), isValid = false),
            LaneInfo(listOf("right"), isValid = true)
        )

        val steps = listOf(
            RouteStep(
                instruction = GermanManeuverTranslator.translate("depart", "straight", "Grabenstraße"),
                distanceMeters = 333.0,
                durationSeconds = 26.0,
                maneuverType = "depart",
                maneuverModifier = "straight",
                maneuverLocation = p0,
                roadName = "Grabenstraße"
            ),
            RouteStep(
                instruction = GermanManeuverTranslator.translate("turn", "left", "Hauptstr. (L 474)"),
                distanceMeters = 96.0,
                durationSeconds = 9.0,
                maneuverType = "turn",
                maneuverModifier = "left",
                maneuverLocation = p1,
                roadName = "Hauptstr.",
                roadRef = "L 474",
                lanes = step1Lanes
            ),
            RouteStep(
                instruction = GermanManeuverTranslator.translate("turn", "right", "Marktplatz"),
                distanceMeters = 333.0,
                durationSeconds = 26.0,
                maneuverType = "turn",
                maneuverModifier = "right",
                maneuverLocation = p2,
                roadName = "Marktplatz",
                lanes = step2Lanes
            ),
            RouteStep(
                instruction = GermanManeuverTranslator.translate("roundabout", "right", "Bahnhofstr.", roundaboutExit = 2),
                distanceMeters = 222.0,
                durationSeconds = 18.0,
                maneuverType = "roundabout",
                maneuverModifier = "right",
                maneuverLocation = p3,
                roadName = "Bahnhofstr.",
                roundaboutExit = 2
            ),
            RouteStep(
                instruction = "Sie haben Ihr Ziel erreicht",
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                maneuverType = "arrive",
                maneuverModifier = "straight",
                maneuverLocation = p4
            )
        )

        val route = RouteResult(
            waypoints = listOf(p0, p4),
            geometryPoints = densifyPolyline(listOf(p0, p1, p2, p3, p4), stepMeters = 20.0),
            totalDistanceMeters = 984.0,
            totalDurationSeconds = 79.0,
            mode = TravelMode.CAR,
            steps = steps
        )

        val signals = listOf(
            RoadSignal(id = 1001L, type = RoadSignalType.TRAFFIC_SIGNALS, latitude = p1.latitude, longitude = p1.longitude),
            RoadSignal(id = 1002L, type = RoadSignalType.ROUNDABOUT, latitude = p3.latitude, longitude = p3.longitude)
        )

        NavigationStateHolder.setSimulatedLocationActive(true)
        NavigationStateHolder.updateRoadContext(50, signals)
        NavigationStateHolder.updateLocation(p0.copy(bearing = 0f, speedMps = 12.5f), isSimulated = true)
        NavigationStateHolder.startNavigation(route)

        // Drive smoothly along route at ~45 km/h (compressed time: ~12s total)
        drivePolyline(route.geometryPoints, speedMps = 12.5f, tickDelayMs = 240L)
    }

    private suspend fun runHighwayScenario() {
        // Simulated A 57 highway drive at 120 km/h (33.3 m/s) approaching Exit 8 "Krefeld, Duisburg"
        val p0 = LocationPoint(51.5000, 6.5500, "A 57 Start")
        val pExit = LocationPoint(51.4670, 6.5500, "AS 8 Ausfahrt") // ~3,670 m south
        val pEnd = LocationPoint(51.4640, 6.5440, "Richtung Krefeld")

        val highwayLanes = listOf(
            LaneInfo(listOf("straight"), isValid = false),
            LaneInfo(listOf("straight"), isValid = false),
            LaneInfo(listOf("slight right"), isValid = true)
        )

        val steps = listOf(
            RouteStep(
                instruction = GermanManeuverTranslator.translate("depart", "straight", "A 57"),
                distanceMeters = 3670.0,
                durationSeconds = 110.0,
                maneuverType = "depart",
                maneuverModifier = "straight",
                maneuverLocation = p0,
                roadRef = "A 57"
            ),
            RouteStep(
                instruction = GermanManeuverTranslator.translate(
                    type = "off ramp",
                    modifier = "slight right",
                    rawName = "",
                    destinations = "Krefeld;Duisburg",
                    exitRef = "8"
                ),
                distanceMeters = 520.0,
                durationSeconds = 25.0,
                maneuverType = "off ramp",
                maneuverModifier = "slight right",
                maneuverLocation = pExit,
                destinations = "Krefeld;Duisburg",
                exitRef = "8",
                lanes = highwayLanes
            ),
            RouteStep(
                instruction = "Sie haben Ihr Ziel erreicht",
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                maneuverType = "arrive",
                maneuverModifier = "straight",
                maneuverLocation = pEnd
            )
        )

        val route = RouteResult(
            waypoints = listOf(p0, pEnd),
            geometryPoints = densifyPolyline(listOf(p0, pExit, pEnd), stepMeters = 90.0),
            totalDistanceMeters = 4190.0,
            totalDurationSeconds = 135.0,
            mode = TravelMode.CAR,
            steps = steps
        )

        NavigationStateHolder.setSimulatedLocationActive(true)
        NavigationStateHolder.updateRoadContext(120, emptyList())
        NavigationStateHolder.updateLocation(p0.copy(bearing = 180f, speedMps = 33.3f), isSimulated = true)
        NavigationStateHolder.startNavigation(route)

        // Drive smoothly along A57 at 120 km/h (~12s total)
        drivePolyline(route.geometryPoints, speedMps = 33.3f, tickDelayMs = 260L)
    }

    /**
     * Fetches a REAL OSRM route from Kalkar to Rheurdt (via A 57), drives the first ~3 km
     * along real OSRM geometry (testing live Autobahn traffic check, real lanes, real exits),
     * then deliberately deviates 250m off-route for 4 ticks to trigger live off-route recalculation.
     */
    private suspend fun runRealOsrmRouteWithOffRouteScenario() {
        val start = LocationPoint(51.7375, 6.2920, "Kalkar")
        val dest = LocationPoint(51.4667, 6.4667, "Rheurdt")
        val repo = RoutingRepository()
        val route = repo.getRoute(listOf(start, dest), TravelMode.CAR).getOrNull()
        if (route == null || route.geometryPoints.size < 10) {
            Log.w(TAG, "Real OSRM route failed or too short, falling back to highway scenario")
            runHighwayScenario()
            return
        }
        Log.i(TAG, "Real OSRM Kalkar->Rheurdt loaded: ${route.steps.size} steps, ${route.totalDistanceMeters.toInt()} m")

        NavigationStateHolder.setSimulatedLocationActive(true)
        NavigationStateHolder.updateLocation(start.copy(bearing = 150f, speedMps = 14f), isSimulated = true)
        NavigationStateHolder.startNavigation(route)

        val pts = route.geometryPoints
        // Sample ~32 points across the first ~40% of the real route so we see city -> B57 -> A57 transitions
        val maxIdx = (pts.size * 0.45).toInt().coerceIn(10, pts.size - 2)
        val stride = (maxIdx / 26).coerceAtLeast(1)
        var lastPoint = pts.first()
        var lastBearing = 150f
        var stepIdx = 0
        while (stepIdx < maxIdx) {
            val a = pts[stepIdx]
            val b = pts[(stepIdx + stride).coerceAtMost(pts.size - 1)]
            val bDeg = bearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude)
            val speed = if (stepIdx > maxIdx / 2) 31.0f else 15.0f
            NavigationStateHolder.updateLocation(a.copy(bearing = bDeg, speedMps = speed), isSimulated = true)
            lastPoint = a
            lastBearing = bDeg
            stepIdx += stride
            delay(320L)
        }

        // Deliberate Off-Route deviation (~250m east) for 4 ticks to verify 3-strike off-route recalculation
        Log.i(TAG, "Injecting deliberate off-route deviation for reroute check")
        for (k in 1..4) {
            NavigationStateHolder.updateLocation(
                LocationPoint(
                    latitude = lastPoint.latitude,
                    longitude = lastPoint.longitude + 0.0038 + k * 0.0004,
                    bearing = (lastBearing + 90f) % 360f,
                    speedMps = 14f
                ),
                isSimulated = true
            )
            delay(500L)
        }
        delay(2500L)
    }

    /**
     * Tests:
     * 1) Stationary red-light stop with noisy 90°/210° GPS drift (must keep heading rock-steady!)
     * 2) Resuming through a roundabout (2nd exit)
     * 3) Clean arrival ("In 200 Metern erreichen Sie Ihr Ziel." -> single "Sie haben Ihr Ziel erreicht")
     */
    private suspend fun runRedLightDriftAndArrivalScenario() {
        val p0 = LocationPoint(51.7380, 6.2920, "Start")
        val pAmpel = LocationPoint(51.7396, 6.2920, "Ampel")
        val pKreisel = LocationPoint(51.7418, 6.2920, "Kreisverkehr")
        val pZiel = LocationPoint(51.7438, 6.2920, "Ziel")

        val steps = listOf(
            RouteStep(
                instruction = GermanManeuverTranslator.translate("depart", "straight", "Grabenstraße"),
                distanceMeters = 420.0,
                durationSeconds = 35.0,
                maneuverType = "depart",
                maneuverModifier = "straight",
                maneuverLocation = p0,
                roadName = "Grabenstraße"
            ),
            RouteStep(
                instruction = GermanManeuverTranslator.translate("roundabout", "right", "Bahnhofstraße", roundaboutExit = 2),
                distanceMeters = 220.0,
                durationSeconds = 20.0,
                maneuverType = "roundabout",
                maneuverModifier = "right",
                maneuverLocation = pKreisel,
                roadName = "Bahnhofstraße",
                roundaboutExit = 2
            ),
            RouteStep(
                instruction = "Sie haben Ihr Ziel erreicht",
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                maneuverType = "arrive",
                maneuverModifier = "straight",
                maneuverLocation = pZiel
            )
        )

        val route = RouteResult(
            waypoints = listOf(p0, pZiel),
            geometryPoints = densifyPolyline(listOf(p0, pAmpel, pKreisel, pZiel), stepMeters = 22.0),
            totalDistanceMeters = 640.0,
            totalDurationSeconds = 55.0,
            mode = TravelMode.CAR,
            steps = steps
        )

        val signals = listOf(
            RoadSignal(id = 2001L, type = RoadSignalType.TRAFFIC_SIGNALS, latitude = pAmpel.latitude, longitude = pAmpel.longitude),
            RoadSignal(id = 2002L, type = RoadSignalType.ROUNDABOUT, latitude = pKreisel.latitude, longitude = pKreisel.longitude)
        )

        NavigationStateHolder.setSimulatedLocationActive(true)
        NavigationStateHolder.updateRoadContext(50, signals)
        NavigationStateHolder.updateLocation(p0.copy(bearing = 0f, speedMps = 12f), isSimulated = true)
        NavigationStateHolder.startNavigation(route)

        val pts = route.geometryPoints
        val ampelIdx = (pts.size * 0.28).toInt().coerceAtLeast(2)

        // Drive north to red light (bearing = 0°)
        for (i in 0 until ampelIdx) {
            NavigationStateHolder.updateLocation(pts[i].copy(bearing = 0f, speedMps = 12f), isSimulated = true)
            delay(250L)
        }

        // Stop at red light and inject noisy 90° / 210° / 280° GPS drift bearings at 0.3 m/s!
        Log.i(TAG, "Red light stop: injecting noisy 90/210/280 deg GPS drift at 0.3 m/s")
        val driftBearings = listOf(90f, 210f, 280f, 95f, 180f, 270f)
        for (drift in driftBearings) {
            NavigationStateHolder.updateLocation(
                pts[ampelIdx].copy(bearing = drift, speedMps = 0.3f, bearingAccuracyDegrees = 15f),
                isSimulated = true
            )
            delay(450L)
        }

        // Green light -> resume north through roundabout to clean arrival
        for (i in ampelIdx until pts.size) {
            NavigationStateHolder.updateLocation(pts[i].copy(bearing = 0f, speedMps = 12f), isSimulated = true)
            delay(280L)
        }
        delay(2000L)
    }

    /**
     * Full 1:1 real-time drive between any real [start] and [dest] coordinates.
     * Fetches the live OSRM route and advances at realistic 1 Hz GPS speed
     * derived from each OSRM step's actual distance/duration.
     */
    private suspend fun runRealTimeRoute(
        context: Context,
        start: LocationPoint,
        dest: LocationPoint
    ) {
        UserSettingsRepository.getInstance(context).setVoiceMuted(false)

        val repo = RoutingRepository()
        val route = repo.getRoute(listOf(start, dest), TravelMode.CAR).getOrNull()
        if (route == null || route.geometryPoints.size < 10) {
            Log.e(TAG, "SIM_ERROR: Could not fetch real OSRM route ${start.name} -> ${dest.name}")
            return
        }

        Log.i(
            TAG,
            "SIM_START: ${start.name}->${dest.name} real-time route loaded: ${route.steps.size} steps, " +
                "${route.totalDistanceMeters.toInt()}m, est ${(route.totalDurationSeconds / 60.0).toInt()} min"
        )
        route.steps.forEachIndexed { idx, s ->
            Log.i(
                TAG,
                "SIM_STEP[$idx]: type=${s.maneuverType} mod=${s.maneuverModifier} exit=${s.roundaboutExit} " +
                    "dist=${s.distanceMeters.toInt()}m dur=${s.durationSeconds.toInt()}s lanes=${s.lanes.size} instr='${s.instruction}'"
            )
        }

        NavigationStateHolder.setSimulatedLocationActive(true)
        NavigationStateHolder.updateLocation(start.copy(bearing = 90f, speedMps = 0f), isSimulated = true)
        NavigationStateHolder.startNavigation(route)
        delay(2000L)

        // Precompute cumulative distances along full OSRM geometry
        val geom = route.geometryPoints
        val cumDist = DoubleArray(geom.size)
        for (i in 1 until geom.size) {
            cumDist[i] = cumDist[i - 1] + CarNavigationHelper.haversineMeters(
                geom[i - 1].latitude, geom[i - 1].longitude,
                geom[i].latitude, geom[i].longitude
            )
        }
        val totalDist = cumDist.last().coerceAtLeast(1.0)

        // Map each route step to its cumulative distance along the route for realistic speed lookup
        val stepStartDists = DoubleArray(route.steps.size)
        var acc = 0.0
        for (i in route.steps.indices) {
            stepStartDists[i] = acc
            acc += route.steps[i].distanceMeters
        }

        var drivenMeters = 0.0
        var elapsedSec = 0
        var lastLoggedStepIdx = -1
        var geomIdx = 0

        while (drivenMeters < totalDist) {
            // Determine realistic speed for current position from active OSRM step
            var oStepIdx = 0
            for (i in stepStartDists.indices) {
                if (drivenMeters >= stepStartDists[i]) oStepIdx = i else break
            }
            val oStep = route.steps[oStepIdx]
            val baseSpeedMps = if (oStep.durationSeconds > 1.0 && oStep.distanceMeters > 5.0) {
                (oStep.distanceMeters / oStep.durationSeconds).toFloat().coerceIn(5.5f, 33.3f)
            } else {
                8.3f // ~30 km/h fallback for short turns/roundabouts
            }

            // Advance along polyline to `drivenMeters`
            while (geomIdx < geom.size - 2 && cumDist[geomIdx + 1] < drivenMeters) {
                geomIdx++
            }
            val segLen = (cumDist[geomIdx + 1] - cumDist[geomIdx]).coerceAtLeast(0.01)
            val frac = ((drivenMeters - cumDist[geomIdx]) / segLen).coerceIn(0.0, 1.0)
            val a = geom[geomIdx]
            val b = geom[geomIdx + 1]
            val lat = a.latitude + (b.latitude - a.latitude) * frac
            val lon = a.longitude + (b.longitude - a.longitude) * frac
            val bearing = bearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude)

            NavigationStateHolder.updateLocation(
                LocationPoint(
                    latitude = lat,
                    longitude = lon,
                    bearing = bearing,
                    speedMps = baseSpeedMps,
                    bearingAccuracyDegrees = 6f
                ),
                isSimulated = true
            )

            val navStepIdx = NavigationStateHolder.currentStepIndex.value
            val activeRouteNow = NavigationStateHolder.activeRoute.value
            if (navStepIdx != lastLoggedStepIdx || elapsedSec % 30 == 0) {
                lastLoggedStepIdx = navStepIdx
                val navStep = activeRouteNow?.steps?.getOrNull(navStepIdx)
                val distToMan = navStep?.maneuverLocation?.let { ml ->
                    CarNavigationHelper.haversineMeters(lat, lon, ml.latitude, ml.longitude).toInt()
                } ?: -1
                Log.i(
                    TAG,
                    "SIM_TELEMETRY t=${elapsedSec}s driven=${drivenMeters.toInt()}/${totalDist.toInt()}m " +
                        "speed=${(baseSpeedMps * 3.6f).toInt()}km/h navStep=$navStepIdx/${activeRouteNow?.steps?.size ?: 0} " +
                        "distToMan=${distToMan}m limit=${NavigationStateHolder.currentSpeedLimitKmh.value} " +
                        "instr='${navStep?.instruction.orEmpty()}'"
                )
            }

            if (!NavigationStateHolder.isNavigating.value) {
                Log.i(TAG, "SIM_NAV_ENDED_EARLY at t=${elapsedSec}s driven=${drivenMeters.toInt()}m")
                break
            }

            delay(1000L)
            elapsedSec++
            drivenMeters += baseSpeedMps.toDouble()
        }

        // Final arrival position at destination
        NavigationStateHolder.updateLocation(
            dest.copy(bearing = 0f, speedMps = 0f, bearingAccuracyDegrees = 5f),
            isSimulated = true
        )
        delay(4000L)
        Log.i(TAG, "SIM_ARRIVED t=${elapsedSec}s totalDist=${totalDist.toInt()}m")
    }

    private suspend fun drivePolyline(
        points: List<LocationPoint>,
        speedMps: Float,
        tickDelayMs: Long
    ) {
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val bearing = bearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude)
            NavigationStateHolder.updateLocation(
                a.copy(bearing = bearing, speedMps = speedMps),
                isSimulated = true
            )
            delay(tickDelayMs)
        }
        points.lastOrNull()?.let { end ->
            NavigationStateHolder.updateLocation(
                end.copy(speedMps = 0f),
                isSimulated = true
            )
        }
    }

    private fun densifyPolyline(points: List<LocationPoint>, stepMeters: Double): List<LocationPoint> {
        if (points.size < 2) return points
        val out = mutableListOf<LocationPoint>()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val dist = CarNavigationHelper.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            val segments = (dist / stepMeters).toInt().coerceAtLeast(1)
            for (s in 0 until segments) {
                val f = s.toDouble() / segments
                out.add(
                    LocationPoint(
                        latitude = a.latitude + (b.latitude - a.latitude) * f,
                        longitude = a.longitude + (b.longitude - a.longitude) * f
                    )
                )
            }
        }
        out.add(points.last())
        return out
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg + 360.0) % 360.0).toFloat()
    }

    companion object {
        private const val TAG = "DebugDriveSim"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var simulationJob: Job? = null
    }
}
