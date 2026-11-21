package com.example.nagivator.ui.map

import com.example.nagivator.data.repository.MapColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleHelperTest {

    @Test
    fun minorRoads_haveStrongPositiveContrastOverLandAndUrban_acrossAllSchemes() {
        val whiteRoad = 0xFFFFFFFF.toInt()
        val openLand = 0xFFF2EFE9.toInt()
        val urbanArea = 0xFFE0DFDF.toInt()

        MapColorScheme.entries.forEach { scheme ->
            val nightRoad = MapStyleHelper.transformNightPixel(whiteRoad, scheme)
            val nightLand = MapStyleHelper.transformNightPixel(openLand, scheme)
            val nightUrban = MapStyleHelper.transformNightPixel(urbanArea, scheme)

            val lumRoad = MapStyleHelper.relativeLuminance(nightRoad)
            val lumLand = MapStyleHelper.relativeLuminance(nightLand)
            val lumUrban = MapStyleHelper.relativeLuminance(nightUrban)

            assertTrue("$scheme: Road must be brighter than land", lumRoad > lumLand)
            assertTrue("$scheme: Road must be brighter than urban", lumRoad > lumUrban)

            val ratioLand = MapStyleHelper.contrastRatio(nightRoad, nightLand)
            assertTrue("$scheme: Road vs land contrast should be >= 3.5, got $ratioLand", ratioLand >= 3.5)
        }
    }

    @Test
    fun majorRoadsAndLabels_remainBrightAndHighContrast() {
        val majorYellowRoad = 0xFFFCD6A4.toInt() // OSM primary/secondary yellow-orange
        val darkLabel = 0xFF2B2B2B.toInt()
        val openLand = 0xFFF2EFE9.toInt()

        MapColorScheme.entries.forEach { scheme ->
            val nightMajor = MapStyleHelper.transformNightPixel(majorYellowRoad, scheme)
            val nightLabel = MapStyleHelper.transformNightPixel(darkLabel, scheme)
            val nightLand = MapStyleHelper.transformNightPixel(openLand, scheme)

            val majorContrast = MapStyleHelper.contrastRatio(nightMajor, nightLand)
            val labelContrast = MapStyleHelper.contrastRatio(nightLabel, nightLand)

            assertTrue("$scheme: Major road contrast >= 5.0, got $majorContrast", majorContrast >= 5.0)
            assertTrue("$scheme: Label contrast >= 7.0, got $labelContrast", labelContrast >= 7.0)
        }
    }

    @Test
    fun water_mapsToDarkBlue() {
        val osmWater = 0xFFAAD3DF.toInt()
        val nightWater = MapStyleHelper.transformNightPixel(osmWater, MapStyleHelper.paletteFor(MapColorScheme.GRAPHITE).let { MapColorScheme.GRAPHITE })
        val r = (nightWater ushr 16) and 0xFF
        val b = nightWater and 0xFF
        assertTrue("Water should remain darker and bluish (b=$b > r=$r)", b > r && b < 80)
    }
}
