package com.example.nagivator.domain

import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TravelMode

/**
 * Result of analyzing a completed car trip against the initially suggested route.
 */
data class TripDeviationOutcome(
    val origin: LocationPoint,
    val destination: LocationPoint,
    val anchor1: LocationPoint?,
    val anchor2: LocationPoint? = null,
    val demoteLearnedRouteId: Long? = null
)

/**
 * Pure JVM-testable tracker that monitors a single active CAR navigation trip to detect
 * intentional route deviations (while filtering out GPS noise, brief errands/detours,
 * and forced Autobahn closure reroutes).
 */
class RouteDeviationTracker {

    private var origin: LocationPoint? = null
    private var destination: LocationPoint? = null
    private var travelMode: TravelMode = TravelMode.CAR
    private var initialPolyline: List<LocationPoint> = emptyList()
    private var startedLearnedRouteId: Long? = null
    private var forcedClosureReroute: Boolean = false
    private val trackPoints = mutableListOf<LocationPoint>()

    fun startTrip(route: RouteResult) {
        reset()
        if (route.mode != TravelMode.CAR) return
        if (route.isOfflineFallback) return
        val start = route.waypoints.firstOrNull() ?: route.geometryPoints.firstOrNull() ?: return
        val dest = route.waypoints.lastOrNull() ?: route.geometryPoints.lastOrNull() ?: return
        if (route.geometryPoints.size < 2) return

        origin = start
        destination = dest
        travelMode = route.mode
        initialPolyline = route.geometryPoints.toList()
        startedLearnedRouteId = route.learnedRouteId
        forcedClosureReroute = false
        trackPoints.add(start)
    }

    fun markForcedClosureReroute() {
        forcedClosureReroute = true
    }

    fun recordLocation(point: LocationPoint) {
        if (origin == null || travelMode != TravelMode.CAR) return
        val last = trackPoints.lastOrNull()
        if (last == null) {
            trackPoints.add(point)
            return
        }
        val dist = CarNavigationHelper.haversineMeters(
            last.latitude, last.longitude,
            point.latitude, point.longitude
        )
        if (dist >= TRACK_SAMPLE_INTERVAL_M) {
            trackPoints.add(point)
        }
    }

    fun reset() {
        origin = null
        destination = null
        initialPolyline = emptyList()
        startedLearnedRouteId = null
        forcedClosureReroute = false
        trackPoints.clear()
    }

    /**
     * Evaluates the recorded track when the driver arrives at (or near) the original destination.
     * Returns `null` if no intentional deviation occurred (or if tracking was disabled/forced).
     */
    fun evaluateAtArrival(): TripDeviationOutcome? {
        val start = origin ?: return null
        val dest = destination ?: return null
        if (travelMode != TravelMode.CAR || forcedClosureReroute) return null
        if (initialPolyline.size < 2 || trackPoints.size < 4) return null

        // Collect all track points significantly outside the initial route corridor (> 150 m)
        val deviatedPoints = mutableListOf<Pair<LocationPoint, Float>>()
        var maxDeviationMeters = 0f
        var deviatedDistanceMeters = 0.0
        var prevPoint: LocationPoint? = null

        for (pt in trackPoints) {
            val (distToInitial, _) = CarNavigationHelper.distanceToPolyline(pt, initialPolyline)
            if (distToInitial > CORRIDOR_TOLERANCE_M) {
                deviatedPoints.add(pt to distToInitial)
                if (distToInitial > maxDeviationMeters) {
                    maxDeviationMeters = distToInitial
                }
                if (prevPoint != null) {
                    deviatedDistanceMeters += CarNavigationHelper.haversineMeters(
                        prevPoint.latitude, prevPoint.longitude,
                        pt.latitude, pt.longitude
                    )
                }
                prevPoint = pt
            } else {
                prevPoint = null
            }
        }

        val isIntentionalDeviation =
            maxDeviationMeters >= MIN_MAX_DEVIATION_M &&
                deviatedDistanceMeters >= MIN_DEVIATED_LENGTH_M &&
                deviatedPoints.isNotEmpty()

        if (!isIntentionalDeviation) {
            return null
        }

        // If the trip started on an already-learned route and the driver deliberately left it,
        // demote that learned route (and also extract the new corridor as a fresh candidate).
        val (anchor1, anchor2) = extractAnchors(deviatedPoints, deviatedDistanceMeters)
        return TripDeviationOutcome(
            origin = start,
            destination = dest,
            anchor1 = anchor1,
            anchor2 = anchor2,
            demoteLearnedRouteId = startedLearnedRouteId
        )
    }

    private fun extractAnchors(
        deviatedPoints: List<Pair<LocationPoint, Float>>,
        deviatedDistanceMeters: Double
    ): Pair<LocationPoint, LocationPoint?> {
        if (deviatedDistanceMeters >= TWO_ANCHOR_THRESHOLD_M && deviatedPoints.size >= 6) {
            val idx1 = (deviatedPoints.size / 3).coerceIn(0, deviatedPoints.lastIndex)
            val idx2 = ((deviatedPoints.size * 2) / 3).coerceIn(idx1, deviatedPoints.lastIndex)
            return deviatedPoints[idx1].first to deviatedPoints[idx2].first
        }
        // Single anchor: pick the point of maximum perpendicular deviation from initial route
        val peak = deviatedPoints.maxByOrNull { it.second }?.first ?: deviatedPoints[deviatedPoints.size / 2].first
        return peak to null
    }

    companion object {
        const val TRACK_SAMPLE_INTERVAL_M = 120.0
        const val CORRIDOR_TOLERANCE_M = 150f
        const val MIN_MAX_DEVIATION_M = 250f
        const val MIN_DEVIATED_LENGTH_M = 600.0
        const val TWO_ANCHOR_THRESHOLD_M = 8_000.0
    }
}
