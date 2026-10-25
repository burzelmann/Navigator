package com.example.nagivator.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiEntityTest {

    @Test
    fun displayTitle_usesCustomTitleWhenAvailable() {
        val poi = PoiEntity(
            title = "Mein Zuhause",
            description = "Musterstraße 12",
            latitude = 52.5200,
            longitude = 13.4050,
            isFavorite = true
        )
        assertEquals("Mein Zuhause", poi.displayTitle)
    }

    @Test
    fun displayTitle_fallsBackToDescriptionWhenTitleIsBlank() {
        val poi = PoiEntity(
            title = "",
            description = "Bäckerei Sonnenschein",
            latitude = 52.5200,
            longitude = 13.4050
        )
        assertEquals("Bäckerei Sonnenschein", poi.displayTitle)
    }

    @Test
    fun displayTitle_fallsBackToCoordinatesWhenBothBlank() {
        val poi = PoiEntity(
            title = "",
            description = "",
            latitude = 52.5200,
            longitude = 13.4050
        )
        assertTrue(poi.displayTitle.startsWith("Markierter Ort"))
        assertTrue(poi.displayTitle.contains("52.520") || poi.displayTitle.contains("52,520"))
    }

    @Test
    fun renamingPoi_preservesCoordinatesAndUpdatesFavoriteStatus() {
        val original = PoiEntity(
            title = "Vorübergehender Ort",
            description = "Adresse 1",
            latitude = 50.1109,
            longitude = 8.6821,
            isFavorite = false
        )

        val renamed = original.copy(
            title = "Büro Frankfurt",
            description = "Neue Notiz",
            iconName = "work",
            isFavorite = true
        )

        assertEquals("Büro Frankfurt", renamed.displayTitle)
        assertEquals("Neue Notiz", renamed.description)
        assertEquals("work", renamed.iconName)
        assertTrue(renamed.isFavorite)
        assertEquals(original.latitude, renamed.latitude, 0.0001)
        assertEquals(original.longitude, renamed.longitude, 0.0001)
    }
}
