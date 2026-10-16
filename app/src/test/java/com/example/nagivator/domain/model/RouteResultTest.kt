package com.example.nagivator.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteResultTest {

    @Test
    fun formattedDistance_formatsMetersAndKilometersCorrectly() {
        val shortRoute = RouteResult(
            waypoints = emptyList(),
            geometryPoints = emptyList(),
            totalDistanceMeters = 850.0,
            totalDurationSeconds = 600.0,
            mode = TravelMode.FOOT
        )
        assertEquals("850 m", shortRoute.formattedDistance)

        val longRoute = RouteResult(
            waypoints = emptyList(),
            geometryPoints = emptyList(),
            totalDistanceMeters = 2450.0,
            totalDurationSeconds = 1800.0,
            mode = TravelMode.FOOT
        )
        val formattedDist = longRoute.formattedDistance
        assertTrue("Formatted distance should contain '2' and '5 km'", formattedDist == "2.5 km" || formattedDist == "2,5 km")
    }

    @Test
    fun formattedDuration_formatsMinutesAndHoursCorrectly() {
        val minutesRoute = RouteResult(
            waypoints = emptyList(),
            geometryPoints = emptyList(),
            totalDistanceMeters = 1000.0,
            totalDurationSeconds = 1200.0, // 20 minutes
            mode = TravelMode.FOOT
        )
        assertEquals("20 Min.", minutesRoute.formattedDuration)

        val hoursRoute = RouteResult(
            waypoints = emptyList(),
            geometryPoints = emptyList(),
            totalDistanceMeters = 10000.0,
            totalDurationSeconds = 4500.0, // 1 Std. 15 Min.
            mode = TravelMode.FOOT
        )
        assertEquals("1 Std. 15 Min.", hoursRoute.formattedDuration)
    }

    @Test
    fun formattedElevation_formatsAscentAndDescentCorrectly() {
        val elevationRoute = RouteResult(
            waypoints = emptyList(),
            geometryPoints = emptyList(),
            totalDistanceMeters = 3000.0,
            totalDurationSeconds = 2400.0,
            mode = TravelMode.FOOT,
            totalAscentMeters = 120.4,
            totalDescentMeters = 85.1
        )
        assertEquals("↗ 120m  ↘ 85m", elevationRoute.formattedElevation)
    }
}
