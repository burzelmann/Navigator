package com.example.nagivator.car

import android.util.Log
import com.example.nagivator.data.repository.RoutingRepository
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Debug-only route drive simulator for testing Android Auto navigation and 30-FPS interpolation on DHU.
 */
object DebugDriveSimulator {

    private const val TAG = "DebugDriveSimulator"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var simJob: Job? = null

    fun start(speedMps: Float = 13.9f) {
        stop()
        NavigationStateHolder.setSimulatedLocationActive(true)

        simJob = scope.launch {
            var route = NavigationStateHolder.activeRoute.value
            if (route == null || route.geometryPoints.size < 2) {
                val start = NavigationStateHolder.currentLocation.value
                    ?: LocationPoint(51.4660, 6.4530, "Rheurdt Start")
                val dest = LocationPoint(
                    start.latitude + 0.025,
                    start.longitude + 0.035,
                    "Simuliertes Ziel"
                )
                val fetched = RoutingRepository().getRoute(listOf(start, dest), TravelMode.CAR)
                route = fetched.getOrNull()
                if (route != null) {
                    NavigationStateHolder.startNavigation(route)
                }
            }

            val points = route?.geometryPoints.orEmpty()
            if (points.size < 2) {
                Log.w(TAG, "No route geometry available for simulation.")
                stop()
                return@launch
            }

            val samples = buildSamples(points, stepMeters = speedMps.toDouble().coerceAtLeast(5.0))
            Log.i(TAG, "Starting drive simulation with ${samples.size} 1s steps at ${speedMps} m/s")

            for (sample in samples) {
                if (!isActive) break
                NavigationStateHolder.updateLocation(
                    sample.copy(speedMps = speedMps),
                    isSimulated = true
                )
                delay(1000L)
            }

            Log.i(TAG, "Drive simulation finished.")
            stop()
        }
    }

    fun simulateOffRoute(offsetDegLon: Double = 0.0025) {
        stop()
        NavigationStateHolder.setSimulatedLocationActive(true)
        simJob = scope.launch {
            val base = NavigationStateHolder.currentLocation.value
                ?: NavigationStateHolder.activeRoute.value?.geometryPoints?.firstOrNull()
                ?: return@launch
            Log.i(TAG, "Simulating 4 off-route GPS updates (~170m offset)")
            repeat(4) { step ->
                if (!isActive) return@launch
                val p = LocationPoint(
                    latitude = base.latitude + step * 0.0001,
                    longitude = base.longitude + offsetDegLon,
                    name = "Off-Route Simulation",
                    bearing = 90f,
                    speedMps = 13.9f
                )
                NavigationStateHolder.updateLocation(p, isSimulated = true)
                delay(400L)
            }
        }
    }

    fun teleport(lat: Double, lon: Double, speedMps: Float = 11.1f) {
        stop()
        NavigationStateHolder.setSimulatedLocationActive(true)
        NavigationStateHolder.updateLocation(
            LocationPoint(
                latitude = lat,
                longitude = lon,
                name = "Debug Teleport",
                bearing = 0f,
                speedMps = speedMps
            ),
            isSimulated = true
        )
    }

    fun stop() {
        simJob?.cancel()
        simJob = null
        NavigationStateHolder.setSimulatedLocationActive(false)
    }

    private fun buildSamples(points: List<LocationPoint>, stepMeters: Double): List<LocationPoint> {
        val result = mutableListOf<LocationPoint>()
        var accumulated = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val segDist = CarNavigationHelper.haversineMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            if (segDist <= 0.5) continue
            val bearing = computeBearing(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

            if (segDist >= stepMeters) {
                val steps = (segDist / stepMeters).toInt().coerceAtLeast(1)
                for (s in 0 until steps) {
                    val f = s.toDouble() / steps
                    result.add(
                        LocationPoint(
                            latitude = p1.latitude + (p2.latitude - p1.latitude) * f,
                            longitude = p1.longitude + (p2.longitude - p1.longitude) * f,
                            name = "Simulierter Standort",
                            bearing = bearing
                        )
                    )
                }
                accumulated = 0.0
            } else {
                accumulated += segDist
                if (accumulated >= stepMeters || i == 0) {
                    result.add(p2.copy(name = "Simulierter Standort", bearing = bearing))
                    accumulated = 0.0
                }
            }
        }
        result.add(points.last())
        return result
    }

    private fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }
}
