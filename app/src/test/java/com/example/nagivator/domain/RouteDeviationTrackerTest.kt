package com.example.nagivator.domain

import com.example.nagivator.data.local.entity.LearnedRouteEntity
import com.example.nagivator.data.repository.LearnedRouteRepository
import com.example.nagivator.data.repository.RoutingRepository
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDeviationTrackerTest {

    private fun buildStraightNorthRoute(mode: TravelMode = TravelMode.CAR, learnedId: Long? = null): RouteResult {
        val pts = (0..20).map { i ->
            LocationPoint(latitude = 51.7000 + i * 0.0020, longitude = 6.3000, name = if (i == 0) "Start" else if (i == 20) "Ziel" else "")
        }
        return RouteResult(
            waypoints = listOf(pts.first(), pts.last()),
            geometryPoints = pts,
            totalDistanceMeters = 4400.0,
            totalDurationSeconds = 300.0,
            mode = mode,
            learnedRouteId = learnedId,
            isLearnedPreference = learnedId != null
        )
    }

    @Test
    fun ignoresOnRouteDriveAndFootModeAndClosureReroutes() {
        val carRoute = buildStraightNorthRoute(TravelMode.CAR)

        // 1. Driving faithfully along the initial route -> no deviation
        val onRouteTracker = RouteDeviationTracker()
        onRouteTracker.startTrip(carRoute)
        carRoute.geometryPoints.forEach { onRouteTracker.recordLocation(it) }
        assertNull(onRouteTracker.evaluateAtArrival())

        // 2. Foot mode is strictly ignored
        val footRoute = buildStraightNorthRoute(TravelMode.FOOT)
        val footTracker = RouteDeviationTracker()
        footTracker.startTrip(footRoute)
        (0..20).forEach { i ->
            val lonOffset = if (i in 4..16) 0.0080 else 0.0
            footTracker.recordLocation(LocationPoint(51.7000 + i * 0.0020, 6.3000 + lonOffset))
        }
        assertNull(footTracker.evaluateAtArrival())

        // 3. Forced Autobahn closure reroute must NOT be learned as user preference
        val closureTracker = RouteDeviationTracker()
        closureTracker.startTrip(carRoute)
        closureTracker.markForcedClosureReroute()
        (0..20).forEach { i ->
            val lonOffset = if (i in 4..16) 0.0080 else 0.0
            closureTracker.recordLocation(LocationPoint(51.7000 + i * 0.0020, 6.3000 + lonOffset))
        }
        assertNull(closureTracker.evaluateAtArrival())
    }

    @Test
    fun detectsIntentionalCarDeviationAndExtractsPeakAnchor() {
        val carRoute = buildStraightNorthRoute(TravelMode.CAR)
        val tracker = RouteDeviationTracker()
        tracker.startTrip(carRoute)

        // Drive ~550m east of the suggested route for ~2.4 km, then rejoin toward destination
        (0..20).forEach { i ->
            val lonOffset = when (i) {
                in 4..15 -> 0.0080 + if (i == 10) 0.0020 else 0.0
                else -> 0.0
            }
            tracker.recordLocation(LocationPoint(51.7000 + i * 0.0020, 6.3000 + lonOffset))
        }

        val outcome = tracker.evaluateAtArrival()
        assertNotNull(outcome)
        assertNotNull(outcome!!.anchor1)
        assertTrue("Expected anchor east of initial route", outcome.anchor1!!.longitude > 6.3060)
        assertNull(outcome.demoteLearnedRouteId)
    }

    @Test
    fun demotesLearnedRouteWhenDriverIntentionallyLeavesIt() {
        val learnedRoute = buildStraightNorthRoute(TravelMode.CAR, learnedId = 42L)
        val tracker = RouteDeviationTracker()
        tracker.startTrip(learnedRoute)

        (0..20).forEach { i ->
            val lonOffset = if (i in 4..15) -0.0080 else 0.0
            tracker.recordLocation(LocationPoint(51.7000 + i * 0.0020, 6.3000 + lonOffset))
        }

        val outcome = tracker.evaluateAtArrival()
        assertNotNull(outcome)
        assertEquals(42L, outcome!!.demoteLearnedRouteId)
    }

    @Test
    fun activePreferenceRequiresThreeConfirmationsAndExactOriginDestDirection() {
        val origin = LocationPoint(51.7732, 6.2923, "Kalkar")
        val dest = LocationPoint(51.4661, 6.4676, "Rheurdt")

        val notYetConfirmed = LearnedRouteEntity(
            id = 1L,
            startName = "Kalkar",
            startLatitude = origin.latitude,
            startLongitude = origin.longitude,
            destinationName = "Rheurdt",
            destinationLatitude = dest.latitude,
            destinationLongitude = dest.longitude,
            travelMode = TravelMode.CAR.name,
            anchorLatitude = 51.60,
            anchorLongitude = 6.40,
            confirmationCount = 2, // below threshold of 3!
            lastUsedMs = 1000L
        )
        assertNull(LearnedRouteRepository.matchActivePreference(listOf(notYetConfirmed), origin, dest))

        val confirmed = notYetConfirmed.copy(id = 2L, confirmationCount = 3)
        // Exact direction matches
        assertEquals(2L, LearnedRouteRepository.matchActivePreference(listOf(confirmed), origin, dest)?.id)
        // Reverse direction must NOT match (directional!)
        assertNull(LearnedRouteRepository.matchActivePreference(listOf(confirmed), dest, origin))
        // Different destination must NOT match
        val otherDest = LocationPoint(51.6195, 6.1990, "Weeze")
        assertNull(LearnedRouteRepository.matchActivePreference(listOf(confirmed), origin, otherDest))
    }

    @Test
    fun plausibilityGuardRejectsExcessiveDetours() {
        assertTrue(RoutingRepository.isPlausibleLearnedRoute(12_000.0, 10_000.0))
        assertTrue(RoutingRepository.isPlausibleLearnedRoute(14_500.0, 10_000.0))
        assertFalse(RoutingRepository.isPlausibleLearnedRoute(15_000.0, 10_000.0))
    }
}
