package com.example.nagivator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun ElevationProfileView(
    elevationPoints: List<Pair<Double, Double>>, // Pair<DistanceMeters, AltitudeMeters>
    modifier: Modifier = Modifier
) {
    if (elevationPoints.size < 2) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)

    val minAlt = elevationPoints.minOf { it.second }
    val maxAlt = elevationPoints.maxOf { it.second }
    val maxDist = elevationPoints.last().first

    val altRange = (maxAlt - minAlt).coerceAtLeast(10.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Höhenprofil (${minAlt.toInt()} m - ${maxAlt.toInt()} m)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            val width = size.width
            val height = size.height

            val linePath = Path()
            val fillPath = Path()

            elevationPoints.forEachIndexed { index, (dist, alt) ->
                val x = (dist / maxDist * width).toFloat()
                val normalizedAlt = ((alt - minAlt) / altRange).toFloat()
                val y = height - (normalizedAlt * (height - 10f) + 5f)

                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }

                if (index == elevationPoints.size - 1) {
                    fillPath.lineTo(x, height)
                    fillPath.close()
                }
            }

            drawPath(
                path = fillPath,
                color = surfaceVariant
            )

            drawPath(
                path = linePath,
                color = primaryColor,
                style = Stroke(width = 4f)
            )
        }
    }
}
