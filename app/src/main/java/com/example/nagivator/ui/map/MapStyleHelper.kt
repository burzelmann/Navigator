package com.example.nagivator.ui.map

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.example.nagivator.data.repository.MapColorScheme

/**
 * Shared map tile color-matrix filters and canvas background colors for both
 * the mobile OsmMapView and the Android Auto CarMapSurfaceRenderer.
 */
object MapStyleHelper {

    fun createTileColorFilter(isDark: Boolean, scheme: MapColorScheme): ColorMatrixColorFilter? {
        if (!isDark) return null
        val matrix = when (scheme) {
            MapColorScheme.GRAPHITE -> ColorMatrix(
                floatArrayOf(
                    -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
                    -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
                    -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
                    0f,       0f,        0f,       1f, 0f
                )
            )
            MapColorScheme.MIDNIGHT_BLUE -> ColorMatrix(
                floatArrayOf(
                    -0.171f, -0.576f, -0.058f, 0f, 217f,
                    -0.183f, -0.615f, -0.062f, 0f, 239f,
                    -0.197f, -0.662f, -0.067f, 0f, 255f,
                    0f,      0f,      0f,      1f, 0f
                )
            )
            MapColorScheme.WARM_AMBER -> ColorMatrix(
                floatArrayOf(
                    -0.196f, -0.658f, -0.066f, 0f, 255f,
                    -0.183f, -0.615f, -0.062f, 0f, 235f,
                    -0.130f, -0.436f, -0.044f, 0f, 165f,
                    0f,      0f,      0f,      1f, 0f
                )
            )
            MapColorScheme.HIGH_CONTRAST -> ColorMatrix(
                floatArrayOf(
                    -0.230f, -0.772f, -0.078f, 0f, 278f,
                    -0.230f, -0.772f, -0.078f, 0f, 278f,
                    -0.230f, -0.772f, -0.078f, 0f, 278f,
                    0f,      0f,      0f,      1f, 0f
                )
            )
        }
        return ColorMatrixColorFilter(matrix)
    }

    fun canvasBackgroundColor(isDark: Boolean, scheme: MapColorScheme): Int {
        if (!isDark) return Color.parseColor("#EBF2F5")
        return when (scheme) {
            MapColorScheme.GRAPHITE -> Color.parseColor("#12161A")
            MapColorScheme.MIDNIGHT_BLUE -> Color.parseColor("#0D1522")
            MapColorScheme.WARM_AMBER -> Color.parseColor("#16100A")
            MapColorScheme.HIGH_CONTRAST -> Color.parseColor("#050508")
        }
    }
}
