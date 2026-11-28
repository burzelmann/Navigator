package com.example.nagivator.data.repository

import com.example.nagivator.domain.model.TrafficIncident
import com.example.nagivator.domain.model.TrafficIncidentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerkehrNrwTrafficTest {

    @Test
    fun parsesActiveCongestionClosureAndFiltersFutureEvents() {
        val sampleJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "id": "f1",
                  "geometry": { "type": "Point", "coordinates": [7.4571, 51.5241] },
                  "properties": {
                    "identifier": "TTI-123",
                    "display_type": "CONGESTION",
                    "future": false,
                    "info": "+15 ",
                    "travel_time_loss": 756
                  }
                },
                {
                  "type": "Feature",
                  "id": "f2",
                  "geometry": { "type": "Point", "coordinates": [6.4636, 51.7190] },
                  "properties": {
                    "identifier": "CL-456",
                    "display_type": "CLOSURE",
                    "future": false,
                    "info": "Vollsperrung"
                  }
                },
                {
                  "type": "Feature",
                  "id": "f3",
                  "geometry": { "type": "Point", "coordinates": [6.5992, 51.8446] },
                  "properties": {
                    "identifier": "CL-FUTURE",
                    "display_type": "CLOSURE",
                    "future": true
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = TrafficRepository.parseVerkehrNrwGeoJson(
            rawJson = sampleJson,
            layerName = "vipnrw:traffic_obstructions_urban_feature"
        )
        assertEquals(2, parsed.size)

        val jam = parsed[0]
        assertEquals(TrafficIncidentType.WARNING, jam.type)
        assertEquals(756, jam.delaySeconds)
        assertTrue(jam.title.contains("+12 Min."))
        assertFalse(jam.isBlocked)
        assertEquals("Verkehr.NRW", jam.source)

        val closure = parsed[1]
        assertEquals(TrafficIncidentType.CLOSURE, closure.type)
        assertTrue(closure.isBlocked)
    }

    @Test
    fun deduplicatesOverlappingAutobahnAndVerkehrNrwIncidentsPreferringDelay() {
        val autobahn = TrafficIncident(
            id = "ab_1",
            roadId = "A40",
            type = TrafficIncidentType.WARNING,
            title = "Stau",
            latitude = 51.4500,
            longitude = 7.0100,
            source = "Autobahn"
        )
        val nrwWithDelay = TrafficIncident(
            id = "vnrw_1",
            roadId = "A40",
            type = TrafficIncidentType.WARNING,
            title = "Stau (+10 Min.)",
            latitude = 51.4505,
            longitude = 7.0105,
            delaySeconds = 600,
            source = "Verkehr.NRW"
        )
        val dedup = TrafficRepository.deduplicateSpatially(listOf(autobahn, nrwWithDelay))
        assertEquals(1, dedup.size)
        assertEquals(600, dedup.first().delaySeconds)
    }

    @Test
    fun enforcesStrict40mClosureFilterForUrbanOpenDataAndPlausibleDetourGuard() {
        val route = com.example.nagivator.domain.model.RouteResult(
            waypoints = listOf(
                com.example.nagivator.domain.model.LocationPoint(51.7400, 6.2900),
                com.example.nagivator.domain.model.LocationPoint(51.7500, 6.2900)
            ),
            geometryPoints = listOf(
                com.example.nagivator.domain.model.LocationPoint(51.7400, 6.2900),
                com.example.nagivator.domain.model.LocationPoint(51.7500, 6.2900)
            ),
            totalDistanceMeters = 1110.0,
            totalDurationSeconds = 90.0,
            mode = com.example.nagivator.domain.model.TravelMode.CAR,
            steps = emptyList()
        )

        // ~15m off route -> MUST match
        val onRouteClosure = TrafficIncident(
            id = "nrw_near",
            roadId = "B57",
            type = TrafficIncidentType.CLOSURE,
            title = "Sperrung",
            latitude = 51.7450,
            longitude = 6.2902,
            isBlocked = true,
            source = "Verkehr.NRW"
        )
        // ~95m off route (parallel side street!) -> MUST be filtered out for Verkehr.NRW closure (> 40m)
        val sideStreetClosure = TrafficIncident(
            id = "nrw_side",
            roadId = "Nebenstraße",
            type = TrafficIncidentType.CLOSURE,
            title = "Sperrung",
            latitude = 51.7450,
            longitude = 6.2914,
            isBlocked = true,
            source = "Verkehr.NRW"
        )

        val filtered = TrafficRepository.filterIncidentsOnRoute(listOf(onRouteClosure, sideStreetClosure), route)
        assertEquals(1, filtered.size)
        assertEquals("nrw_near", filtered.first().id)

        assertTrue(TrafficRepository.isPlausibleClosureDetour(15_000.0, 10_000.0))
        assertFalse(TrafficRepository.isPlausibleClosureDetour(18_000.0, 10_000.0))
    }
}
