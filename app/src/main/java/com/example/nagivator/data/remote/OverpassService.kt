package com.example.nagivator.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class OsmMapElement(
    val type: String = "",
    val id: Long = 0L,
    val lat: Double? = null,
    val lon: Double? = null,
    val nodes: List<Long> = emptyList(),
    val tags: Map<String, String> = emptyMap()
)

@Serializable
data class OsmMapResponse(
    val elements: List<OsmMapElement> = emptyList()
)

/**
 * Official OpenStreetMap 0.6 Map JSON endpoint (keyless, fast & reliable for small local bounding boxes).
 */
interface OsmMapApi {
    @GET("api/0.6/map.json")
    suspend fun getMap(@Query("bbox") bbox: String): OsmMapResponse
}
