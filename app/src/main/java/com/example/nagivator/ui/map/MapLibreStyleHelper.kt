package com.example.nagivator.ui.map

import com.example.nagivator.data.repository.MapColorScheme
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

/**
 * Native GPU vector map styles powered by OpenFreeMap (100% free, keyless, OSM data)
 * with instant in-place GPU restyling for all 4 automotive night color schemes.
 */
object MapLibreStyleHelper {

    const val DAY_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    const val NIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

    fun styleUrlFor(isDarkTheme: Boolean): String {
        return if (isDarkTheme) NIGHT_STYLE_URL else DAY_STYLE_URL
    }

    /**
     * Dynamically applies one of our 4 ISO-15008-inspired night color palettes directly to
     * OpenFreeMap's vector layers on the GPU — instant, zero tile reload, crisp vector typography.
     */
    fun applyNightColorScheme(style: Style, scheme: MapColorScheme) {
        val p = MapStyleHelper.paletteFor(scheme)

        (style.getLayer("background") as? BackgroundLayer)?.setProperties(
            PropertyFactory.backgroundColor(p.bgCanvas)
        )

        listOf("water", "water_intermittent").forEach { id ->
            (style.getLayer(id) as? FillLayer)?.setProperties(
                PropertyFactory.fillColor(p.water)
            )
        }
        (style.getLayer("waterway") as? LineLayer)?.setProperties(
            PropertyFactory.lineColor(p.water)
        )

        listOf("landuse_residential", "landuse_commercial", "landuse_industrial").forEach { id ->
            (style.getLayer(id) as? FillLayer)?.setProperties(
                PropertyFactory.fillColor(p.urban)
            )
        }

        listOf("landcover_wood", "landuse_park", "landcover_grass", "park").forEach { id ->
            (style.getLayer(id) as? FillLayer)?.setProperties(
                PropertyFactory.fillColor(p.forest)
            )
        }

        listOf("building", "building-3d").forEach { id ->
            (style.getLayer(id) as? FillLayer)?.setProperties(
                PropertyFactory.fillColor(p.building),
                PropertyFactory.fillOutlineColor(p.roadCasing)
            )
        }

        listOf("highway_minor", "highway_path", "road_pier").forEach { id ->
            (style.getLayer(id) as? LineLayer)?.setProperties(
                PropertyFactory.lineColor(p.minorRoad)
            )
        }

        listOf("highway_major_casing", "highway_motorway_casing").forEach { id ->
            (style.getLayer(id) as? LineLayer)?.setProperties(
                PropertyFactory.lineColor(p.roadCasing)
            )
        }

        listOf("highway_major_inner", "highway_major_subtle").forEach { id ->
            (style.getLayer(id) as? LineLayer)?.setProperties(
                PropertyFactory.lineColor(p.majorRoad)
            )
        }

        listOf("highway_motorway_inner", "highway_motorway_subtle").forEach { id ->
            (style.getLayer(id) as? LineLayer)?.setProperties(
                PropertyFactory.lineColor(p.motorway)
            )
        }

        // Crisp high-legibility vector labels with dark anti-glare halo
        style.layers.filterIsInstance<SymbolLayer>().forEach { sym ->
            val id = sym.id
            if (id.startsWith("highway_name") || id.startsWith("place_") || id.startsWith("water_name") || id.startsWith("label_")) {
                sym.setProperties(
                    PropertyFactory.textColor(p.label),
                    PropertyFactory.textHaloColor(p.bgCanvas),
                    PropertyFactory.textHaloWidth(1.6f)
                )
            }
        }
    }
}
