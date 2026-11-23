package com.example.nagivator.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.model.GermanManeuverTranslator
import com.example.nagivator.domain.model.LaneInfo
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RoadSignal
import com.example.nagivator.domain.model.RoadSignalType
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.RouteStep
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Debug-only drive simulator for automated Android Auto DHU video verification.
 * Never included in release builds (`app/src/debug/`).
 */
class DebugDriveSimulatorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scenario = intent.getStringExtra("scenario") ?: "city"
        Log.i(TAG, "Starting debug drive simulation scenario=$scenario")

        simulationJob?.cancel()
        simulationJob = scope.launch {
            when (scenario) {
                "highway" -> runHighwayScenario()
                "stop" -> {
                    NavigationStateHolder.setSimulatedLocationActive(false)
                    NavigationStateHolder.stopNavigation()
                }
                else -> runCityScenario()
            }
        }
    }

    private suspend fun runCityScenario() {
        // Simulated city route in Kalkar / Niederrhein with:
        // 1) Multi-lane left turn at traffic light + compound right turn 95m later
        // 2) Roundabout taking 2nd exit
        val p0 = LocationPoint(51.7375, 6.2920, "Start")
        val p1 = LocationPoint(51.7405, 6.2920, "Kreuzung Ampel")      // ~333m north
        val p2 = LocationPoint(51.7405, 6.2906, "Abzweig Marktplatz")  // ~96m west
        val p3 = LocationPoint(51.7435, 6.2906, "Kreisverkehr")        // ~333m north
        val p4 = LocationPoint(51.7455, 6.2906, "Ziel Bahnhofstraße")  // ~222m north

        val step1Lanes = listOf(
            LaneInfo(listOf("left"), isValid = true),
            LaneInfo(listOf("straight"), isValid = false),
            LaneInfo(listOf("right"), isValid = false)
        )
        val step2Lanes = listOf(
            LaneInfo(listOf("straight"), isValid = false),
            LaneInfo(listOf("right"), isValid = true)
        )

        val steps = listOf(
            RouteStep(
                instruction = GermanManeuverTranslator.translate("depart", "straight", "Grabenstraße"),
                distanceMeters = 333.0,
                durationSeconds = 26.0,
                maneuverType = "depart",
                maneuverModifier = "straight",
                maneuverLocation = p0,
                roadName = "Grabenstraße"
            ),
            RouteStep(
                instruction = GermanManeuverTranslator.translate("turn", "left", "Hauptstr. (L 474)"),
                distanceMeters = 96.0,
                durationSeconds = 9.0,
                maneuverType = "turn",
                maneuverModifier = "left",
                maneuverLocation = p1,
                roadName = "Hauptstr.",
                roadRef = "L 474",
                lanes = step1Lanes
            ),
            RouteStep(
                instruction = GermanManeuverTranslator.translate("turn", "right", "Marktplatz"),
                distanceMeters = 333.0,
                durationSeconds = 26.0,
                maneuverType = "turn",
                maneuverModifier = "right",
                maneuverLocation = p2,
                roadName = "Marktplatz",
                lanes = step2Lanes
            ),
            RouteStep(
                instruction = GermanManeuverTranslator.translate("roundabout", "right", "Bahnhofstr.", roundaboutExit = 2),
                distanceMeters = 222.0,
                durationSeconds = 18.0,
                maneuverType = "roundabout",
                maneuverModifier = "right",
                maneuverLocation = p3,
                roadName = "Bahnhofstr.",
                roundaboutExit = 2
            ),
            RouteStep(
                instruction = "Sie haben Ihr Ziel erreicht",
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                maneuverType = "arrive",
                maneuverModifier = "straight",
                maneuverLocation = p4
            )
        )

        val route = RouteResult(
            waypoints = listOf(p0, p4),
            geometryPoints = densifyPolyline(listOf(p0, p1, p2, p3, p4), stepMeters = 20.0),
            totalDistanceMeters = 984.0,
            totalDurationSeconds = 79.0,
            mode = TravelMode.CAR,
            steps = steps
        )

        val signals = listOf(
            RoadSignal(id = 1001L, type = RoadSignalType.TRAFFIC_SIGNALS, latitude = p1.latitude, longitude = p1.longitude),
            RoadSignal(id = 1002L, type = RoadSignalType.ROUNDABOUT, latitude = p3.latitude, longitude = p3.longitude)
        )

        NavigationStateHolder.setSimulatedLocationActive(true)
        NavigationStateHolder.updateRoadContext(50, signals)
        NavigationStateHolder.updateLocation(p0.copy(bearing = 0f, speedMps = 12.5f), isSimulated = true)
        NavigationStateHolder.startNavigation(route)

        // Drive smoothly along route at ~45 km/h (compressed time: ~12s total)
        drivePolyline(route.geometryPoints, speedMps = 12.5f, tickDelayMs = 240L)
    }

    private suspend fun runHighwayScenario() {
        // Simulated A 57 highway drive at 120 km/h (33.3 m/s) approaching Exit 8 "Krefeld, Duisburg"
        val p0 = LocationPoint(51.5000, 6.5500, "A 57 Start")
        val pExit = LocationPoint(51.4670, 6.5500, "AS 8 Ausfahrt") // ~3,670 m south
        val pEnd = LocationPoint(51.4640, 6.5440, "Richtung Krefeld")

        val highwayLanes = listOf(
            LaneInfo(listOf("straight"), isValid = false),
            LaneInfo(listOf("straight"), isValid = false),
            LaneInfo(listOf("slight right"), isValid = true)
        )

        val steps = listOf(
            RouteStep(
                instruction = GermanManeuverTranslator.translate("depart", "straight", "A 57"),
                distanceMeters = 3670.0,
                durationSeconds = 110.0,
                maneuverType = "depart",
                maneuverModifier = "straight",
                maneuverLocation = p0,
                roadRef = "A 57"
            ),
            RouteStep(
                instruction = GermanManeuverTranslator.translate(
                    type = "off ramp",
                    modifier = "slight right",
                    rawName = "",
                    destinations = "Krefeld;Duisburg",
                    exitRef = "8"
                ),
                distanceMeters = 520.0,
                durationSeconds = 25.0,
                maneuverType = "off ramp",
                maneuverModifier = "slight right",
                maneuverLocation = pExit,
                destinations = "Krefeld;Duisburg",
                exitRef = "8",
                lanes = highwayLanes
            ),
            RouteStep(
                instruction = "Sie haben Ihr Ziel erreicht",
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                maneuverType = "arrive",
                maneuverModifier = "straight",
                maneuverLocation = pEnd
            )
        )

        val route = RouteResult(
            waypoints = listOf(p0, pEnd),
            geometryPoints = densifyPolyline(listOf(p0, pExit, pEnd), stepMeters = 90.0),
            totalDistanceMeters = 4190.0,
            totalDurationSeconds = 135.0,
            mode = TravelMode.CAR,
            steps = steps
        )

        NavigationStateHolder.setSimulatedLocationActive(true)
        NavigationStateHolder.updateRoadContext(120, emptyList())
        NavigationStateHolder.updateLocation(p0.copy(bearing = 180f, speedMps = 33.3f), isSimulated = true)
        NavigationStateHolder.startNavigation(route)

        // Drive smoothly along A57 at 120 km/h (~12s total)
        drivePolyline(route.geometryPoints, speedMps = 33.3f, tickDelayMs = 260L)
    }

    private suspend fun drivePolyline(
        points: List<LocationPoint>,
        speedMps: Float,
        tickDelayMs: Long
    ) {
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val bearing = bearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude)
            NavigationStateHolder.updateLocation(
                a.copy(bearing = bearing, speedMps = speedMps),
                isSimulated = true
            )
            delay(tickDelayMs)
        }
        points.lastOrNull()?.let { end ->
            NavigationStateHolder.updateLocation(
                end.copy(speedMps = 0f),
                isSimulated = true
            )
        }
    }

    private fun densifyPolyline(points: List<LocationPoint>, stepMeters: Double): List<LocationPoint> {
        if (points.size < 2) return points
        val out = mutableListOf<LocationPoint>()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val dist = CarNavigationHelper.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            val segments = (dist / stepMeters).toInt().coerceAtLeast(1)
            for (s in 0 until segments) {
                val f = s.toDouble() / segments
                out.add(
                    LocationPoint(
                        latitude = a.latitude + (b.latitude - a.latitude) * f,
                        longitude = a.longitude + (b.longitude - a.longitude) * f
                    )
                )
            }
        }
        out.add(points.last())
        return out
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg + 360.0) % 360.0).toFloat()
    }

    companion object {
        private const val TAG = "DebugDriveSim"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var simulationJob: Job? = null
    }
}
