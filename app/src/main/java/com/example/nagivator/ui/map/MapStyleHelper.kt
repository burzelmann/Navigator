package com.example.nagivator.ui.map

import android.graphics.Bitmap
import android.graphics.Color
import com.example.nagivator.data.repository.MapColorScheme
import kotlin.math.max
import kotlin.math.min

/**
 * Semantic night-mode map tile transformer following automotive cartography & ISO 15008 principles:
 * - Positive polarity: roads are rendered significantly brighter than land/building backgrounds.
 * - Low-glare charcoal/slate/midnight backgrounds (avoiding pure #000000 smearing).
 * - Preserved road hierarchy (bright amber/gold primaries, clear silver-gray residential roads,
 *   crisp off-white labels, muted dark land/forest/water).
 */
object MapStyleHelper {

    data class NightPalette(
        val bgCanvas: Int,
        val land: Int,
        val urban: Int,
        val building: Int,
        val forest: Int,
        val water: Int,
        val roadCasing: Int,
        val minorRoad: Int,
        val majorRoad: Int,
        val motorway: Int,
        val label: Int
    )

    fun paletteFor(scheme: MapColorScheme): NightPalette = when (scheme) {
        MapColorScheme.GRAPHITE -> NightPalette(
            bgCanvas = 0xFF161A20.toInt(),
            land = 0xFF181D24.toInt(),
            urban = 0xFF1E242D.toInt(),
            building = 0xFF262E3A.toInt(),
            forest = 0xFF16221E.toInt(),
            water = 0xFF0E2238.toInt(),
            roadCasing = 0xFF2F3947.toInt(),
            minorRoad = 0xFF7B8B9E.toInt(),
            majorRoad = 0xFFE6B45C.toInt(),
            motorway = 0xFF5CA4E6.toInt(),
            label = 0xFFF2F6FA.toInt()
        )
        MapColorScheme.MIDNIGHT_BLUE -> NightPalette(
            bgCanvas = 0xFF0F1724.toInt(),
            land = 0xFF121C2B.toInt(),
            urban = 0xFF182438.toInt(),
            building = 0xFF213048.toInt(),
            forest = 0xFF102329.toInt(),
            water = 0xFF0A1B33.toInt(),
            roadCasing = 0xFF2C3E59.toInt(),
            minorRoad = 0xFF7D95B8.toInt(),
            majorRoad = 0xFFF0BE64.toInt(),
            motorway = 0xFF66B8FF.toInt(),
            label = 0xFFF4F8FF.toInt()
        )
        MapColorScheme.WARM_AMBER -> NightPalette(
            bgCanvas = 0xFF181410.toInt(),
            land = 0xFF1C1712.toInt(),
            urban = 0xFF241E17.toInt(),
            building = 0xFF2E261D.toInt(),
            forest = 0xFF1B2016.toInt(),
            water = 0xFF161E26.toInt(),
            roadCasing = 0xFF3E3328.toInt(),
            minorRoad = 0xFF917F68.toInt(),
            majorRoad = 0xFFF2B857.toInt(),
            motorway = 0xFFE08B4F.toInt(),
            label = 0xFFFAF0E1.toInt()
        )
        MapColorScheme.HIGH_CONTRAST -> NightPalette(
            bgCanvas = 0xFF101318.toInt(),
            land = 0xFF12161C.toInt(),
            urban = 0xFF171C24.toInt(),
            building = 0xFF1F2630.toInt(),
            forest = 0xFF101D18.toInt(),
            water = 0xFF0B1C30.toInt(),
            roadCasing = 0xFF354052.toInt(),
            minorRoad = 0xFF9EB0C7.toInt(),
            majorRoad = 0xFFFFCA5C.toInt(),
            motorway = 0xFF6BC2FF.toInt(),
            label = 0xFFFFFFFF.toInt()
        )
    }

    fun canvasBackgroundColor(isDark: Boolean, scheme: MapColorScheme): Int {
        return if (!isDark) 0xFFEBF2F5.toInt() else paletteFor(scheme).bgCanvas
    }

    /**
     * Transforms a single ARGB pixel from a light OSM-Carto tile into a high-legibility night pixel.
     * Pure Kotlin math (no Android framework calls) so it can be unit-tested on the JVM.
     */
    fun transformNightPixel(argb: Int, scheme: MapColorScheme): Int {
        val a = (argb ushr 24) and 0xFF
        if (a == 0) return argb
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF

        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val chroma = maxC - minC
        val lum = (0.299f * r + 0.587f * g + 0.114f * b)
        val p = paletteFor(scheme)

        val mapped = when {
            // 1. Pure white / near-white -> Residential & local roads (OSM Carto white fill)
            minC >= 244 && chroma <= 12 -> p.minorRoad

            // 2. Dark labels, road shields, crisp outlines -> Off-white with smooth AA blending
            lum < 110f && chroma < 45 -> {
                val t = ((110f - lum) / 110f).coerceIn(0f, 1f)
                lerpColor(p.roadCasing, p.label, t)
            }

            // 3. Saturated warm hues -> Primary / Secondary / Tertiary / Trunk roads (yellow, orange, peach, red)
            //    Higher chroma threshold (>= 48) avoids false matches on pale sand/dune/farmland fills (#EEE5C4)
            chroma >= 48 && r >= g - 15 && r > b + 38 && lum >= 120f -> {
                val intensity = ((chroma - 48f) / 60f).coerceIn(0.55f, 1f)
                lerpColor(p.minorRoad, p.majorRoad, intensity)
            }

            // 4. Water (pale cyan-blue #AAD3DF) vs. Motorway (deeper blue #7092FF / #89A4CB)
            b > r + 14 && chroma >= 18 -> {
                if (g >= 188 && b >= 200 && r >= 140) {
                    p.water
                } else if (lum >= 100f && b > g + 8) {
                    p.motorway
                } else {
                    p.water
                }
            }

            // 5. Forest, parks, grass, farmland (greenish tint)
            g > r + 5 && g > b + 5 -> p.forest

            // 6. Road casings & medium gray outlines (between dark text and light urban/buildings)
            chroma <= 18 && lum in 110f..185f -> {
                val t = ((lum - 110f) / 75f).coerceIn(0f, 1f)
                lerpColor(p.roadCasing, p.building, t)
            }

            // 7. Buildings (warm beige-gray ~#D9D0C9)
            lum in 185f..218f -> p.building

            // 8. Urban / residential area fill (~#E0DFDF)
            lum in 218f..234f -> p.urban

            // 9. Open land background (~#F2EFE9)
            else -> p.land
        }

        return (a shl 24) or (mapped and 0x00FFFFFF)
    }

    /**
     * Transforms an entire 256x256 OSM tile bitmap into a night-mode bitmap.
     */
    fun transformNightBitmap(source: Bitmap, scheme: MapColorScheme): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            pixels[i] = transformNightPixel(pixels[i], scheme)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Computes relative WCAG luminance (0.0..1.0) for contrast verification in tests.
     */
    fun relativeLuminance(argb: Int): Double {
        fun linear(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    fun contrastRatio(c1: Int, c2: Int): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val r = (((c1 ushr 16) and 0xFF) * (1f - f) + ((c2 ushr 16) and 0xFF) * f).toInt()
        val g = (((c1 ushr 8) and 0xFF) * (1f - f) + ((c2 ushr 8) and 0xFF) * f).toInt()
        val b = ((c1 and 0xFF) * (1f - f) + (c2 and 0xFF) * f).toInt()
        return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }
}
