package com.example.nagivator.data.repository

import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.data.remote.OsmMapApi
import com.example.nagivator.data.remote.OsmMapElement
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RoadContextSnapshot
import com.example.nagivator.domain.model.RoadSignal
import com.example.nagivator.domain.model.RoadSignalType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Fetches live OSM speed limits (`maxspeed`) and prominent junction/traffic signals
 * (traffic lights, stop signs, give-way, roundabouts) from the official keyless OSM 0.6 Map API.
 * Caches road geometries in memory so speed-limit updates as you turn onto a new street happen in 0 ms.
 */
class RoadContextRepository {

    private data class CachedRoadWay(
        val speedLimitKmh: Int,
        val points: List<LocationPoint>
    )

    private data class CachedArea(
        val centerLat: Double,
        val centerLon: Double,
        val fetchedAtMs: Long,
        val roads: List<CachedRoadWay>,
        val signals: List<RoadSignal>
    )

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Nagivator/1.0 (Android App; com.verhuelsdonk.navigator)")
                .build()
            chain.proceed(req)
        }
        .build()

    private val osmMapApi: OsmMapApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openstreetmap.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(MediaType.get("application/json")))
            .build()
            .create(OsmMapApi::class.java)
    }

    @Volatile
    private var cachedArea: CachedArea? = null

    /**
     * Fetches or returns cached speed limit + nearby road signals around ([lat], [lon]).
     * Network refreshes only when moving > 350 m from the cached center or after 90 s.
     * Within the cached area, resolves the closest road's speed limit instantaneously in memory.
     */
    suspend fun fetchAround(lat: Double, lon: Double, forceRefresh: Boolean = false): RoadContextSnapshot? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            var area = cachedArea
            val distFromCenter = if (area != null) {
                CarNavigationHelper.haversineMeters(lat, lon, area.centerLat, area.centerLon)
            } else {
                Double.MAX_VALUE
            }

            val needsFetch = forceRefresh || area == null ||
                distFromCenter > 350.0 || (now - area.fetchedAtMs) > 90_000L

            if (needsFetch) {
                val bbox = buildBbox(lat, lon)
                val fetched = runCatching {
                    val resp = osmMapApi.getMap(bbox)
                    parseArea(resp.elements, lat, lon, now)
                }.getOrNull()

                if (fetched != null) {
                    cachedArea = fetched
                    area = fetched
                }
            }

            val activeArea = area ?: return@withContext null
            val limit = resolveClosestSpeedLimit(lat, lon, activeArea.roads)
            RoadContextSnapshot(
                speedLimitKmh = limit,
                signals = activeArea.signals,
                centerLat = activeArea.centerLat,
                centerLon = activeArea.centerLon,
                fetchedAtMs = activeArea.fetchedAtMs
            )
        }

    private fun parseArea(
        elements: List<OsmMapElement>,
        centerLat: Double,
        centerLon: Double,
        nowMs: Long
    ): CachedArea {
        val nodeMap = HashMap<Long, LocationPoint>(elements.size)
        val signals = mutableListOf<RoadSignal>()

        for (el in elements) {
            if (el.type == "node" && el.lat != null && el.lon != null) {
                nodeMap[el.id] = LocationPoint(el.lat, el.lon)
                val hw = el.tags["highway"]
                val sigType = when (hw) {
                    "traffic_signals" -> RoadSignalType.TRAFFIC_SIGNALS
                    "stop" -> RoadSignalType.STOP
                    "give_way" -> RoadSignalType.GIVE_WAY
                    else -> null
                }
                if (sigType != null) {
                    signals.add(RoadSignal(el.id, sigType, el.lat, el.lon))
                }
            }
        }

        val roads = mutableListOf<CachedRoadWay>()
        for (el in elements) {
            if (el.type != "way") continue
            val tags = el.tags
            val pts = el.nodes.mapNotNull { nodeMap[it] }
            if (pts.size < 2) continue

            val junction = tags["junction"]
            if (junction == "roundabout" || junction == "circular") {
                val avgLat = pts.map { it.latitude }.average()
                val avgLon = pts.map { it.longitude }.average()
                signals.add(RoadSignal(el.id, RoadSignalType.ROUNDABOUT, avgLat, avgLon))
            }

            val hw = tags["highway"] ?: continue
            val limit = parseMaxSpeedKmh(tags["maxspeed"]) ?: defaultLimitForHighway(hw) ?: continue
            roads.add(CachedRoadWay(speedLimitKmh = limit, points = pts))
        }

        val dedupedSignals = signals.distinctBy {
            val grid = if (it.type == RoadSignalType.ROUNDABOUT) 1500 else 3000
            "${it.type}_${(it.latitude * grid).toInt()}_${(it.longitude * grid).toInt()}"
        }

        return CachedArea(
            centerLat = centerLat,
            centerLon = centerLon,
            fetchedAtMs = nowMs,
            roads = roads,
            signals = dedupedSignals
        )
    }

    private fun resolveClosestSpeedLimit(
        lat: Double,
        lon: Double,
        roads: List<CachedRoadWay>
    ): Int? {
        if (roads.isEmpty()) return null
        val loc = LocationPoint(lat, lon)
        var bestDist = Float.MAX_VALUE
        var bestLimit: Int? = null
        for (road in roads) {
            val (dist, _) = CarNavigationHelper.distanceToPolyline(loc, road.points)
            if (dist < bestDist) {
                bestDist = dist
                bestLimit = road.speedLimitKmh
            }
        }
        return if (bestDist <= 45f) bestLimit else null
    }

    private fun defaultLimitForHighway(highway: String): Int? = when (highway) {
        "living_street" -> 7
        "residential" -> 50
        "motorway" -> SPEED_LIMIT_UNLIMITED
        else -> null
    }

    companion object {
        const val SPEED_LIMIT_UNLIMITED = -1

        fun buildBbox(lat: Double, lon: Double, dLat: Double = 0.0045, dLon: Double = 0.0065): String {
            return String.format(
                Locale.US,
                "%.5f,%.5f,%.5f,%.5f",
                lon - dLon,
                lat - dLat,
                lon + dLon,
                lat + dLat
            )
        }

        /**
         * Parses OSM `maxspeed` tag values into km/h, or [SPEED_LIMIT_UNLIMITED] (-1) for `none`.
         */
        fun parseMaxSpeedKmh(raw: String?): Int? {
            if (raw.isNullOrBlank()) return null
            val norm = raw.trim().lowercase(Locale.US)
            return when {
                norm == "none" || norm == "de:motorway" -> SPEED_LIMIT_UNLIMITED
                norm == "walk" || norm == "de:living_street" -> 7
                norm == "de:urban" || norm == "nl:urban" -> 50
                norm == "de:rural" -> 100
                norm == "nl:rural" -> 80
                norm.contains("zone30") || norm.contains("zone:30") -> 30
                norm.contains("zone20") || norm.contains("zone:20") -> 20
                norm.endsWith("mph") -> {
                    val num = norm.removeSuffix("mph").trim().toDoubleOrNull()
                    num?.let { (it * 1.60934).roundToInt() }
                }
                else -> {
                    val digits = Regex("""^\d+""").find(norm)?.value?.toIntOrNull()
                    if (digits != null && digits in 5..250) digits else null
                }
            }
        }
    }
}
