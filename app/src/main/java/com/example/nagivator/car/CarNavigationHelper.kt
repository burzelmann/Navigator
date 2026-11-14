package com.example.nagivator.car

import androidx.car.app.model.Distance
import com.example.nagivator.data.repository.AppThemeMode
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.RouteStep
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure math and navigation helpers for Android Auto map rendering and turn-by-turn guidance.
 */
object CarNavigationHelper {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Interpolates between two compass bearings (in degrees) along the shortest angular path.
     */
    fun interpolateAngle(fromDeg: Float, toDeg: Float, fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        var delta = ((toDeg - fromDeg) % 360f + 540f) % 360f - 180f
        if (delta == -180f) delta = 180f
        return ((fromDeg + delta * t) % 360f + 360f) % 360f
    }

    /**
     * Linearly interpolates coordinates and bearing between two [LocationPoint]s.
     */
    fun interpolateLocation(from: LocationPoint, to: LocationPoint, fraction: Float): LocationPoint {
        val t = fraction.coerceIn(0f, 1f)
        return to.copy(
            latitude = from.latitude + (to.latitude - from.latitude) * t,
            longitude = from.longitude + (to.longitude - from.longitude) * t,
            bearing = interpolateAngle(from.bearing, to.bearing, t)
        )
    }

    /**
     * Preserves the last valid bearing while stationary so the map does not snap to North at red lights.
     */
    fun preserveBearingIfStationary(previous: LocationPoint?, incoming: LocationPoint): LocationPoint {
        if (previous == null) return incoming
        val isStationaryWithoutBearing = incoming.speedMps < 0.8f && incoming.bearing == 0f
        return if (isStationaryWithoutBearing) {
            incoming.copy(bearing = previous.bearing)
        } else {
            incoming
        }
    }

    /**
     * Skips the initial "depart" step if a following maneuver step exists so the first real turn is shown immediately.
     */
    fun initialStepIndex(steps: List<RouteStep>): Int {
        return if (steps.size > 1 && steps[0].maneuverType.equals("depart", ignoreCase = true)) {
            1
        } else {
            0
        }
    }

    /**
     * Formats a distance in meters into an Android Auto [Distance] using meters below 1 km and 0.1 km precision above.
     */
    fun createCarDistance(meters: Double): Distance {
        val safeMeters = meters.coerceAtLeast(0.0)
        return if (safeMeters < 1000.0) {
            val rounded = ((safeMeters / 10.0).roundToInt() * 10).toDouble()
            Distance.create(rounded, Distance.UNIT_METERS)
        } else {
            Distance.create(safeMeters / 1000.0, Distance.UNIT_KILOMETERS_P1)
        }
    }

    /**
     * Computes great-circle distance in meters between two WGS84 coordinates.
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Computes the remaining distance in meters along [geometryPoints], optionally starting from [currentLoc].
     */
    fun remainingPolylineMeters(
        currentLoc: LocationPoint?,
        geometryPoints: List<LocationPoint>
    ): Double {
        if (geometryPoints.isEmpty()) return 0.0
        var total = 0.0
        if (currentLoc != null) {
            total += haversineMeters(
                currentLoc.latitude, currentLoc.longitude,
                geometryPoints[0].latitude, geometryPoints[0].longitude
            )
        }
        for (i in 0 until geometryPoints.size - 1) {
            val p1 = geometryPoints[i]
            val p2 = geometryPoints[i + 1]
            total += haversineMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        }
        return total
    }

    /**
     * Estimates remaining travel time in seconds proportionally from the route's original total distance and duration.
     */
    fun estimateRemainingSeconds(route: RouteResult, remainingMeters: Double): Double {
        if (route.totalDistanceMeters <= 0.0) return route.totalDurationSeconds.coerceAtLeast(0.0)
        val ratio = (remainingMeters / route.totalDistanceMeters).coerceIn(0.0, 1.0)
        return (route.totalDurationSeconds * ratio).coerceAtLeast(0.0)
    }

    /**
     * Formats a search result subtitle with straight-line distance (when GPS is available) and address.
     */
    fun formatSearchSubtitle(currentLoc: LocationPoint?, destination: LocationPoint): String {
        val addr = destination.address.ifBlank { "Gefundener Ort" }
        if (currentLoc == null) return addr
        val distMeters = haversineMeters(
            currentLoc.latitude, currentLoc.longitude,
            destination.latitude, destination.longitude
        )
        val distStr = if (distMeters < 1000.0) {
            "${((distMeters / 10.0).roundToInt() * 10)} m"
        } else {
            "%.1f km".format(distMeters / 1000.0)
        }
        return "$distStr • $addr"
    }

    /**
     * Computes the shortest distance in meters from [loc] to any segment of [geometryPoints],
     * returning `(distanceMeters, closestSegmentStartIndex)`.
     *
     * Projecting onto line segments (rather than measuring distance to vertices only) avoids false
     * off-route triggers on long straight rural/forest roads where OSRM vertices are far apart.
     */
    fun distanceToPolyline(
        loc: LocationPoint,
        geometryPoints: List<LocationPoint>
    ): Pair<Float, Int> {
        if (geometryPoints.isEmpty()) return Pair(Float.MAX_VALUE, 0)
        if (geometryPoints.size == 1) {
            val d = haversineMeters(
                loc.latitude, loc.longitude,
                geometryPoints[0].latitude, geometryPoints[0].longitude
            ).toFloat()
            return Pair(d, 0)
        }

        val cosLat = cos(Math.toRadians(loc.latitude)).coerceAtLeast(0.01)
        var minDist = Float.MAX_VALUE
        var minIdx = 0

        for (i in 0 until geometryPoints.size - 1) {
            val a = geometryPoints[i]
            val b = geometryPoints[i + 1]

            // Local equirectangular projection relative to A
            val bx = (b.longitude - a.longitude) * cosLat
            val by = b.latitude - a.latitude
            val px = (loc.longitude - a.longitude) * cosLat
            val py = loc.latitude - a.latitude

            val lenSq = bx * bx + by * by
            val t = if (lenSq > 0.0) {
                ((px * bx + py * by) / lenSq).coerceIn(0.0, 1.0)
            } else {
                0.0
            }

            val projLat = a.latitude + t * by
            val projLon = a.longitude + t * (b.longitude - a.longitude)
            val dist = haversineMeters(loc.latitude, loc.longitude, projLat, projLon).toFloat()
            if (dist < minDist) {
                minDist = dist
                minIdx = i
            }
        }
        return Pair(minDist, minIdx)
    }

    /**
     * Cycles through theme modes in the order SYSTEM -> LIGHT -> DARK -> SYSTEM.
     */
    fun nextThemeMode(current: AppThemeMode): AppThemeMode = when (current) {
        AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
        AppThemeMode.LIGHT -> AppThemeMode.DARK
        AppThemeMode.DARK -> AppThemeMode.SYSTEM
    }
}

