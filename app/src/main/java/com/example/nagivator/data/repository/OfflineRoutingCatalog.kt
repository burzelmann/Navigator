package com.example.nagivator.data.repository

import com.example.nagivator.data.local.entity.OfflineRegionEntity

enum class RoutingTileGroup(val label: String) {
    DACH_NEIGHBORS("Deutschland & Nachbarn"),
    VACATION_EUROPE("Beliebte Urlaubsregionen")
}

data class RoutingTileCatalogEntry(
    val fileName: String,
    val title: String,
    val subtitle: String,
    val group: RoutingTileGroup
)

/**
 * Curated human-readable catalog for BRouter 5°×5° `.rd5` routing tiles covering
 * DACH, Benelux, and popular European vacation regions, with automatic fallback
 * descriptions for any other 5°×5° tile worldwide.
 */
object OfflineRoutingCatalog {

    val entries: List<RoutingTileCatalogEntry> = listOf(
        // Deutschland & Nachbarn
        RoutingTileCatalogEntry(
            fileName = "E5_N50.rd5",
            title = "Westdeutschland & NRW",
            subtitle = "Niederrhein, Ruhrgebiet, Hessen, RLP, Ost-NL",
            group = RoutingTileGroup.DACH_NEIGHBORS
        ),
        RoutingTileCatalogEntry(
            fileName = "E10_N50.rd5",
            title = "Nord-, Mittel- & Ostdeutschland",
            subtitle = "Hamburg, Berlin, Sachsen, Thüringen, Franken",
            group = RoutingTileGroup.DACH_NEIGHBORS
        ),
        RoutingTileCatalogEntry(
            fileName = "E5_N45.rd5",
            title = "Südwestdeutschland & Schweiz",
            subtitle = "Baden-Württemberg, Elsass, Schweiz, Norditalien-West",
            group = RoutingTileGroup.DACH_NEIGHBORS
        ),
        RoutingTileCatalogEntry(
            fileName = "E10_N45.rd5",
            title = "Bayern, Österreich & Südtirol",
            subtitle = "München, Alpen, Tirol, Gardasee, Salzburg",
            group = RoutingTileGroup.DACH_NEIGHBORS
        ),
        RoutingTileCatalogEntry(
            fileName = "E0_N50.rd5",
            title = "Niederlande & Belgien (West)",
            subtitle = "Amsterdam, Nordseeküste, Zeeland, Brüssel",
            group = RoutingTileGroup.DACH_NEIGHBORS
        ),
        RoutingTileCatalogEntry(
            fileName = "E5_N55.rd5",
            title = "Dänemark West & Nordsee",
            subtitle = "Jütland, Nordseeküste, Sylt Nord",
            group = RoutingTileGroup.DACH_NEIGHBORS
        ),
        RoutingTileCatalogEntry(
            fileName = "E10_N55.rd5",
            title = "Dänemark Ost & Südschweden",
            subtitle = "Kopenhagen, Fünen, Ostsee, Malmö",
            group = RoutingTileGroup.DACH_NEIGHBORS
        ),

        // Beliebte Urlaubsregionen
        RoutingTileCatalogEntry(
            fileName = "E0_N45.rd5",
            title = "Frankreich (Mitte & Südost)",
            subtitle = "Lyon, Provence, Burgund, Zentralmassiv",
            group = RoutingTileGroup.VACATION_EUROPE
        ),
        RoutingTileCatalogEntry(
            fileName = "E15_N45.rd5",
            title = "Österreich Ost, Slowenien & Adria",
            subtitle = "Wien, Graz, Kroatien-Nord, Istrien",
            group = RoutingTileGroup.VACATION_EUROPE
        ),
        RoutingTileCatalogEntry(
            fileName = "E10_N40.rd5",
            title = "Mittelitalien & Toskana",
            subtitle = "Florenz, Rom, Elba, Sardinien Ost",
            group = RoutingTileGroup.VACATION_EUROPE
        ),
        RoutingTileCatalogEntry(
            fileName = "E0_N40.rd5",
            title = "Spanien Ost & Katalonien",
            subtitle = "Barcelona, Costa Brava, Valencia, Mallorca",
            group = RoutingTileGroup.VACATION_EUROPE
        ),
        RoutingTileCatalogEntry(
            fileName = "W5_N40.rd5",
            title = "Spanien Mitte & Norden",
            subtitle = "Madrid, Baskenland, Pyrenäen West",
            group = RoutingTileGroup.VACATION_EUROPE
        )
    )

    private val byFileName: Map<String, RoutingTileCatalogEntry> = entries.associateBy { it.fileName }

    fun describeSegment(fileName: String): RoutingTileCatalogEntry {
        return byFileName[fileName] ?: RoutingTileCatalogEntry(
            fileName = fileName,
            title = "Region ${fileName.removeSuffix(".rd5")}",
            subtitle = "BRouter 5°×5°-Kachel ($fileName)",
            group = RoutingTileGroup.VACATION_EUROPE
        )
    }

    /**
     * Returns all `.rd5` segment filenames required to cover the given [region] bounding box.
     */
    fun requiredSegmentsFor(region: OfflineRegionEntity): List<String> {
        return OfflineRoutingRepository.segmentsForBbox(
            minLat = region.minLat,
            maxLat = region.maxLat,
            minLon = region.minLon,
            maxLon = region.maxLon
        )
    }

    /**
     * Returns the subset of [requiredSegmentsFor] that are not yet installed.
     */
    fun missingSegmentsFor(
        region: OfflineRegionEntity,
        installedFileNames: Set<String>
    ): List<String> {
        return requiredSegmentsFor(region).filterNot { it in installedFileNames }
    }

    /**
     * Returns the names of saved map regions covered (partially or fully) by [segmentFileName].
     */
    fun coveredRegionNames(
        segmentFileName: String,
        regions: List<OfflineRegionEntity>
    ): List<String> {
        return regions.filter { segmentFileName in requiredSegmentsFor(it) }.map { it.name }
    }
}
