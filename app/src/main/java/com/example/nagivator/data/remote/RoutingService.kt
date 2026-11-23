package com.example.nagivator.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class OsrmWaypoint(
    val name: String = "",
    val location: List<Double>
)

@Serializable
data class OsrmLane(
    val indications: List<String> = emptyList(),
    val valid: Boolean = false
)

@Serializable
data class OsrmIntersection(
    val lanes: List<OsrmLane> = emptyList()
)

@Serializable
data class OsrmStep(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val name: String = "",
    val ref: String = "",
    val destinations: String = "",
    val exits: String = "",
    @SerialName("rotary_name") val rotaryName: String = "",
    val maneuver: OsrmManeuver? = null,
    val intersections: List<OsrmIntersection> = emptyList()
)

@Serializable
data class OsrmManeuver(
    val type: String = "",
    val modifier: String? = null,
    val exit: Int? = null,
    val location: List<Double> = emptyList()
)

@Serializable
data class OsrmLeg(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val steps: List<OsrmStep> = emptyList()
)

@Serializable
data class OsrmGeometry(
    val coordinates: List<List<Double>> = emptyList(),
    val type: String = "LineString"
)

@Serializable
data class OsrmRoute(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val legs: List<OsrmLeg> = emptyList(),
    val geometry: OsrmGeometry? = null
)

@Serializable
data class OsrmResponse(
    val code: String = "",
    val routes: List<OsrmRoute> = emptyList(),
    val waypoints: List<OsrmWaypoint> = emptyList()
)

interface RoutingApi {
    // profile = "foot" or "driving"
    // coordinates format: "lon1,lat1;lon2,lat2"
    @GET("route/v1/{profile}/{coordinates}")
    suspend fun getRoute(
        @Path("profile") profile: String,
        @Path("coordinates") coordinates: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Query("steps") steps: Boolean = true,
        @Query("alternatives") alternatives: Boolean = false,
        @Query("continue_straight") continueStraight: Boolean = false,
        @Query("radiuses") radiuses: String? = null
    ): OsrmResponse
}
