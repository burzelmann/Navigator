package com.example.nagivator.data.repository

import android.util.Log
import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.data.remote.AutobahnEventDto
import com.example.nagivator.data.remote.AutobahnTrafficApi
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TrafficIncident
import com.example.nagivator.domain.model.TrafficIncidentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import kotlin.math.max
import kotlin.math.min

/**
 * Fetches keyless German Autobahn live traffic data (closures, warnings, roadworks)
 * from the official Autobahn GmbH Open Data API (`verkehr.autobahn.de`).
 */
class TrafficRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Nagivator/1.0 (Android App; com.verhuelsdonk.navigator)")
                .build()
            chain.proceed(req)
        }
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val api: AutobahnTrafficApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://verkehr.autobahn.de/o/autobahn/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(MediaType.get("application/json")))
            .build()
            .create(AutobahnTrafficApi::class.java)
    }

    /**
     * Queries keyless German open-data traffic sources along [route]:
     * 1. Autobahn GmbH (`verkehr.autobahn.de`) for touched Bundesautobahnen
     * 2. Verkehr.NRW / Mobilithek WFS (`verkehr.nrw/geoserver/vipnrw/wfs`) for urban/state/federal
     *    closures, roadworks, and live congestion delays (`travel_time_loss`)
     */
    suspend fun fetchIncidentsOnRoute(route: RouteResult): List<TrafficIncident> =
        withContext(Dispatchers.IO) {
            val allIncidents = mutableListOf<TrafficIncident>()

            // 1. Autobahn GmbH (Bundesautobahnen)
            val roadIds = extractAutobahnIds(route)
            for (roadId in roadIds) {
                runCatching {
                    api.getClosures(roadId).closure.mapNotNull {
                        it.toIncident(roadId, TrafficIncidentType.CLOSURE, defaultBlocked = true)
                    }
                }.onSuccess { allIncidents.addAll(it) }

                runCatching {
                    api.getWarnings(roadId).warning.mapNotNull {
                        it.toIncident(roadId, TrafficIncidentType.WARNING, defaultBlocked = false)
                    }
                }.onSuccess { allIncidents.addAll(it) }

                runCatching {
                    api.getRoadworks(roadId).roadworks.mapNotNull {
                        it.toIncident(roadId, TrafficIncidentType.ROADWORK, defaultBlocked = false)
                    }
                }.onSuccess { allIncidents.addAll(it) }
            }

            // 2. Verkehr.NRW / Mobilithek Open Data WFS (urban/state/federal roads + live delays)
            val bbox = computeRouteBbox(route)
            if (bbox != null && intersectsGermany(bbox)) {
                val nrwIncidents = fetchVerkehrNrwIncidents(bbox, route)
                allIncidents.addAll(nrwIncidents)
            }

            // 3. NDW Netherlands Open Data DATEX II v3 (opendata.ndw.nu/actueel_beeld.xml.gz)
            if (bbox != null && intersectsNetherlands(bbox)) {
                val ndwIncidents = fetchNdwIncidents(bbox, route)
                allIncidents.addAll(ndwIncidents)
            }

            val filtered = filterIncidentsOnRoute(allIncidents, route)
            deduplicateSpatially(filtered)
        }

    private fun fetchNdwIncidents(bbox: RouteBbox, route: RouteResult): List<TrafficIncident> {
        val now = System.currentTimeMillis()
        val cached = ndwCache
        val allNdw = if (cached != null && (now - cached.fetchedAtMs) < NDW_CACHE_TTL_MS) {
            cached.incidents
        } else {
            val fetched = runCatching {
                val req = Request.Builder().url(NDW_ACTUEEL_BEELD_URL).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "NDW feed HTTP ${resp.code()}")
                        emptyList()
                    } else {
                        val stream = resp.body()?.byteStream() ?: return@use emptyList()
                        GZIPInputStream(stream).use { gz ->
                            parseNdwDatexXml(gz)
                        }
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "NDW feed failed: ${e.message}")
            }.getOrDefault(emptyList())
            if (fetched.isNotEmpty()) {
                ndwCache = NdwCacheEntry(now, fetched)
            }
            fetched
        }

        val inBbox = allNdw.filter { inc ->
            inc.latitude in bbox.minLat..bbox.maxLat && inc.longitude in bbox.minLon..bbox.maxLon
        }.map { inc ->
            val road = findNearestRoadLabel(route, inc.latitude, inc.longitude)
            if (road.isNotBlank() && inc.roadId == "Strecke") inc.copy(roadId = road) else inc
        }
        Log.i(TAG, "NDW fetched ${allNdw.size} total NL situations, ${inBbox.size} in route bbox")
        return inBbox
    }

    private fun fetchVerkehrNrwIncidents(bbox: RouteBbox, route: RouteResult): List<TrafficIncident> {
        val bboxParam = String.format(
            Locale.US,
            "%.5f,%.5f,%.5f,%.5f,EPSG:4326",
            bbox.minLon,
            bbox.minLat,
            bbox.maxLon,
            bbox.maxLat
        )
        val out = mutableListOf<TrafficIncident>()
        for (layer in VERKEHR_NRW_LAYERS) {
            val url = "$VERKEHR_NRW_WFS_BASE&typeName=$layer&bbox=$bboxParam"
            runCatching {
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body()?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val parsed = parseVerkehrNrwGeoJson(body, layer, route)
                            out.addAll(parsed)
                        }
                    } else {
                        Log.w(TAG, "Verkehr.NRW layer $layer HTTP ${resp.code()}")
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Verkehr.NRW layer $layer failed: ${e.message}")
            }
        }
        Log.i(TAG, "Verkehr.NRW fetched ${out.size} raw incidents in route bbox")
        return out
    }

    private fun AutobahnEventDto.toIncident(
        roadId: String,
        type: TrafficIncidentType,
        defaultBlocked: Boolean
    ): TrafficIncident? {
        if (isFutureBool()) return null
        val lat = coordinate?.latDouble() ?: return null
        val lon = coordinate?.lonDouble() ?: return null
        val id = identifier.ifBlank { "${roadId}_${type.name}_${lat}_$lon" }
        return TrafficIncident(
            id = id,
            roadId = roadId,
            type = type,
            title = title.ifBlank { "$roadId Meldung" },
            subtitle = subtitle,
            description = description.joinToString(" ").trim(),
            latitude = lat,
            longitude = lon,
            isBlocked = defaultBlocked || isBlockedBool()
        )
    }

    data class RouteBbox(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )

    private data class NdwCacheEntry(
        val fetchedAtMs: Long,
        val incidents: List<TrafficIncident>
    )

    companion object {
        private const val TAG = "NagivatorTraffic"
        private const val NDW_ACTUEEL_BEELD_URL = "https://opendata.ndw.nu/actueel_beeld.xml.gz"
        private const val NDW_CACHE_TTL_MS = 90_000L
        @Volatile
        private var ndwCache: NdwCacheEntry? = null
        private val parserJson = Json { ignoreUnknownKeys = true }
        private val AUTOBAHN_REGEX = Regex("""\bA\s?(\d{1,3})\b""")
        private const val VERKEHR_NRW_WFS_BASE =
            "https://www.verkehr.nrw/geoserver/vipnrw/wfs?service=WFS&version=1.1.0&request=GetFeature&outputFormat=application/json&srsName=EPSG:4326&maxFeatures=100"
        private val VERKEHR_NRW_LAYERS = listOf(
            "vipnrw:traffic_abnormal_travel_time_loss_feature",
            "vipnrw:traffic_obstructions_urban_feature",
            "vipnrw:traffic_roadworks_urban_feature"
        )

        fun computeRouteBbox(route: RouteResult, padDegrees: Double = 0.02): RouteBbox? {
            if (route.geometryPoints.size < 2) return null
            var minLat = Double.POSITIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var minLon = Double.POSITIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY
            for (pt in route.geometryPoints) {
                minLat = min(minLat, pt.latitude)
                maxLat = max(maxLat, pt.latitude)
                minLon = min(minLon, pt.longitude)
                maxLon = max(maxLon, pt.longitude)
            }
            return RouteBbox(
                minLat = minLat - padDegrees,
                maxLat = maxLat + padDegrees,
                minLon = minLon - padDegrees,
                maxLon = maxLon + padDegrees
            )
        }

        fun intersectsGermany(bbox: RouteBbox): Boolean {
            return bbox.maxLat >= 47.2 && bbox.minLat <= 55.2 &&
                bbox.maxLon >= 5.7 && bbox.minLon <= 15.2
        }

        fun intersectsNetherlands(bbox: RouteBbox): Boolean {
            return bbox.maxLat >= 50.7 && bbox.minLat <= 53.6 &&
                bbox.maxLon >= 3.3 && bbox.minLon <= 7.3
        }

        /**
         * Streaming SAX parser for NDW Netherlands DATEX II v3 (`actueel_beeld.xml.gz`).
         * Extracts active closures, accidents, congestion, roadworks, and obstructions with WGS84 coordinates.
         */
        fun parseNdwDatexXml(xmlStream: InputStream): List<TrafficIncident> {
            val out = mutableListOf<TrafficIncident>()
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val parser = factory.newSAXParser()

            parser.parse(xmlStream, object : DefaultHandler() {
                private var inRecord = false
                private var recordId = ""
                private var xsiType = ""
                private var causeType = ""
                private var mgmtType = ""
                private var roadName = ""
                private var lat: Double? = null
                private var lon: Double? = null
                private val textBuf = StringBuilder()

                override fun startElement(uri: String?, localName: String, qName: String, attributes: Attributes) {
                    textBuf.setLength(0)
                    if (localName == "situationRecord") {
                        inRecord = true
                        recordId = attributes.getValue("id").orEmpty()
                        xsiType = attributes.getValue("http://www.w3.org/2001/XMLSchema-instance", "type")
                            ?: attributes.getValue("xsi:type")
                            ?: ""
                        causeType = ""
                        mgmtType = ""
                        roadName = ""
                        lat = null
                        lon = null
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inRecord) {
                        textBuf.append(ch, start, length)
                    }
                }

                override fun endElement(uri: String?, localName: String, qName: String) {
                    if (!inRecord) return
                    val txt = textBuf.toString().trim()
                    when (localName) {
                        "causeType" -> if (causeType.isEmpty()) causeType = txt
                        "roadOrCarriagewayOrLaneManagementType" -> if (mgmtType.isEmpty()) mgmtType = txt
                        "roadNumber" -> if (roadName.isEmpty() && txt.isNotBlank()) roadName = txt
                        "latitude" -> if (lat == null) lat = txt.toDoubleOrNull()
                        "longitude" -> if (lon == null) lon = txt.toDoubleOrNull()
                        "posList" -> if (lat == null && lon == null && txt.isNotBlank()) {
                            val tokens = txt.split(Regex("\\s+"))
                            if (tokens.size >= 2) {
                                lat = tokens[0].toDoubleOrNull()
                                lon = tokens[1].toDoubleOrNull()
                            }
                        }
                        "situationRecord" -> {
                            inRecord = false
                            val rLat = lat
                            val rLon = lon
                            if (rLat != null && rLon != null) {
                                buildNdwIncident(recordId, xsiType, causeType, mgmtType, roadName, rLat, rLon)?.let {
                                    out.add(it)
                                }
                            }
                        }
                    }
                }
            })
            return out
        }

        private fun buildNdwIncident(
            recordId: String,
            xsiType: String,
            causeType: String,
            mgmtType: String,
            roadName: String,
            lat: Double,
            lon: Double
        ): TrafficIncident? {
            val typeLower = xsiType.lowercase()
            val mgmtLower = mgmtType.lowercase()
            val causeLower = causeType.lowercase()

            // Skip pure advisory speed/rerouting signs to avoid false alerts
            if (typeLower.contains("speedmanagement") || typeLower.contains("reroutingmanagement")) {
                return null
            }

            val (type, isBlocked, title) = when {
                mgmtLower.contains("roadclosed") || mgmtLower.contains("carriagewayclosures") -> {
                    Triple(TrafficIncidentType.CLOSURE, true, "Sperrung (NL)")
                }
                typeLower.contains("accident") || causeLower.contains("accident") -> {
                    Triple(TrafficIncidentType.WARNING, false, "Unfall (NL)")
                }
                typeLower.contains("abnormaltraffic") || causeLower.contains("congestion") -> {
                    Triple(TrafficIncidentType.WARNING, false, "Stau (NL)")
                }
                causeLower.contains("maintenance") || mgmtLower.contains("laneclosures") -> {
                    Triple(TrafficIncidentType.ROADWORK, false, "Baustelle (NL)")
                }
                typeLower.contains("obstruction") -> {
                    Triple(TrafficIncidentType.WARNING, false, "Hindernis (NL)")
                }
                else -> return null
            }

            return TrafficIncident(
                id = "ndw_${recordId.ifBlank { "${lat}_$lon" }}",
                roadId = roadName.ifBlank { "Strecke" },
                type = type,
                title = title,
                latitude = lat,
                longitude = lon,
                isBlocked = isBlocked,
                source = "NDW (NL)"
            )
        }

        /**
         * Parses a Verkehr.NRW WFS GeoJSON response into [TrafficIncident] items,
         * ignoring future/planned events (`future == true`).
         */
        fun parseVerkehrNrwGeoJson(
            rawJson: String,
            layerName: String,
            route: RouteResult? = null
        ): List<TrafficIncident> {
            val root = runCatching { parserJson.parseToJsonElement(rawJson).jsonObject }.getOrNull()
                ?: return emptyList()
            val features = root["features"]?.jsonArray ?: return emptyList()
            val out = mutableListOf<TrafficIncident>()

            for (featEl in features) {
                val feat = featEl.jsonObject
                val props = feat["properties"]?.jsonObject ?: continue
                if (props["future"]?.jsonPrimitive?.booleanOrNull == true) continue

                val geom = feat["geometry"]?.jsonObject ?: continue
                val coords = geom["coordinates"]?.jsonArray ?: continue
                if (coords.size < 2) continue
                val lon = coords[0].jsonPrimitive.doubleOrNull ?: continue
                val lat = coords[1].jsonPrimitive.doubleOrNull ?: continue

                val displayType = props["display_type"]?.jsonPrimitive?.content?.uppercase()?.trim().orEmpty()
                val rawInfo = props["info"]?.jsonPrimitive?.content?.replace("|", " ")?.trim().orEmpty()
                val lossSec = props["travel_time_loss"]?.jsonPrimitive?.intOrNull
                val identifier = props["identifier"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: feat["id"]?.jsonPrimitive?.content
                    ?: "nrw_${layerName}_${lat}_$lon"

                val nearestRoad = route?.let { findNearestRoadLabel(it, lat, lon) }.orEmpty()
                val roadLabel = nearestRoad.ifBlank { "Strecke" }

                val (type, isBlocked, title) = when {
                    layerName.contains("abnormal_travel_time_loss") || displayType == "CONGESTION" -> {
                        val mins = ((lossSec ?: 0) / 60).coerceAtLeast(1)
                        val label = if (lossSec != null && lossSec >= 60) {
                            "Stau (+$mins Min.)"
                        } else if (rawInfo.isNotBlank()) {
                            "Stau ($rawInfo)"
                        } else {
                            "Stau voraus"
                        }
                        Triple(TrafficIncidentType.WARNING, false, label)
                    }
                    displayType == "CLOSURE" -> {
                        Triple(TrafficIncidentType.CLOSURE, true, "Sperrung")
                    }
                    displayType == "ROADWORKS" || layerName.contains("roadworks") -> {
                        Triple(TrafficIncidentType.ROADWORK, false, "Baustelle")
                    }
                    else -> {
                        Triple(TrafficIncidentType.WARNING, false, "Verkehrsstörung")
                    }
                }

                out.add(
                    TrafficIncident(
                        id = "vnrw_$identifier",
                        roadId = roadLabel,
                        type = type,
                        title = title,
                        subtitle = rawInfo,
                        description = rawInfo,
                        latitude = lat,
                        longitude = lon,
                        isBlocked = isBlocked,
                        delaySeconds = lossSec?.takeIf { it > 0 },
                        source = "Verkehr.NRW"
                    )
                )
            }
            return out
        }

        private fun findNearestRoadLabel(route: RouteResult, lat: Double, lon: Double): String {
            var bestDist = Double.POSITIVE_INFINITY
            var bestLabel = ""
            for (step in route.steps) {
                val loc = step.maneuverLocation ?: continue
                val label = step.roadRef.ifBlank { step.roadName }.trim()
                if (label.isBlank()) continue
                val d = CarNavigationHelper.haversineMeters(lat, lon, loc.latitude, loc.longitude)
                if (d < bestDist && d <= 2000.0) {
                    bestDist = d
                    bestLabel = label
                }
            }
            return bestLabel
        }

        /**
         * Deduplicates incidents from multiple providers (e.g. Autobahn GmbH + Verkehr.NRW)
         * that refer to the same physical location within [radiusMeters], keeping the richer entry.
         */
        fun deduplicateSpatially(
            incidents: List<TrafficIncident>,
            radiusMeters: Double = 250.0
        ): List<TrafficIncident> {
            val result = mutableListOf<TrafficIncident>()
            for (inc in incidents) {
                val existingIdx = result.indexOfFirst { other ->
                    other.type == inc.type &&
                        CarNavigationHelper.haversineMeters(other.latitude, other.longitude, inc.latitude, inc.longitude) <= radiusMeters
                }
                if (existingIdx < 0) {
                    result.add(inc)
                } else {
                    val existing = result[existingIdx]
                    // Prefer incident with explicit delaySeconds or non-generic roadId
                    val incScore = (if (inc.delaySeconds != null) 2 else 0) + (if (inc.roadId != "Strecke") 1 else 0)
                    val exScore = (if (existing.delaySeconds != null) 2 else 0) + (if (existing.roadId != "Strecke") 1 else 0)
                    if (incScore > exScore) {
                        result[existingIdx] = inc
                    }
                }
            }
            return result
        }

        /**
         * Extracts normalized German Autobahn IDs (e.g. "A57", "A3") from a route's step instructions.
         */
        fun extractAutobahnIds(route: RouteResult): List<String> {
            val found = LinkedHashSet<String>()
            for (step in route.steps) {
                AUTOBAHN_REGEX.findAll(step.instruction).forEach { match ->
                    val num = match.groupValues[1]
                    if (num.isNotEmpty()) {
                        found.add("A$num")
                    }
                }
            }
            return found.toList()
        }

        /**
         * Filters incidents to those lying close to the active route polyline, ordered by
         * appearance along the route.
         *
         * Uses tighter distance thresholds for urban/regional open-data feeds (`Verkehr.NRW`,
         * `NDW (NL)`) — especially `<= 40 m` for closures — so side-street or opposite-ramp
         * closures never trigger false reroutes.
         */
        fun filterIncidentsOnRoute(
            incidents: List<TrafficIncident>,
            route: RouteResult,
            maxDistanceMeters: Float? = null
        ): List<TrafficIncident> {
            if (route.geometryPoints.size < 2 || incidents.isEmpty()) return emptyList()
            return incidents.mapNotNull { incident ->
                val limit = maxDistanceMeters ?: when {
                    incident.source == "Autobahn" -> 180f
                    incident.type == TrafficIncidentType.CLOSURE || incident.isBlocked -> 40f
                    else -> 80f
                }
                val point = LocationPoint(incident.latitude, incident.longitude)
                val (dist, segIdx) = CarNavigationHelper.distanceToPolyline(point, route.geometryPoints)
                if (dist <= limit) segIdx to incident else null
            }
                .sortedBy { it.first }
                .map { it.second }
                .distinctBy { it.id }
        }

        /**
         * Plausibility guard for automatic closure detours: rejects detours that are > 60%
         * longer than the original route (e.g. when a point closure sits near a cul-de-sac).
         */
        fun isPlausibleClosureDetour(detourDistanceMeters: Double, originalDistanceMeters: Double): Boolean {
            if (originalDistanceMeters <= 0.0) return true
            return detourDistanceMeters <= originalDistanceMeters * 1.60
        }
    }
}
