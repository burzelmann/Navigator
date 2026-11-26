package com.example.nagivator.data.repository

import android.content.Context
import android.util.Log
import btools.router.FormatJson
import btools.router.OsmNodeNamed
import btools.router.RoutingContext
import btools.router.RoutingEngine
import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.domain.model.GermanManeuverTranslator
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.RouteStep
import com.example.nagivator.domain.model.SpeedPreference
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.floor

data class OfflineRoutingSegmentInfo(
    val fileName: String,
    val sizeBytes: Long
) {
    val sizeMB: Double get() = sizeBytes / (1024.0 * 1024.0)
}

/**
 * On-device street-level offline routing powered by BRouter (`brouter-1.7.10-ro.jar`).
 * Uses 5°×5° `.rd5` routing tiles stored under `filesDir/brouter/segments4/` and bundled
 * routing profiles (`car-fast.brf`, `hiking-mountain.brf`, `lookups.dat`).
 */
class OfflineRoutingRepository(private val appContext: Context) {

    private val brouterRootDir: File get() = File(appContext.filesDir, "brouter")
    val segmentsDir: File get() = File(brouterRootDir, "segments4")
    private val profilesDir: File get() = File(brouterRootDir, "profiles2")

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun ensureDirectoriesAndProfiles() {
        segmentsDir.mkdirs()
        profilesDir.mkdirs()
        for (assetName in BUNDLED_PROFILES) {
            val target = File(profilesDir, assetName)
            if (!target.exists() || target.length() == 0L) {
                runCatching {
                    appContext.assets.open("brouter/profiles2/$assetName").use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Failed to unpack BRouter asset $assetName: ${e.message}")
                }
            }
        }
    }

    fun hasSegment(segmentFileName: String): Boolean {
        val f = File(segmentsDir, segmentFileName)
        return f.exists() && f.length() > 1024L
    }

    fun hasSegmentsFor(waypoints: List<LocationPoint>): Boolean {
        if (waypoints.size < 2) return false
        val required = waypoints.map { segmentNameFor(it.latitude, it.longitude) }.distinct()
        return required.all { hasSegment(it) }
    }

    fun listInstalledSegments(): List<OfflineRoutingSegmentInfo> {
        val files = segmentsDir.listFiles { f -> f.isFile && f.name.endsWith(".rd5") && f.length() > 1024L }
            ?: return emptyList()
        return files.sortedBy { it.name }.map { OfflineRoutingSegmentInfo(it.name, it.length()) }
    }

    fun deleteSegment(segmentFileName: String): Boolean {
        return File(segmentsDir, segmentFileName).delete()
    }

