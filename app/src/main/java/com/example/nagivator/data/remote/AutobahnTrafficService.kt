package com.example.nagivator.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.http.GET
import retrofit2.http.Path

@Serializable
data class AutobahnCoordinate(
    val lat: JsonPrimitive? = null,
    @SerialName("long")
    val lon: JsonPrimitive? = null
) {
    fun latDouble(): Double? = lat?.content?.toDoubleOrNull()
    fun lonDouble(): Double? = lon?.content?.toDoubleOrNull()
}

@Serializable
data class AutobahnEventDto(
    val identifier: String = "",
    val title: String = "",
    val subtitle: String = "",
    val description: List<String> = emptyList(),
    val isBlocked: JsonPrimitive? = null,
    val future: JsonPrimitive? = null,
    val coordinate: AutobahnCoordinate? = null
) {
    fun isBlockedBool(): Boolean = isBlocked?.content?.equals("true", ignoreCase = true) == true
    fun isFutureBool(): Boolean = future?.content?.equals("true", ignoreCase = true) == true
}

@Serializable
data class AutobahnClosureResponse(
    val closure: List<AutobahnEventDto> = emptyList()
)

@Serializable
data class AutobahnWarningResponse(
    val warning: List<AutobahnEventDto> = emptyList()
)

@Serializable
data class AutobahnRoadworksResponse(
    val roadworks: List<AutobahnEventDto> = emptyList()
)

interface AutobahnTrafficApi {
    @GET("{roadId}/services/closure")
    suspend fun getClosures(@Path("roadId") roadId: String): AutobahnClosureResponse

    @GET("{roadId}/services/warning")
    suspend fun getWarnings(@Path("roadId") roadId: String): AutobahnWarningResponse

    @GET("{roadId}/services/roadworks")
    suspend fun getRoadworks(@Path("roadId") roadId: String): AutobahnRoadworksResponse
}
