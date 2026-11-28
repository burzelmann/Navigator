package com.example.nagivator.domain.model

enum class RoadSignalType {
    TRAFFIC_SIGNALS,
    STOP,
    GIVE_WAY,
    ROUNDABOUT
}

data class RoadSignal(
    val id: Long,
    val type: RoadSignalType,
    val latitude: Double,
    val longitude: Double
)

enum class TrafficIncidentType {
    CLOSURE,
    WARNING,
    ROADWORK
}

data class TrafficIncident(
    val id: String,
    val roadId: String,
    val type: TrafficIncidentType,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val latitude: Double,
    val longitude: Double,
    val isBlocked: Boolean = false,
    val delaySeconds: Int? = null,
    val source: String = "Autobahn"
)

data class RoadContextSnapshot(
    val speedLimitKmh: Int?,
    val signals: List<RoadSignal>,
    val centerLat: Double,
    val centerLon: Double,
    val fetchedAtMs: Long
)
