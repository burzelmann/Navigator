package com.example.nagivator.data.repository

import com.example.nagivator.data.local.entity.OfflineRegionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRoutingCatalogTest {

    @Test
    fun describesKnownAndUnknownSegments() {
        val nrw = OfflineRoutingCatalog.describeSegment("E5_N50.rd5")
        assertEquals("Westdeutschland & NRW", nrw.title)
        assertEquals(RoutingTileGroup.DACH_NEIGHBORS, nrw.group)

        val bavaria = OfflineRoutingCatalog.describeSegment("E10_N45.rd5")
        assertTrue(bavaria.title.contains("Bayern"))

        val california = OfflineRoutingCatalog.describeSegment("W125_N35.rd5")
        assertEquals("Region W125_N35", california.title)
    }

    @Test
    fun computesMissingSegmentsAndCoveredRegionsAccurately() {
        val kalkar = OfflineRegionEntity(
            id = 1,
            name = "Kalkar",
            minLat = 51.72,
            maxLat = 51.79,
            minLon = 6.24,
            maxLon = 6.35,
            minZoom = 11,
            maxZoom = 14,
            tileCount = 120,
            sizeMB = 3.2
        )
        val borderRegion = OfflineRegionEntity(
            id = 2,
            name = "Alpen-Nord-Süd",
            minLat = 49.5,
            maxLat = 50.5,
            minLon = 6.0,
            maxLon = 7.0,
            minZoom = 10,
            maxZoom = 14,
            tileCount = 200,
            sizeMB = 6.5
        )

        assertEquals(listOf("E5_N50.rd5"), OfflineRoutingCatalog.requiredSegmentsFor(kalkar))
        assertTrue(OfflineRoutingCatalog.missingSegmentsFor(kalkar, setOf("E5_N50.rd5")).isEmpty())
        assertEquals(listOf("E5_N50.rd5"), OfflineRoutingCatalog.missingSegmentsFor(kalkar, emptySet()))

        // Border region spans E5_N45 + E5_N50
        assertEquals(
            listOf("E5_N45.rd5"),
            OfflineRoutingCatalog.missingSegmentsFor(borderRegion, setOf("E5_N50.rd5"))
        )
        assertEquals(
            listOf("Kalkar", "Alpen-Nord-Süd"),
            OfflineRoutingCatalog.coveredRegionNames("E5_N50.rd5", listOf(kalkar, borderRegion))
        )
    }
}
