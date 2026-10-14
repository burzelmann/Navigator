package com.example.nagivator.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class PhotonProperties(
    val name: String? = null,
    val country: String? = null,
    val city: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val osm_type: String? = null
)

@Serializable
data class PhotonPointGeometry(
    val coordinates: List<Double> = emptyList(),
    val type: String = "Point"
)

@Serializable
data class PhotonFeature(
    val properties: PhotonProperties,
    val geometry: PhotonPointGeometry
)

@Serializable
data class PhotonResponse(
    val features: List<PhotonFeature> = emptyList()
)

interface GeocodingApi {
    @GET("api/")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("lang") lang: String = "de"
    ): PhotonResponse

    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("lang") lang: String = "de"
    ): PhotonResponse
}
