package com.example.nagivator.data.repository

import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRoutingTest {

    @Test
    fun computesCorrect5x5SegmentFileNamesWorldwide() {
        // Kalkar / Niederrhein (51.7732, 6.2923) -> E5_N50.rd5
        assertEquals("E5_N50.rd5", OfflineRoutingRepository.segmentNameFor(51.7732, 6.2923))
        // Berlin (52.52, 13.405) -> E10_N50.rd5
        assertEquals("E10_N50.rd5", OfflineRoutingRepository.segmentNameFor(52.52, 13.405))
        // London (51.507, -0.127) -> W5_N50.rd5
        assertEquals("W5_N50.rd5", OfflineRoutingRepository.segmentNameFor(51.507, -0.127))
        // Southern hemisphere (-33.86, 151.20) -> E150_S35.rd5
        assertEquals("E150_S35.rd5", OfflineRoutingRepository.segmentNameFor(-33.86, 151.20))
    }

    @Test
    fun computesSegmentsForBoundingBoxAcrossTileBoundary() {
        val segments = OfflineRoutingRepository.segmentsForBbox(
            minLat = 49.5,
            maxLat = 51.0,
            minLon = 4.5,
            maxLon = 6.5
        )
        assertEquals(
            setOf("E0_N45.rd5", "E5_N45.rd5", "E0_N50.rd5", "E5_N50.rd5"),
            segments.toSet()
        )
    }

    @Test
    fun parsesBRouterGeoJsonWithVoiceHintsAndRoundaboutExit() {
        val sampleGeoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "track-length": "1250",
                    "total-time": "110",
                    "voicehints": [
                      [1, 5, 0, 420.0, 88],
                      [2, 13, 2, 580.0, 45],
                      [3, 2, 0, 250.0, -90]
                    ]
                  },
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [6.2920, 51.7730, 20.0],
                      [6.2940, 51.7730, 21.0],
                      [6.2940, 51.7760, 22.0],
                      [6.2980, 51.7760, 20.0],
                      [6.2980, 51.7780, 19.0]
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val waypoints = listOf(
            LocationPoint(51.7730, 6.2920, "Start"),
            LocationPoint(51.7780, 6.2980, "Ziel")
        )
        val route = OfflineRoutingRepository.parseBRouterGeoJson(sampleGeoJson, waypoints, TravelMode.CAR)
        assertNotNull(route)
        route!!
        assertEquals(5, route.geometryPoints.size)
        assertEquals(1250.0, route.totalDistanceMeters, 0.1)
        assertFalse(route.isOfflineFallback)

        // Depart + right turn + roundabout 2nd exit + left turn + arrive
        assertTrue(route.steps.any { it.maneuverType == "turn" && it.maneuverModifier == "right" })
        val rb = route.steps.firstOrNull { it.maneuverType == "roundabout" }
        assertNotNull(rb)
        assertEquals(2, rb!!.roundaboutExit)
        assertTrue(rb.instruction.contains("zweite Ausfahrt"))
        assertTrue(route.steps.any { it.maneuverType == "turn" && it.maneuverModifier == "left" })
        assertEquals("arrive", route.steps.last().maneuverType)
    }
}