    fun deleteAllSegments() {
        segmentsDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Streams a 5°×5° `.rd5` routing segment from `brouter.de` to internal storage with
     * atomic rename on completion so interrupted downloads never corrupt the routing engine.
     */
    suspend fun downloadSegment(
        segmentFileName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<OfflineRoutingSegmentInfo> = withContext(Dispatchers.IO) {
        runCatching {
            ensureDirectoriesAndProfiles()
            val url = "$SEGMENTS_BASE_URL$segmentFileName"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val targetFile = File(segmentsDir, segmentFileName)
            val tmpFile = File(segmentsDir, "$segmentFileName.tmp")
            if (tmpFile.exists()) tmpFile.delete()

            val call = downloadClient.newCall(req)
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("BRouter Segment Download fehlgeschlagen (HTTP ${resp.code()})")
                    }
                    val body = resp.body() ?: throw IllegalStateException("Leere Antwort von brouter.de")
                    val totalBytes = body.contentLength()
                    var downloaded = 0L
                    var lastReportMs = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(tmpFile).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastReportMs >= 200L || (totalBytes > 0 && downloaded >= totalBytes)) {
                                    lastReportMs = now
                                    onProgress(downloaded, totalBytes)
                                }
                            }
                            output.flush()
                        }
                    }
                    if (downloaded < 1024L) {
                        tmpFile.delete()
                        throw IllegalStateException("Heruntergeladene Routing-Kachel ist ungültig ($downloaded Bytes)")
                    }
                    if (targetFile.exists()) targetFile.delete()
                    if (!tmpFile.renameTo(targetFile)) {
                        tmpFile.copyTo(targetFile, overwrite = true)
                        tmpFile.delete()
                    }
                    Log.i(TAG, "Downloaded BRouter segment $segmentFileName (${downloaded / (1024 * 1024)} MB)")
                    OfflineRoutingSegmentInfo(segmentFileName, targetFile.length())
                }
            } catch (ce: CancellationException) {
                call.cancel()
                if (tmpFile.exists()) tmpFile.delete()
                throw ce
            } catch (e: Throwable) {
                if (tmpFile.exists()) tmpFile.delete()
                throw e
            }
        }
    }

    /**
     * Computes a full street-following offline route with German turn-by-turn steps using
     * BRouter on-device. Returns `null` if required `.rd5` segments are missing or routing fails.
     */
    suspend fun computeOfflineRoute(
        waypoints: List<LocationPoint>,
        mode: TravelMode,
        footSpeedPref: SpeedPreference = SpeedPreference.NORMAL,
        carSpeedPref: SpeedPreference = SpeedPreference.NORMAL
    ): RouteResult? = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext null
        if (!hasSegmentsFor(waypoints)) {
            Log.d(TAG, "Offline routing skipped: missing .rd5 segment for ${waypoints.map { segmentNameFor(it.latitude, it.longitude) }.distinct()}")
            return@withContext null
        }

        ensureDirectoriesAndProfiles()
        val primaryProfile = if (mode == TravelMode.FOOT) "hiking-mountain" else "car-fast"
        val candidateProfiles = if (mode == TravelMode.CAR) {
            listOf(primaryProfile, "shortest")
        } else {
            listOf(primaryProfile)
        }

        val osmWaypoints = waypoints.mapIndexed { idx, pt ->
            OsmNodeNamed().apply {
                name = when (idx) {
                    0 -> "from"
                    waypoints.lastIndex -> "to"
                    else -> "via$idx"
                }
                ilon = ((pt.longitude + 180.0) * 1_000_000.0 + 0.5).toInt()
                ilat = ((pt.latitude + 90.0) * 1_000_000.0 + 0.5).toInt()
            }
        }

        for (profileBaseName in candidateProfiles) {
            val profileFile = File(profilesDir, "$profileBaseName.brf")
            if (!profileFile.exists()) continue

            try {
                val rc = RoutingContext().apply {
                    localFunction = profileFile.absolutePath
                    memoryclass = 64
                    keyValues = hashMapOf("turnInstructionMode" to "3")
                    turnInstructionMode = 3 // OsmAnd/comment-style voicehints with angles & roundabout exits
                    turnInstructionRoundabouts = true
                    turnInstructionCatchingRange = 40.0
                    considerTurnRestrictions = (mode == TravelMode.CAR)
                    carMode = (mode == TravelMode.CAR)
                    footMode = (mode == TravelMode.FOOT)
                }

                val startMs = System.currentTimeMillis()
                val engine = RoutingEngine(null, null, segmentsDir, osmWaypoints, rc)
                engine.quite = true
                engine.doRun(15_000L)

                val track = engine.foundTrack
                if (track == null || track.nodes == null || track.nodes.size < 2) {
                    Log.w(TAG, "Offline BRouter ($profileBaseName) returned no track: ${engine.errorMessage}")
                    continue
                }

                val rawGeoJson = FormatJson(rc).format(track)
                val parsed = parseBRouterGeoJson(
                    rawJson = rawGeoJson,
                    waypoints = waypoints,
                    mode = mode,
                    footSpeedPref = footSpeedPref,
                    carSpeedPref = carSpeedPref,
                    overrideCarSpeedEstimate = (mode == TravelMode.CAR && profileBaseName == "shortest")
                )
                val elapsedMs = System.currentTimeMillis() - startMs
                if (parsed != null) {
                    Log.i(
                        TAG,
                        "Offline BRouter SUCCESS ($profileBaseName) in ${elapsedMs}ms: " +
                            "${parsed.geometryPoints.size} pts, ${parsed.steps.size} steps, " +
                            "${"%.1f".format(parsed.totalDistanceMeters / 1000.0)} km"
                    )
                    return@withContext parsed
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Offline BRouter ($profileBaseName) failed: ${e.message}", e)
            }
        }
        null
    }

    companion object {
        private const val TAG = "OfflineRouting"
        private const val USER_AGENT = "Nagivator/1.0 (Android App; com.verhuelsdonk.navigator)"
        private const val SEGMENTS_BASE_URL = "https://brouter.de/brouter/segments4/"
        private val BUNDLED_PROFILES = listOf("lookups.dat", "car-fast.brf", "hiking-mountain.brf", "shortest.brf")
        private val json = Json { ignoreUnknownKeys = true }

        @Volatile
        private var instance: OfflineRoutingRepository? = null

        fun init(context: Context): OfflineRoutingRepository {
            return instance ?: synchronized(this) {
                instance ?: OfflineRoutingRepository(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(context: Context): OfflineRoutingRepository = init(context)

        fun instanceOrNull(): OfflineRoutingRepository? = instance

        /**
         * Returns the BRouter 5°×5° segment file name covering `(lat, lon)`, e.g. `E5_N50.rd5`.
         */
        fun segmentNameFor(lat: Double, lon: Double): String {
            val lonBase = (floor(lon / 5.0).toInt()) * 5
            val latBase = (floor(lat / 5.0).toInt()) * 5
            val lonStr = if (lonBase >= 0) "E$lonBase" else "W${-lonBase}"
            val latStr = if (latBase >= 0) "N$latBase" else "S${-latBase}"
            return "${lonStr}_${latStr}.rd5"
        }

        /**
         * Returns all distinct 5°×5° segment file names intersecting the given bounding box.
         */
        fun segmentsForBbox(
            minLat: Double,
            maxLat: Double,
            minLon: Double,
            maxLon: Double
        ): List<String> {
            val latStart = (floor(minLat / 5.0).toInt()) * 5
            val latEnd = (floor(maxLat / 5.0).toInt()) * 5
            val lonStart = (floor(minLon / 5.0).toInt()) * 5
            val lonEnd = (floor(maxLon / 5.0).toInt()) * 5
            val out = LinkedHashSet<String>()
            var lat = latStart
            while (lat <= latEnd) {
                var lon = lonStart
                while (lon <= lonEnd) {
                    out.add(segmentNameFor(lat.toDouble(), lon.toDouble()))
                    lon += 5
                }
                lat += 5
            }
            return out.toList()
        }

        /**
         * Parses BRouter GeoJSON output (from `FormatJson`) including `voicehints` into a full
         * turn-by-turn [RouteResult].
         */
        fun parseBRouterGeoJson(
            rawJson: String,
            waypoints: List<LocationPoint>,
            mode: TravelMode,
            footSpeedPref: SpeedPreference = SpeedPreference.NORMAL,
            carSpeedPref: SpeedPreference = SpeedPreference.NORMAL,
            overrideCarSpeedEstimate: Boolean = false
        ): RouteResult? {
            val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
            val feature = root["features"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val coords = feature["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return null
            if (coords.size < 2) return null

            val geomPoints = ArrayList<LocationPoint>(coords.size)
            val cumDist = DoubleArray(coords.size)
            val elePoints = ArrayList<Pair<Double, Double>>(coords.size)
            var ascent = 0.0
            var descent = 0.0

            for (i in coords.indices) {
                val c = coords[i].jsonArray
                if (c.size < 2) continue
                val lon = c[0].jsonPrimitive.doubleOrNull ?: continue
                val lat = c[1].jsonPrimitive.doubleOrNull ?: continue
                val ele = if (c.size >= 3) c[2].jsonPrimitive.doubleOrNull ?: 0.0 else 0.0
                val pt = LocationPoint(latitude = lat, longitude = lon)
                if (geomPoints.isNotEmpty()) {
                    val prev = geomPoints.last()
                    val segDist = CarNavigationHelper.haversineMeters(prev.latitude, prev.longitude, lat, lon)
                    cumDist[geomPoints.size] = cumDist[geomPoints.size - 1] + segDist
                    val prevEle = elePoints.lastOrNull()?.second ?: ele
                    val dEle = ele - prevEle
                    if (dEle > 0) ascent += dEle else descent += -dEle
                }
                geomPoints.add(pt)
                elePoints.add(cumDist[geomPoints.size - 1] to ele)
            }
            if (geomPoints.size < 2) return null

            val props = feature["properties"]?.jsonObject
            val totalDist = props?.get("track-length")?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: cumDist[geomPoints.lastIndex]
            val rawTotalTimeSec = props?.get("total-time")?.jsonPrimitive?.content?.toDoubleOrNull()
            val totalDur = if (mode == TravelMode.FOOT) {
                totalDist / footSpeedPref.footSpeedMps
            } else {
                val baseSec = if (overrideCarSpeedEstimate || rawTotalTimeSec == null) {
                    totalDist / 13.9 // ~50 km/h realistic rural/town car average
                } else {
                    rawTotalTimeSec
                }
                baseSec * carSpeedPref.carTimeFactor
            }
            val avgSpeedMps = (totalDist / totalDur.coerceAtLeast(1.0)).coerceAtLeast(1.0)

            val steps = mutableListOf<RouteStep>()
            val voiceHints = props?.get("voicehints")?.jsonArray
            if (voiceHints != null && voiceHints.isNotEmpty()) {
                var prevTrackIdx = 0
                for (vhEl in voiceHints) {
                    val arr = vhEl.jsonArray
                    if (arr.size < 4) continue
                    val trackIdx = (arr[0].jsonPrimitive.intOrNull ?: continue).coerceIn(0, geomPoints.lastIndex)
                    val cmdCode = arr[1].jsonPrimitive.intOrNull ?: 1
                    val exitNum = arr[2].jsonPrimitive.intOrNull ?: 0
                    val distToNext = arr[3].jsonPrimitive.doubleOrNull
                        ?: (cumDist[trackIdx] - cumDist[prevTrackIdx]).coerceAtLeast(0.0)

                    // Add initial depart step if first voicehint is well ahead of start
                    if (steps.isEmpty() && trackIdx > 0 && cumDist[trackIdx] > 25.0) {
                        val d0 = cumDist[trackIdx]
                        steps.add(
                            RouteStep(
                                instruction = GermanManeuverTranslator.translate("depart", "straight", ""),
                                distanceMeters = d0,
                                durationSeconds = d0 / avgSpeedMps,
                                maneuverType = "depart",
                                maneuverModifier = "straight",
                                maneuverLocation = geomPoints.first()
                            )
                        )
                    }

                    val mapped = mapVoiceHintCmd(cmdCode, exitNum) ?: continue
                    val instruction = GermanManeuverTranslator.translate(
                        type = mapped.maneuverType,
                        modifier = mapped.maneuverModifier,
                        rawName = "",
                        roundaboutExit = mapped.roundaboutExit
                    )
                    val stepDist = distToNext.coerceAtLeast(0.0)
                    steps.add(
                        RouteStep(
                            instruction = instruction,
                            distanceMeters = stepDist,
                            durationSeconds = stepDist / avgSpeedMps,
                            maneuverType = mapped.maneuverType,
                            maneuverModifier = mapped.maneuverModifier,
                            maneuverLocation = geomPoints[trackIdx],
                            roundaboutExit = mapped.roundaboutExit
                        )
                    )
                    prevTrackIdx = trackIdx
                }
            }

            if (steps.isEmpty()) {
                steps.add(
                    RouteStep(
                        instruction = GermanManeuverTranslator.translate("depart", "straight", ""),
                        distanceMeters = totalDist,
                        durationSeconds = totalDur,
                        maneuverType = "depart",
                        maneuverModifier = "straight",
                        maneuverLocation = geomPoints.first()
                    )
                )
            }
            if (steps.last().maneuverType != "arrive") {
                steps.add(
                    RouteStep(
                        instruction = GermanManeuverTranslator.translate("arrive", "", ""),
                        distanceMeters = 0.0,
                        durationSeconds = 0.0,
                        maneuverType = "arrive",
                        maneuverModifier = "straight",
                        maneuverLocation = geomPoints.last()
                    )
                )
            }

            return RouteResult(
                waypoints = waypoints,
                geometryPoints = geomPoints,
                totalDistanceMeters = totalDist,
                totalDurationSeconds = totalDur,
                mode = mode,
                steps = steps,
                totalAscentMeters = ascent,
                totalDescentMeters = descent,
                elevationPoints = elePoints,
                isOfflineFallback = false
            )
        }

        private data class MappedManeuver(
            val maneuverType: String,
            val maneuverModifier: String,
            val roundaboutExit: Int? = null
        )

        private fun mapVoiceHintCmd(cmdCode: Int, exitNumber: Int): MappedManeuver? {
            return when (cmdCode) {
                1 -> MappedManeuver("continue", "straight")
                2 -> MappedManeuver("turn", "left")
                3 -> MappedManeuver("turn", "slight left")
                4 -> MappedManeuver("turn", "sharp left")
                5 -> MappedManeuver("turn", "right")
                6 -> MappedManeuver("turn", "slight right")
                7 -> MappedManeuver("turn", "sharp right")
                8 -> MappedManeuver("fork", "slight left")
                9 -> MappedManeuver("fork", "slight right")
                10, 11, 15 -> MappedManeuver("turn", "uturn")
                13, 14 -> MappedManeuver("roundabout", "right", roundaboutExit = exitNumber.takeIf { it > 0 })
                17 -> MappedManeuver("off ramp", "slight left")
                18 -> MappedManeuver("off ramp", "slight right")
                100 -> MappedManeuver("arrive", "straight")
                else -> null
            }
        }
    }
}
