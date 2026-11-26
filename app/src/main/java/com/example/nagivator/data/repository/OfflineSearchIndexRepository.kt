package com.example.nagivator.data.repository

import android.content.Context
import android.util.Log
import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.data.local.NagivatorDatabase
import com.example.nagivator.data.local.dao.OfflineSearchPlaceDao
import com.example.nagivator.data.local.entity.OfflineSearchPlaceEntity
import com.example.nagivator.domain.model.LocationPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Downloads and queries a compact on-device offline search index (towns, named streets,
 * and key POIs) per downloaded offline map region via OpenStreetMap Overpass API.
 */
class OfflineSearchIndexRepository(private val dao: OfflineSearchPlaceDao) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches towns, named streets, and key POIs inside the region's bounding box and stores
     * them in Room for instant (<10 ms) offline search.
     *
     * Strategy:
     * 1. Primary: Overpass API (complete index of all streets, towns, and POIs in the bbox).
     * 2. Guaranteed Fallback: Photon bbox harvesting (fast, resilient when Overpass mirrors
     *    are at capacity with HTTP 502/504).
     *
     * Returns the number of indexed entries.
     */
    suspend fun buildIndexForRegion(
        regionName: String,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): Int = withContext(Dispatchers.IO) {
        val overpassPlaces = fetchFromOverpass(regionName, minLat, maxLat, minLon, maxLon)
        val places = if (overpassPlaces.isNotEmpty()) {
            overpassPlaces
        } else {
            Log.i(TAG, "Overpass unavailable for '$regionName', using Photon bbox fallback")
            fetchFromPhotonBbox(regionName, minLat, maxLat, minLon, maxLon)
        }

        if (places.isNotEmpty()) {
            dao.deleteByRegion(regionName)
            places.chunked(500).forEach { batch ->
                dao.insertAll(batch)
            }
            Log.i(TAG, "Indexed ${places.size} offline search places for region '$regionName'")
        }
        places.size
    }

    private fun fetchFromOverpass(
        regionName: String,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<OfflineSearchPlaceEntity> {
        val bbox = String.format(java.util.Locale.US, "%.5f,%.5f,%.5f,%.5f", minLat, minLon, maxLat, maxLon)
        val overpassQuery = """
            [out:json][timeout:12];
            (
              node["place"]["name"]($bbox);
              way["highway"]["name"]($bbox);
              node["amenity"~"^(fuel|pharmacy|hospital|parking|restaurant|cafe|charging_station)$"]["name"]($bbox);
              node["shop"~"^(supermarket|bakery|convenience)$"]["name"]($bbox);
              node["railway"~"^(station|halt)$"]["name"]($bbox);
            );
            out center qt;
        """.trimIndent()

        for (endpoint in OVERPASS_ENDPOINTS) {
            try {
                val formBody = FormBody.Builder()
                    .add("data", overpassQuery)
                    .build()
                val req = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", USER_AGENT)
                    .post(formBody)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body()?.string()
                        if (!body.isNullOrBlank()) {
                            val parsed = parseOverpassJson(body, regionName)
                            if (parsed.isNotEmpty()) return parsed
                        }
                    } else {
                        Log.w(TAG, "Overpass $endpoint HTTP ${resp.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Overpass endpoint $endpoint failed: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun fetchFromPhotonBbox(
        regionName: String,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<OfflineSearchPlaceEntity> {
        val bbox = String.format(java.util.Locale.US, "%.5f,%.5f,%.5f,%.5f", minLon, minLat, maxLon, maxLat)
        val seeds = LinkedHashSet<String>()
        val cleanRegion = regionName.trim()
        if (cleanRegion.length >= 2) seeds.add(cleanRegion)

        // Reverse-geocode center to also capture the actual town/city name if region has a custom label
        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLon + maxLon) / 2.0
        runCatching {
            val revUrl = String.format(
                java.util.Locale.US,
                "https://photon.komoot.io/reverse?lat=%.5f&lon=%.5f&lang=de",
                centerLat,
                centerLon
            )
            val req = Request.Builder()
                .url(revUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "de-DE,de;q=0.9")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body()?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val props = root["features"]?.jsonArray?.firstOrNull()?.jsonObject?.get("properties")?.jsonObject
                    val city = props?.get("city")?.jsonPrimitive?.content?.trim().orEmpty()
                    if (city.length >= 2) seeds.add(city)
                }
            }
        }

        seeds.addAll(PHOTON_FALLBACK_SEEDS)

        val dedup = LinkedHashMap<String, OfflineSearchPlaceEntity>()
        for ((idx, seed) in seeds.withIndex()) {
            if (idx > 0) Thread.sleep(250)
            try {
                val encoded = java.net.URLEncoder.encode(seed, "UTF-8")
                val url = "https://photon.komoot.io/api/?q=$encoded&bbox=$bbox&limit=50&lang=de"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "de-DE,de;q=0.9")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body()?.string().orEmpty()
                        for (entry in parsePhotonBboxJson(body, regionName)) {
                            val key = "${entry.normalizedName}|${entry.category}|${entry.subtitle.lowercase()}"
                            dedup.putIfAbsent(key, entry)
                        }
                    } else {
                        Log.w(TAG, "Photon bbox seed '$seed' HTTP ${resp.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Photon bbox seed '$seed' failed: ${e.message}")
            }
        }
        return dedup.values.toList()
    }

    suspend fun deleteIndexForRegion(regionName: String) = withContext(Dispatchers.IO) {
        dao.deleteByRegion(regionName)
    }

    suspend fun searchOffline(
        query: String,
        biasLat: Double? = null,
        biasLon: Double? = null,
        limit: Int = 8
    ): List<LocationPoint> = withContext(Dispatchers.IO) {
        val clean = query.trim().lowercase()
        if (clean.length < 2) return@withContext emptyList()

        val rawMatches = dao.search(
            containsPattern = "%$clean%",
            prefixPattern = "$clean%",
            limit = (limit * 3).coerceAtLeast(18)
        )

        val ranked = if (biasLat != null && biasLon != null) {
            rawMatches.sortedWith(
                compareBy<OfflineSearchPlaceEntity>(
                    { if (it.normalizedName.startsWith(clean)) 0 else 1 },
                    { -it.priority },
                    { CarNavigationHelper.haversineMeters(biasLat, biasLon, it.latitude, it.longitude) }
                )
            )
        } else {
            rawMatches
        }

        ranked.take(limit).map { place ->
            LocationPoint(
                latitude = place.latitude,
                longitude = place.longitude,
                name = place.name,
                address = place.subtitle
            )
        }
    }

    companion object {
        private const val TAG = "OfflineSearchIndex"
        private const val USER_AGENT = "Nagivator/1.0 (Android App; com.verhuelsdonk.navigator)"
        private val json = Json { ignoreUnknownKeys = true }
        private val OVERPASS_ENDPOINTS = listOf(
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
        private val PHOTON_FALLBACK_SEEDS = listOf(
            "straße", "weg", "platz", "allee",
            "tankstelle", "apotheke", "supermarkt", "bahnhof"
        )

        fun getInstance(context: Context): OfflineSearchIndexRepository {
            val db = NagivatorDatabase.getDatabase(context.applicationContext)
            return OfflineSearchIndexRepository(db.offlineSearchPlaceDao())
        }

        /**
         * Parses a Photon GeoJSON response (from a bbox query) into [OfflineSearchPlaceEntity] items.
         */
        fun parsePhotonBboxJson(rawJson: String, regionName: String): List<OfflineSearchPlaceEntity> {
            val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull()
                ?: return emptyList()
            val features = root["features"]?.jsonArray ?: return emptyList()
            val results = mutableListOf<OfflineSearchPlaceEntity>()

            for (feat in features) {
                val obj = feat.jsonObject
                val coords = obj["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: continue
                if (coords.size < 2) continue
                val lon = coords[0].jsonPrimitive.doubleOrNull ?: continue
                val lat = coords[1].jsonPrimitive.doubleOrNull ?: continue

                val props = obj["properties"]?.jsonObject ?: continue
                val name = props["name"]?.jsonPrimitive?.content?.trim()
                    ?: props["street"]?.jsonPrimitive?.content?.trim()
                    ?: continue
                if (name.isBlank()) continue

                val osmKey = props["osm_key"]?.jsonPrimitive?.content
                val osmValue = props["osm_value"]?.jsonPrimitive?.content
                val (category, priority) = classifyTags(
                    place = if (osmKey == "place") osmValue else null,
                    highway = if (osmKey == "highway") osmValue else null,
                    amenity = if (osmKey == "amenity") osmValue else null,
                    shop = if (osmKey == "shop") osmValue else null,
                    railway = if (osmKey == "railway") osmValue else null
                ) ?: ("Ort" to 1)

                val cityHint = props["city"]?.jsonPrimitive?.content?.trim().orEmpty()
                val subtitle = buildString {
                    append(category)
                    if (cityHint.isNotBlank() && !cityHint.equals(name, ignoreCase = true)) {
                        append(" • ")
                        append(cityHint)
                    }
                    append(" • Offline (")
                    append(regionName)
                    append(")")
                }

                results.add(
                    OfflineSearchPlaceEntity(
                        regionName = regionName,
                        name = name,
                        normalizedName = name.lowercase(),
                        category = category,
                        subtitle = subtitle,
                        latitude = lat,
                        longitude = lon,
                        priority = priority
                    )
                )
            }
            return results
        }

        /**
         * Parses an Overpass JSON response into deduplicated [OfflineSearchPlaceEntity] items.
         * Multiple OSM way segments sharing the same street name are merged to their average centroid.
         */
        fun parseOverpassJson(rawJson: String, regionName: String): List<OfflineSearchPlaceEntity> {
            val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull()
                ?: return emptyList()
            val elements = root["elements"]?.jsonArray ?: return emptyList()

            data class Acc(
                val name: String,
                val category: String,
                val cityHint: String,
                val priority: Int,
                var sumLat: Double = 0.0,
                var sumLon: Double = 0.0,
                var count: Int = 0
            )

            val merged = LinkedHashMap<String, Acc>()

            for (el in elements) {
                val obj = el.jsonObject
                val tags = obj["tags"]?.jsonObject ?: continue
                val name = tags["name"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (name.isBlank()) continue

                val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.doubleOrNull
                    ?: continue
                val lon = obj["lon"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.doubleOrNull
                    ?: continue

                val (category, priority) = classifyTags(
                    place = tags["place"]?.jsonPrimitive?.content,
                    highway = tags["highway"]?.jsonPrimitive?.content,
                    amenity = tags["amenity"]?.jsonPrimitive?.content,
                    shop = tags["shop"]?.jsonPrimitive?.content,
                    railway = tags["railway"]?.jsonPrimitive?.content
                ) ?: continue

                val cityHint = tags["addr:city"]?.jsonPrimitive?.content?.trim().orEmpty()
                // Deduplicate same name + category (+ city if known) to merge multi-segment streets
                val key = "${name.lowercase()}|$category|${cityHint.lowercase()}"
                val acc = merged.getOrPut(key) { Acc(name, category, cityHint, priority) }
                acc.sumLat += lat
                acc.sumLon += lon
                acc.count++
            }

            return merged.values.map { acc ->
                val avgLat = acc.sumLat / acc.count
                val avgLon = acc.sumLon / acc.count
                val subtitle = buildString {
                    append(acc.category)
                    if (acc.cityHint.isNotBlank()) {
                        append(" • ")
                        append(acc.cityHint)
                    }
                    append(" • Offline (")
                    append(regionName)
                    append(")")
                }
                OfflineSearchPlaceEntity(
                    regionName = regionName,
                    name = acc.name,
                    normalizedName = acc.name.lowercase(),
                    category = acc.category,
                    subtitle = subtitle,
                    latitude = avgLat,
                    longitude = avgLon,
                    priority = acc.priority
                )
            }
        }

        private fun classifyTags(
            place: String?,
            highway: String?,
            amenity: String?,
            shop: String?,
            railway: String?
        ): Pair<String, Int>? {
            if (!place.isNullOrBlank()) {
                val label = when (place) {
                    "city", "town" -> "Ort"
                    "village", "hamlet" -> "Ortschaft"
                    "suburb" -> "Ortsteil"
                    else -> "Ort"
                }
                return label to 3
            }
            if (!railway.isNullOrBlank()) return "Bahnhof" to 2
            if (!amenity.isNullOrBlank()) {
                val label = when (amenity) {
                    "fuel" -> "Tankstelle"
                    "charging_station" -> "Ladestation"
                    "pharmacy" -> "Apotheke"
                    "hospital" -> "Krankenhaus"
                    "parking" -> "Parkplatz"
                    "restaurant" -> "Restaurant"
                    "cafe" -> "Café"
                    else -> "POI"
                }
                return label to 2
            }
            if (!shop.isNullOrBlank()) {
                val label = when (shop) {
                    "supermarket" -> "Supermarkt"
                    "bakery" -> "Bäckerei"
                    "convenience" -> "Einkauf"
                    else -> "Geschäft"
                }
                return label to 2
            }
            if (!highway.isNullOrBlank()) return "Straße" to 1
            return null
        }
    }
}
