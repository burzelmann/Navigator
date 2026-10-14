package com.example.nagivator.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class BRouterFeatureProperties(
    @SerialName("track-length") val trackLength: String? = "0",
    @SerialName("total-time") val totalTime: String? = "0"
)

@Serializable
data class BRouterGeometry(
    val coordinates: List<List<Double>> = emptyList(),
    val type: String = "LineString"
)

@Serializable
data class BRouterFeature(
    val geometry: BRouterGeometry = BRouterGeometry(),
    val properties: BRouterFeatureProperties = BRouterFeatureProperties()
)

@Serializable
data class BRouterResponse(
    val features: List<BRouterFeature> = emptyList()
)

interface BRouterApi {
    @GET("brouter")
    suspend fun getRoute(
        @Query("lonlats", encoded = true) lonlats: String,
        @Query("profile") profile: String = "trekking",
        @Query("alternativeidx") alternativeIdx: Int = 0,
        @Query("format") format: String = "geojson"
    ): BRouterResponse
}
