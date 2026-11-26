package com.example.nagivator.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSearchIndexTest {

    @Test
    fun parsesOverpassJsonDeduplicatesStreetSegmentsAndAssignsPriorities() {
        val sampleJson = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 1,
                  "lat": 51.7375,
                  "lon": 6.2920,
                  "tags": { "place": "town", "name": "Kalkar" }
                },
                {
                  "type": "way",
                  "id": 2,
                  "center": { "lat": 51.7730, "lon": 6.2920 },
                  "tags": { "highway": "residential", "name": "Scholtenweg" }
                },
                {
                  "type": "way",
                  "id": 3,
                  "center": { "lat": 51.7750, "lon": 6.2940 },
                  "tags": { "highway": "residential", "name": "Scholtenweg" }
                },
                {
                  "type": "node",
                  "id": 4,
                  "lat": 51.7390,
                  "lon": 6.2950,
                  "tags": { "amenity": "fuel", "name": "Aral Tankstelle", "addr:city": "Kalkar" }
                },
                {
                  "type": "node",
                  "id": 5,
                  "lat": 51.7400,
                  "lon": 6.2960,
                  "tags": { "shop": "supermarket", "name": "REWE" }
                },
                {
                  "type": "node",
                  "id": 6,
                  "lat": 51.7410,
                  "lon": 6.2970,
                  "tags": { "highway": "residential" }
                }
              ]
            }
        """.trimIndent()

        val items = OfflineSearchIndexRepository.parseOverpassJson(sampleJson, "Kalkar")
        assertEquals(4, items.size)

        val town = items.first { it.name == "Kalkar" }
        assertEquals("Ort", town.category)
        assertEquals(3, town.priority)
        assertEquals("Ort • Offline (Kalkar)", town.subtitle)

        val street = items.first { it.name == "Scholtenweg" }
        assertEquals("Straße", street.category)
        assertEquals(1, street.priority)
        // Centroid of two Scholtenweg segments (51.7730+51.7750)/2 = 51.7740
        assertEquals(51.7740, street.latitude, 0.0001)
        assertEquals(6.2930, street.longitude, 0.0001)

        val fuel = items.first { it.name == "Aral Tankstelle" }
        assertEquals("Tankstelle", fuel.category)
        assertEquals(2, fuel.priority)
        assertTrue(fuel.subtitle.contains("Kalkar"))

        val shop = items.first { it.name == "REWE" }
        assertEquals("Supermarkt", shop.category)
        assertEquals(2, shop.priority)
    }

    @Test
    fun parsesPhotonBboxJsonWithGermanCategoriesAndOfflineSubtitle() {
        val sampleJson = """
            {
              "features": [
                {
                  "geometry": { "coordinates": [6.2920, 51.7375] },
                  "properties": { "name": "Kalkar", "osm_key": "place", "osm_value": "town", "city": "Kalkar" }
                },
                {
                  "geometry": { "coordinates": [6.2945, 51.7392] },
                  "properties": { "name": "Grabenstraße", "osm_key": "highway", "osm_value": "residential", "city": "Kalkar" }
                },
                {
                  "geometry": { "coordinates": [6.2950, 51.7390] },
                  "properties": { "name": "Aral", "osm_key": "amenity", "osm_value": "fuel", "city": "Kalkar" }
                }
              ]
            }
        """.trimIndent()

        val items = OfflineSearchIndexRepository.parsePhotonBboxJson(sampleJson, "Kalkar")
        assertEquals(3, items.size)

        val town = items.first { it.name == "Kalkar" }
        assertEquals("Ort", town.category)
        assertEquals(3, town.priority)
        assertEquals("Ort • Offline (Kalkar)", town.subtitle)

        val street = items.first { it.name == "Grabenstraße" }
        assertEquals("Straße", street.category)
        assertEquals(1, street.priority)
        assertEquals("Straße • Kalkar • Offline (Kalkar)", street.subtitle)

        val fuel = items.first { it.name == "Aral" }
        assertEquals("Tankstelle", fuel.category)
        assertEquals(2, fuel.priority)
    }
}
