package com.example.nagivator.domain

import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.TravelMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffRouteDetectorTest {

    // Straight northbound route along lon=6.2900 from lat=51.7400 to 51.7500 (bearing ~0°)
    private val northRoute = listOf(
        LocationPoint(51.7400, 6.2900),
        LocationPoint(51.7500, 6.2900)
    )

    @Test
    fun ignoresSingleGpsJitterSpike() {
        val detector = OffRouteDetector()
        // ~48m east (1 fix spike)
        val spike = LocationPoint(51.7450, 6.2907, bearing = 0f, speedMps = 12f)
        val r1 = detector.evaluate(spike, northRoute, TravelMode.CAR, nowMs = 10_000L)
        assertFalse(r1.shouldReroute)
        assertTrue(r1.isOffRoute)

        // Next fix back on route -> resets strikes
        val backOn = LocationPoint(51.7455, 6.2900, bearing = 0f, speedMps = 12f)
        val r2 = detector.evaluate(backOn, northRoute, TravelMode.CAR, nowMs = 11_000L)
        assertFalse(r2.shouldReroute)
        assertFalse(r2.isOffRoute)
    }

    @Test
    fun triggersFastAfterTwoFixesOnHeadingDivergenceAtJunction() {
        val detector = OffRouteDetector()
        // ~28m east, heading East (90° off northbound route) at 36 km/h (10 m/s)
        val wrongTurn1 = LocationPoint(51.7450, 6.29041, bearing = 90f, speedMps = 10f)
        val wrongTurn2 = LocationPoint(51.7450, 6.29055, bearing = 90f, speedMps = 10f)

        val r1 = detector.evaluate(wrongTurn1, northRoute, TravelMode.CAR, nowMs = 10_000L)
        assertFalse(r1.shouldReroute)

        val r2 = detector.evaluate(wrongTurn2, northRoute, TravelMode.CAR, nowMs = 11_000L)
        assertTrue(r2.shouldReroute)
    }

    @Test
    fun triggersImmediatelyOnSevereDepartureAndRespectsCooldown() {
        val detector = OffRouteDetector(cooldownMs = 4_000L)
        // ~95m east (> 75m immediate threshold)
        val farOff = LocationPoint(51.7450, 6.2914, bearing = 45f, speedMps = 14f)

        val r1 = detector.evaluate(farOff, northRoute, TravelMode.CAR, nowMs = 10_000L)
        assertTrue(r1.shouldReroute)
        detector.markRecalculateStarted(10_000L)

        // Within 4s cooldown -> blocked
        val r2 = detector.evaluate(farOff, northRoute, TravelMode.CAR, nowMs = 12_500L)
        assertFalse(r2.shouldReroute)

        // After 4s cooldown -> allowed again
        val r3 = detector.evaluate(farOff, northRoute, TravelMode.CAR, nowMs = 14_500L)
        assertTrue(r3.shouldReroute)
    }
}
