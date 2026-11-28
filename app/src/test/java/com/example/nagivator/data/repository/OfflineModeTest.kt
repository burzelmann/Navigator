package com.example.nagivator.data.repository

import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.RouteStep
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModeTest {

    @After
    fun tearDown() {
        UserSettingsRepository.isOfflineModeEnabled = false
    }

    @Test
    fun blocksAllOnlineRequestsImmediatelyWhenOfflineModeEnabled() = runBlocking {
        UserSettingsRepository.isOfflineModeEnabled = true

        val routingRepo = RoutingRepository()
        val trafficRepo = TrafficRepository()
        val roadContextRepo = RoadContextRepository()

        // 1. Online search must fail immediately so local offline search fallback takes over
        val searchRes = routingRepo.searchPlaces("Kalkar")
        assertTrue(searchRes.isFailure)

        // 2. Reverse geocode returns local coordinate label immediately
        val revRes = routingRepo.reverseGeocode(51.7375, 6.2920).getOrThrow()
        assertEquals("Offline-Modus", revRes.address)

        // 3. Traffic repository makes zero HTTP calls and returns empty list
        val sampleRoute = RouteResult(
            waypoints = listOf(LocationPoint(51.7375, 6.2920), LocationPoint(51.4661, 6.4675)),
            geometryPoints = listOf(LocationPoint(51.7375, 6.2920), LocationPoint(51.4661, 6.4675)),
            totalDistanceMeters = 35_000.0,
            totalDurationSeconds = 2_400.0,
            mode = TravelMode.CAR,
            steps = listOf(
                RouteStep(
                    instruction = "Weiter auf A57",
                    distanceMeters = 35_000.0,
                    durationSeconds = 2_400.0,
                    maneuverType = "continue",
                    maneuverModifier = "straight"
                )
            )
        )
        val incidents = trafficRepo.fetchIncidentsOnRoute(sampleRoute)
        assertTrue(incidents.isEmpty())

        // 4. RoadContext repository makes zero OSM HTTP calls
        val roadCtx = roadContextRepo.fetchAround(51.7375, 6.2920, forceRefresh = true)
        assertNull(roadCtx)
    }
}
