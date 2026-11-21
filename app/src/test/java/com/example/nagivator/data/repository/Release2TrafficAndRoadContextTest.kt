package com.example.nagivator.data.repository

import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.RouteStep
import com.example.nagivator.domain.model.TrafficIncident
import com.example.nagivator.domain.model.TrafficIncidentType
import com.example.nagivator.domain.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Release2TrafficAndRoadContextTest {

    @Test
    fun parseMaxSpeedKmh_handlesGermanAndNumericValues() {
        assertEquals(50, RoadContextRepository.parseMaxSpeedKmh("50"))
        assertEquals(100, RoadContextRepository.parseMaxSpeedKmh("100"))
        assertEquals(50, RoadContextRepository.parseMaxSpeedKmh("DE:urban"))
        assertEquals(100, RoadContextRepository.parseMaxSpeedKmh("DE:rural"))
        assertEquals(30, RoadContextRepository.parseMaxSpeedKmh("DE:zone:30"))
        assertEquals(7, RoadContextRepository.parseMaxSpeedKmh("walk"))
        assertEquals(RoadContextRepository.SPEED_LIMIT_UNLIMITED, RoadContextRepository.parseMaxSpeedKmh("none"))
        assertEquals(80, RoadContextRepository.parseMaxSpeedKmh("50 mph"))
        assertNull(RoadContextRepository.parseMaxSpeedKmh("signals"))
    }

    @Test
    fun extractAutobahnIds_findsOnlyGermanAutobahns() {
        val route = RouteResult(
            waypoints = emptyList(),
            geometryPoints = emptyList(),
            totalDistanceMeters = 50_000.0,
            totalDurationSeconds = 2000.0,
            mode = TravelMode.CAR,
            steps = listOf(
                RouteStep("Links abbiegen auf L 18", 500.0, 40.0, "turn", "left"),
                RouteStep("Rechts auffahren auf A 57", 12000.0, 400.0, "on ramp", "right"),
                RouteStep("Weiter auf A3 Richtung Köln", 20000.0, 600.0, "merge", "left"),
                RouteStep("Ausfahrt auf B 57 nehmen", 500.0, 30.0, "off ramp", "right")
            )
        )
        assertEquals(listOf("A57", "A3"), TrafficRepository.extractAutobahnIds(route))
    }

    @Test
    fun filterIncidentsOnRoute_matchesNearbyAndIgnoresDistant() {
        val route = RouteResult(
            waypoints = listOf(LocationPoint(51.7000, 6.3000), LocationPoint(51.7200, 6.3000)),
            geometryPoints = listOf(LocationPoint(51.7000, 6.3000), LocationPoint(51.7200, 6.3000)),
            totalDistanceMeters = 2200.0,
            totalDurationSeconds = 120.0,
            mode = TravelMode.CAR,
            steps = emptyList()
        )
        val onRoute = TrafficIncident("1", "A57", TrafficIncidentType.CLOSURE, "Vollsperrung", latitude = 51.7100, longitude = 6.3002, isBlocked = true)
        val farAway = TrafficIncident("2", "A57", TrafficIncidentType.WARNING, "Stau", latitude = 51.7100, longitude = 6.3200, isBlocked = false)

        val matched = TrafficRepository.filterIncidentsOnRoute(listOf(onRoute, farAway), route)
        assertEquals(1, matched.size)
        assertEquals("1", matched.first().id)
    }

    @Test
    fun selectRouteAvoidingPoint_picksCleanAlternative() {
        val blocked = LocationPoint(51.7100, 6.3000)
        val blockedRoute = RouteResult(
            waypoints = emptyList(),
            geometryPoints = listOf(LocationPoint(51.7000, 6.3000), LocationPoint(51.7200, 6.3000)),
            totalDistanceMeters = 2200.0,
            totalDurationSeconds = 100.0,
            mode = TravelMode.CAR,
            steps = emptyList()
        )
        val detourRoute = RouteResult(
            waypoints = emptyList(),
            geometryPoints = listOf(LocationPoint(51.7000, 6.3100), LocationPoint(51.7200, 6.3100)),
            totalDistanceMeters = 2800.0,
            totalDurationSeconds = 140.0,
            mode = TravelMode.CAR,
            steps = emptyList()
        )

        val chosen = RoutingRepository.selectRouteAvoidingPoint(listOf(blockedRoute, detourRoute), blocked)
        assertNotNull(chosen)
        assertEquals(2800.0, chosen!!.totalDistanceMeters, 0.1)
    }
}
