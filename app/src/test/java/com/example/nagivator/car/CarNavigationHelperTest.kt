package com.example.nagivator.car

import androidx.car.app.model.Distance
import com.example.nagivator.data.repository.AppThemeMode
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.RouteStep
import com.example.nagivator.domain.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarNavigationHelperTest {

    @Test
    fun interpolateAngle_handlesWraparoundShortestPath() {
        assertEquals(0f, CarNavigationHelper.interpolateAngle(350f, 10f, 0.5f), 0.01f)
        assertEquals(0f, CarNavigationHelper.interpolateAngle(10f, 350f, 0.5f), 0.01f)
        assertEquals(90f, CarNavigationHelper.interpolateAngle(0f, 180f, 0.5f), 0.01f)
    }

    @Test
    fun interpolateLocation_interpolatesLatLonAndBearing() {
        val from = LocationPoint(latitude = 51.0, longitude = 6.0, bearing = 350f)
        val to = LocationPoint(latitude = 51.2, longitude = 6.4, bearing = 10f)

        val mid = CarNavigationHelper.interpolateLocation(from, to, 0.5f)
        assertEquals(51.1, mid.latitude, 0.0001)
        assertEquals(6.2, mid.longitude, 0.0001)
        assertEquals(0f, mid.bearing, 0.01f)
    }

    @Test
    fun preserveBearingIfStationary_keepsLastHeadingAtRedLight() {
        val prev = LocationPoint(latitude = 51.0, longitude = 6.0, bearing = 135f, speedMps = 10f)
        val stoppedZero = LocationPoint(latitude = 51.0, longitude = 6.0, bearing = 0f, speedMps = 0f)
        val stoppedDrift90 = LocationPoint(latitude = 51.0, longitude = 6.0, bearing = 225f, speedMps = 0.4f)
        val creepingDrift = LocationPoint(latitude = 51.0, longitude = 6.0, bearing = 45f, speedMps = 1.5f)
        val poorAccuracy = LocationPoint(
            latitude = 51.001,
            longitude = 6.001,
            bearing = 310f,
            speedMps = 6f,
            bearingAccuracyDegrees = 65f
        )
        val moving = LocationPoint(
            latitude = 51.001,
            longitude = 6.001,
            bearing = 90f,
            speedMps = 5f,
            bearingAccuracyDegrees = 10f
        )

        assertEquals(135f, CarNavigationHelper.preserveBearingIfStationary(prev, stoppedZero).bearing, 0.01f)
        assertEquals(135f, CarNavigationHelper.preserveBearingIfStationary(prev, stoppedDrift90).bearing, 0.01f)
        assertEquals(135f, CarNavigationHelper.preserveBearingIfStationary(prev, creepingDrift).bearing, 0.01f)
        assertEquals(135f, CarNavigationHelper.preserveBearingIfStationary(prev, poorAccuracy).bearing, 0.01f)
        assertEquals(90f, CarNavigationHelper.preserveBearingIfStationary(prev, moving).bearing, 0.01f)
    }

    @Test
    fun initialStepIndex_skipsDepartWhenTurnsFollow() {
        val stepsWithTurn = listOf(
            RouteStep("Losfahren", 200.0, 20.0, maneuverType = "depart"),
            RouteStep("Rechts abbiegen", 500.0, 40.0, maneuverType = "turn")
        )
        assertEquals(1, CarNavigationHelper.initialStepIndex(stepsWithTurn))

        val singleStep = listOf(
            RouteStep("Direkt", 200.0, 20.0, maneuverType = "depart")
        )
        assertEquals(0, CarNavigationHelper.initialStepIndex(singleStep))
    }

    @Test
    fun createCarDistance_usesMetersBelow1KmAndKilometersAbove() {
        val shortDist = CarNavigationHelper.createCarDistance(244.0)
        assertEquals(Distance.UNIT_METERS, shortDist.displayUnit)
        assertEquals(240.0, shortDist.displayDistance, 0.01)

        val longDist = CarNavigationHelper.createCarDistance(2450.0)
        assertEquals(Distance.UNIT_KILOMETERS_P1, longDist.displayUnit)
        assertEquals(2.45, longDist.displayDistance, 0.01)
    }

    @Test
    fun remainingPolylineAndEta_scaleProportionally() {
        val p1 = LocationPoint(51.0, 6.0)
        val p2 = LocationPoint(51.01, 6.0)
        val p3 = LocationPoint(51.02, 6.0)

        val fullDist = CarNavigationHelper.remainingPolylineMeters(null, listOf(p1, p2, p3))
        val halfDist = CarNavigationHelper.remainingPolylineMeters(null, listOf(p2, p3))
        assertTrue(fullDist > 2000.0)
        assertEquals(fullDist / 2.0, halfDist, 5.0)

        val route = RouteResult(
            waypoints = listOf(p1, p3),
            geometryPoints = listOf(p2, p3),
            totalDistanceMeters = fullDist,
            totalDurationSeconds = 120.0,
            mode = TravelMode.CAR
        )
        val remainingSec = CarNavigationHelper.estimateRemainingSeconds(route, halfDist)
        assertEquals(60.0, remainingSec, 1.0)
    }

    @Test
    fun formatSearchSubtitle_includesDistanceWhenGpsAvailable() {
        val current = LocationPoint(51.466, 6.45, name = "Start")
        val dest = LocationPoint(51.466, 6.45, name = "Aldi", address = "Rheurdt, Deutschland")
        val subtitleWithGps = CarNavigationHelper.formatSearchSubtitle(current, dest)
        assertEquals("0 m • Rheurdt, Deutschland", subtitleWithGps)

        val subtitleWithoutGps = CarNavigationHelper.formatSearchSubtitle(null, dest)
        assertEquals("Rheurdt, Deutschland", subtitleWithoutGps)
    }

    @Test
    fun nextThemeMode_cyclesSystemLightDark() {
        assertEquals(AppThemeMode.LIGHT, CarNavigationHelper.nextThemeMode(AppThemeMode.SYSTEM))
        assertEquals(AppThemeMode.DARK, CarNavigationHelper.nextThemeMode(AppThemeMode.LIGHT))
        assertEquals(AppThemeMode.SYSTEM, CarNavigationHelper.nextThemeMode(AppThemeMode.DARK))
    }

    @Test
    fun distanceToPolyline_midpointOnLongStraightRoad_isNearZero() {
        // Two vertices ~1.1 km apart along a straight north-south rural road
        val p1 = LocationPoint(51.7000, 6.3000)
        val p2 = LocationPoint(51.7100, 6.3000)
        val midpoint = LocationPoint(51.7050, 6.3000)

        val (dist, idx) = CarNavigationHelper.distanceToPolyline(midpoint, listOf(p1, p2))
        assertEquals(0, idx)
        assertTrue("Expected < 2m on segment midpoint, got $dist", dist < 2f)
    }

    @Test
    fun distanceToPolyline_offRoutePoint_returnsPerpendicularDistance() {
        val p1 = LocationPoint(51.7000, 6.3000)
        val p2 = LocationPoint(51.7100, 6.3000)
        // ~0.0015 deg lon at 51.7 deg lat is ~103 m east of the road
        val offRoute = LocationPoint(51.7050, 6.3015)

        val (dist, _) = CarNavigationHelper.distanceToPolyline(offRoute, listOf(p1, p2))
        assertTrue("Expected ~103m off-route, got $dist", dist in 90f..120f)
    }

    @Test
    fun targetZoomForSpeed_appliesSpeedTiersAndHysteresis() {
        assertEquals(17, CarNavigationHelper.targetZoomForSpeed(5.0f, 16)) // 18 km/h -> 17
        assertEquals(16, CarNavigationHelper.targetZoomForSpeed(15.0f, 17)) // 54 km/h -> 16
        assertEquals(15, CarNavigationHelper.targetZoomForSpeed(28.0f, 16)) // 100.8 km/h -> 15
        // Deadband at 35 km/h (~9.7 m/s) preserves currentZoom
        assertEquals(17, CarNavigationHelper.targetZoomForSpeed(9.7f, 17))
        assertEquals(16, CarNavigationHelper.targetZoomForSpeed(9.7f, 16))
    }

    @Test
    fun nextMapColorScheme_cyclesAllSchemes() {
        val first = com.example.nagivator.data.repository.MapColorScheme.GRAPHITE
        val second = CarNavigationHelper.nextMapColorScheme(first)
        val third = CarNavigationHelper.nextMapColorScheme(second)
        val fourth = CarNavigationHelper.nextMapColorScheme(third)
        val back = CarNavigationHelper.nextMapColorScheme(fourth)
        assertEquals(com.example.nagivator.data.repository.MapColorScheme.MIDNIGHT_BLUE, second)
        assertEquals(com.example.nagivator.data.repository.MapColorScheme.WARM_AMBER, third)
        assertEquals(com.example.nagivator.data.repository.MapColorScheme.HIGH_CONTRAST, fourth)
        assertEquals(first, back)
    }

    @Test
    fun targetZoomForNavigation_zoomsInBeforeRoundaboutsJunctionsAndTurns() {
        // Highway speed (108 km/h = 30 m/s) -> base zoom 15
        assertEquals(15, CarNavigationHelper.targetZoomForNavigation(30f, 15, "turn", 900.0))
        // Complex junction (fork / off ramp / roundabout) zooms in early: 16 at 450m, 17 at 200m, 18 at 100m
        assertEquals(16, CarNavigationHelper.targetZoomForNavigation(30f, 15, "fork", 450.0))
        assertEquals(17, CarNavigationHelper.targetZoomForNavigation(30f, 16, "off ramp", 200.0))
        assertEquals(18, CarNavigationHelper.targetZoomForNavigation(16f, 17, "roundabout", 90.0))
        // Regular turn within 220m zooms to at least 17 even at rural speed (60 km/h)
        assertEquals(17, CarNavigationHelper.targetZoomForNavigation(16.6f, 16, "turn", 180.0))
    }
}

