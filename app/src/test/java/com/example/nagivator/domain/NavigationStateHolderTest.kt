package com.example.nagivator.domain

import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationStateHolderTest {

    @Test
    fun startAndStopNavigation_updatesStateCorrectly() {
        val dummyRoute = RouteResult(
            waypoints = listOf(LocationPoint(52.52, 13.40), LocationPoint(52.53, 13.41)),
            geometryPoints = listOf(LocationPoint(52.52, 13.40), LocationPoint(52.53, 13.41)),
            totalDistanceMeters = 1500.0,
            totalDurationSeconds = 120.0,
            mode = TravelMode.CAR
        )

        NavigationStateHolder.startNavigation(dummyRoute)
        assertTrue(NavigationStateHolder.isNavigating.value)
        assertEquals(dummyRoute, NavigationStateHolder.activeRoute.value)
        assertEquals(0, NavigationStateHolder.currentStepIndex.value)

        NavigationStateHolder.updateStepIndex(1)
        assertEquals(1, NavigationStateHolder.currentStepIndex.value)

        NavigationStateHolder.stopNavigation()
        assertFalse(NavigationStateHolder.isNavigating.value)
        assertNull(NavigationStateHolder.activeRoute.value)
        assertEquals(0, NavigationStateHolder.currentStepIndex.value)
    }

    @Test
    fun updateLocation_updatesCurrentLocation() {
        val point = LocationPoint(52.52, 13.40, name = "Berlin", bearing = 90f, speedMps = 15f)
        NavigationStateHolder.updateLocation(point)
        assertEquals(point, NavigationStateHolder.currentLocation.value)
    }

    @Test
    fun toggleMute_togglesMuteState() {
        val initial = NavigationStateHolder.isMuted.value
        NavigationStateHolder.toggleMute()
        assertEquals(!initial, NavigationStateHolder.isMuted.value)
        NavigationStateHolder.toggleMute()
        assertEquals(initial, NavigationStateHolder.isMuted.value)
    }
}
