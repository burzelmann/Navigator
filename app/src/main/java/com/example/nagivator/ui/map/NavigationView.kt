package com.example.nagivator.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nagivator.domain.model.RouteResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NavigationView(
    route: RouteResult,
    currentStepIndex: Int,
    isMuted: Boolean,
    isOverviewMode: Boolean,
    mapOrientation: Float,
    bottomPadding: Dp = 0.dp,
    onToggleMute: () -> Unit,
    onToggleOverviewMode: () -> Unit,
    onResetNorth: () -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeIndex = currentStepIndex.coerceIn(0, route.steps.size - 1)
    val isLastStep = safeIndex == route.steps.size - 1 && safeIndex > 0
    val currentStep = route.steps[safeIndex]
    val nextStep = if (safeIndex < route.steps.size - 1) route.steps[safeIndex + 1] else null

    // Compute ETA time
    val etaMillis = System.currentTimeMillis() + (route.totalDurationSeconds * 1000).toLong()
    val etaFormatted = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(etaMillis)) + " Uhr"

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Top Guidance Banner (Prominentes Abbiegeschild)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLastStep) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isLastStep) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Icon(
                                imageVector = if (isLastStep) Icons.Default.Place else getManeuverIcon(currentStep.maneuverModifier),
                                contentDescription = null,
                                tint = if (isLastStep) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(14.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            val distText = if (currentStep.distanceMeters >= 1000) {
                                "IN %.1f KM".format(currentStep.distanceMeters / 1000.0)
                            } else {
                                "IN ${currentStep.distanceMeters.toInt()} METERN"
                            }
                            Text(
                                text = if (isLastStep) "AM ZIEL" else distText,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isLastStep) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isLastStep) "Ziel erreicht" else currentStep.instruction.ifBlank { "Dem Weg folgen" },
                                style = MaterialTheme.typography.titleLarge,
                                color = if (isLastStep) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (nextStep != null && !isLastStep) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getManeuverIcon(nextStep.maneuverModifier),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "Danach: ${nextStep.instruction.ifBlank { "Geradeaus" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Side Floating Action Buttons (Rechte Seite)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = onResetNorth,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = "Einnorden",
                    modifier = Modifier.rotate(-mapOrientation)
                )
            }

            FloatingActionButton(
                onClick = onToggleMute,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Ton Umschalten"
                )
            }

            FloatingActionButton(
                onClick = onToggleOverviewMode,
                containerColor = if (isOverviewMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (isOverviewMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "Routen-Übersicht"
                )
            }
        }

        // 3. Bottom Cockpit (Untere Ankunfts-Anzeige)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = bottomPadding + 16.dp, start = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = etaFormatted,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${route.formattedDuration} • ${route.formattedDistance}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onStopNavigation,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BEENDEN", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun getManeuverIcon(modifier: String): ImageVector {
    return when {
        modifier.contains("left", ignoreCase = true) -> Icons.Default.TurnLeft
        modifier.contains("right", ignoreCase = true) -> Icons.Default.TurnRight
        modifier.contains("roundabout", ignoreCase = true) -> Icons.Default.RoundaboutRight
        modifier.contains("merge", ignoreCase = true) -> Icons.AutoMirrored.Filled.MergeType
        else -> Icons.Default.ArrowUpward
    }
}
