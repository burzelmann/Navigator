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
        val stopped = LocationPoint(latitude = 51.0, longitude = 6.0, bearing = 0f, speedMps = 0f)
        val moving = LocationPoint(latitude = 51.001, longitude = 6.001, bearing = 90f, speedMps = 5f)

        assertEquals(135f, CarNavigationHelper.preserveBearingIfStationary(prev, stopped).bearing, 0.01f)
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
}

