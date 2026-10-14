package com.example.nagivator.domain.model

data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val maneuverType: String = "",
    val maneuverModifier: String = "",
    val maneuverLocation: LocationPoint? = null
)

data class RouteResult(
    val waypoints: List<LocationPoint>,
    val geometryPoints: List<LocationPoint>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val mode: TravelMode,
    val steps: List<RouteStep> = emptyList(),
    val totalAscentMeters: Double = 0.0,
    val totalDescentMeters: Double = 0.0,
    val elevationPoints: List<Pair<Double, Double>> = emptyList() // Pair<DistanceMeters, AltitudeMeters>
) {
    val formattedDistance: String
        get() = if (totalDistanceMeters >= 1000) {
            "%.1f km".format(totalDistanceMeters / 1000.0)
        } else {
            "${totalDistanceMeters.toInt()} m"
        }

    val formattedDuration: String
        get() {
            val minutes = (totalDurationSeconds / 60.0).toInt()
            val hours = minutes / 60
            val remainingMins = minutes % 60
            return if (hours > 0) {
                "${hours} Std. ${remainingMins} Min."
            } else {
                "${minutes} Min."
            }
        }

    val formattedElevation: String
        get() = if (totalAscentMeters > 0 || totalDescentMeters > 0) {
            "↗ ${totalAscentMeters.toInt()}m  ↘ ${totalDescentMeters.toInt()}m"
        } else ""
}
