package com.example.nagivator.domain

import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.RouteStep
import com.example.nagivator.domain.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingResilienceTest {

    private val start = LocationPoint(51.7732, 6.2924, name = "Zuhause")
    private val dest = LocationPoint(51.7925, 6.1420, name = "Kleve Markt")

    @Test
    fun `offline fallback route is clearly flagged and points maneuver to destination`() {
        val fallback = RouteResult(
            waypoints = listOf(start, dest),
            geometryPoints = listOf(start, dest),
            totalDistanceMeters = 10_600.0,
            totalDurationSeconds = 883.0,
            mode = TravelMode.CAR,
            steps = listOf(
                RouteStep(
                    instruction = "Offline: Luftlinie zum Ziel (kein Netz)",
                    distanceMeters = 10_600.0,
                    durationSeconds = 883.0,
                    maneuverType = "straight",
                    maneuverModifier = "straight",
                    maneuverLocation = dest
                )
            ),
            isOfflineFallback = true
        )

        assertTrue(fallback.isOfflineFallback)
        assertEquals(dest, fallback.steps.first().maneuverLocation)
        assertEquals(0, CarNavigationHelper.initialStepIndex(fallback.steps))
        assertTrue(CarNavigationHelper.remainingPolylineMeters(start, fallback.geometryPoints) > 10_000.0)
    }

    @Test
    fun `standard online route defaults isOfflineFallback to false`() {
        val online = RouteResult(
            waypoints = listOf(start, dest),
            geometryPoints = listOf(start, dest),
            totalDistanceMeters = 14_000.0,
            totalDurationSeconds = 900.0,
            mode = TravelMode.CAR,
            steps = listOf(
                RouteStep("Losfahren", 200.0, 20.0, "depart", "straight", start),
                RouteStep("Rechts abbiegen", 13_800.0, 880.0, "turn", "right", dest)
            )
        )

        assertFalse(online.isOfflineFallback)
        assertEquals(1, CarNavigationHelper.initialStepIndex(online.steps))
    }
}
