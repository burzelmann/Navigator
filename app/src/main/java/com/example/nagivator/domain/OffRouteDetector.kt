package com.example.nagivator.domain

import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.TravelMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class OffRouteEvaluation(
    val shouldReroute: Boolean,
    val distanceMeters: Float,
    val closestSegmentIndex: Int,
    val isOffRoute: Boolean
)

/**
 * Fast, jitter-resilient off-route detector shared between Android Auto and Smartphone.
 *
 * Combines three physical signals to trigger rerouting in ~1–2 s instead of ~8–14 s:
 * 1. Severe lateral departure (`> 75 m` in CAR, `> 55 m` in FOOT): triggers immediately (1 fix).
 * 2. Heading divergence (`> 25 m` lateral AND speed >= 4 m/s AND heading >= 55° off route segment):
 *    triggers after 2 consecutive fixes (~1–2 s) to catch wrong turns at junctions immediately.
 * 3. Standard lateral departure (`> 38 m` in CAR, `> 28 m` in FOOT): triggers after 2 consecutive fixes.
 */
class OffRouteDetector(
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS
) {
    private var strikeCount: Int = 0
    private var lastRecalculateMs: Long = 0L

    fun evaluate(
        location: LocationPoint,
        routeGeometry: List<LocationPoint>,
        mode: TravelMode,
        nowMs: Long = System.currentTimeMillis()
    ): OffRouteEvaluation {
        if (routeGeometry.size < 2) {
            return OffRouteEvaluation(
                shouldReroute = false,
                distanceMeters = 0f,
                closestSegmentIndex = 0,
                isOffRoute = false
            )
        }

        val (minDist, closestIdx) = CarNavigationHelper.distanceToPolyline(location, routeGeometry)

        val immediateThreshold = if (mode == TravelMode.CAR) CAR_IMMEDIATE_METERS else FOOT_IMMEDIATE_METERS
        val standardThreshold = if (mode == TravelMode.CAR) CAR_STANDARD_METERS else FOOT_STANDARD_METERS
        val cooldownElapsed = (nowMs - lastRecalculateMs) >= cooldownMs

        // Tier 1: Severe departure — no GPS jitter jumps > 75 m
        if (minDist > immediateThreshold) {
            strikeCount++
            if (cooldownElapsed) {
                strikeCount = 0
                return OffRouteEvaluation(true, minDist, closestIdx, true)
            }
            return OffRouteEvaluation(false, minDist, closestIdx, true)
        }

        // Tier 2: Heading divergence at speed (wrong turn / opposite direction)
        val hasHeadingDivergence = mode == TravelMode.CAR &&
            minDist > CAR_HEADING_MIN_METERS &&
            location.speedMps >= HEADING_MIN_SPEED_MPS &&
            isHeadingDivergent(location.bearing, routeGeometry, closestIdx)

        // Tier 3: Standard lateral departure
        if (minDist > standardThreshold || hasHeadingDivergence) {
            strikeCount++
            if (strikeCount >= STRIKES_REQUIRED && cooldownElapsed) {
                strikeCount = 0
                return OffRouteEvaluation(true, minDist, closestIdx, true)
            }
            return OffRouteEvaluation(false, minDist, closestIdx, true)
        }

        strikeCount = 0
        return OffRouteEvaluation(false, minDist, closestIdx, false)
    }

    fun markRecalculateStarted(nowMs: Long = System.currentTimeMillis()) {
        lastRecalculateMs = nowMs
        strikeCount = 0
    }

    fun reset() {
        strikeCount = 0
        lastRecalculateMs = 0L
    }

    companion object {
        const val CAR_IMMEDIATE_METERS = 75f
        const val FOOT_IMMEDIATE_METERS = 55f
        const val CAR_STANDARD_METERS = 38f
        const val FOOT_STANDARD_METERS = 28f
        const val CAR_HEADING_MIN_METERS = 25f
        const val HEADING_MIN_SPEED_MPS = 4.0f // ~14.4 km/h
        const val HEADING_DIVERGENCE_DEG = 55f
        const val STRIKES_REQUIRED = 2
        const val DEFAULT_COOLDOWN_MS = 4_000L

        internal fun isHeadingDivergent(
            userBearingDeg: Float,
            geometry: List<LocationPoint>,
            segmentIndex: Int
        ): Boolean {
            if (geometry.size < 2) return false
            val idx = segmentIndex.coerceIn(0, geometry.size - 2)
            val segBearing = bearingDegrees(geometry[idx], geometry[idx + 1])
            val delta = smallestAngleDiffDeg(userBearingDeg, segBearing)
            return delta >= HEADING_DIVERGENCE_DEG
        }

        internal fun bearingDegrees(from: LocationPoint, to: LocationPoint): Float {
            val lat1 = Math.toRadians(from.latitude)
            val lat2 = Math.toRadians(to.latitude)
            val dLon = Math.toRadians(to.longitude - from.longitude)
            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            val brng = Math.toDegrees(atan2(y, x))
            return ((brng + 360.0) % 360.0).toFloat()
        }

        internal fun smallestAngleDiffDeg(a: Float, b: Float): Float {
            val diff = abs(((a - b + 540f) % 360f) - 180f)
            return diff
        }
    }
}
