package com.example.nagivator.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nagivator.data.local.entity.PoiEntity
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TravelMode

import androidx.compose.material3.CircularProgressIndicator
import com.example.nagivator.domain.model.PoiIconRegistry
import com.example.nagivator.ui.components.ElevationProfileView

@Composable
fun PoiDetailsCard(
    poi: PoiEntity,
    isCalculatingRoute: Boolean,
    onStartRoute: (TravelMode) -> Unit,
    onStartRoutePlanner: () -> Unit,
    onClose: () -> Unit,
    onToggleFavorite: (PoiEntity) -> Unit,
    onEditPoi: ((PoiEntity) -> Unit)? = null,
    onDeletePoi: ((PoiEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(
                            imageVector = PoiIconRegistry.getIcon(poi.iconName),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column {
                        Text(
                            text = poi.displayTitle,
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (poi.description.isNotBlank()) {
                            Text(
                                text = poi.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row {
                    if (onEditPoi != null) {
                        IconButton(onClick = { onEditPoi(poi) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Ort bearbeiten",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { onToggleFavorite(poi) }) {
                        Icon(
                            imageVector = if (poi.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorit",
                            tint = if (poi.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (onDeletePoi != null && (poi.id > 0L || poi.isFavorite)) {
                        IconButton(onClick = { onDeletePoi(poi) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Ort löschen",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isCalculatingRoute) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(24.dp)
                            .width(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Berechne Route...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Schnell-Aktionen:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onStartRoute(TravelMode.FOOT) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DirectionsWalk, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Zu Fuß")
                    }

                    Button(
                        onClick = { onStartRoute(TravelMode.CAR) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mit Auto")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = onStartRoutePlanner,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Routenplaner mit Wegpunkten starten")
                }
            }
        }
    }
}

@Composable
fun RouteCard(
    route: RouteResult,
    selectedPoi: PoiEntity?,
    currentTravelMode: TravelMode,
    isNavigating: Boolean,
    currentStepIndex: Int = 0,
    onModeChange: (TravelMode) -> Unit,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    onToggleFavorite: (PoiEntity) -> Unit,
    onEditPoi: ((PoiEntity) -> Unit)? = null,
    onDeletePoi: ((PoiEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val destName = selectedPoi?.displayTitle 
                        ?: route.waypoints.lastOrNull()?.name?.ifBlank { null } 
                        ?: "Zielort"
                        
                    Text(
                        text = destName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    if (selectedPoi?.description?.isNotBlank() == true) {
                        Text(
                            text = selectedPoi.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (route.waypoints.size > 2) {
                        Text(
                            text = "${route.waypoints.size - 2} Zwischenstopp(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    if (selectedPoi != null) {
                        if (onEditPoi != null) {
                            IconButton(onClick = { onEditPoi(selectedPoi) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Ort bearbeiten",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { onToggleFavorite(selectedPoi) }) {
                            Icon(
                                imageVector = if (selectedPoi.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorit",
                                tint = if (selectedPoi.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (onDeletePoi != null && (selectedPoi.id > 0L || selectedPoi.isFavorite)) {
                            IconButton(onClick = { onDeletePoi(selectedPoi) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Ort löschen",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    IconButton(onClick = onStopNavigation) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Travel Mode Segmented Row
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = currentTravelMode == TravelMode.FOOT,
                    onClick = { onModeChange(TravelMode.FOOT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Default.DirectionsWalk, contentDescription = null) }
                ) {
                    Text("Zu Fuß")
                }
                SegmentedButton(
                    selected = currentTravelMode == TravelMode.CAR,
                    onClick = { onModeChange(TravelMode.CAR) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) }
                ) {
                    Text("Auto")
                }
            }

            // Elevation Profile (if available)
            if (route.elevationPoints.size >= 2) {
                Spacer(modifier = Modifier.height(12.dp))
                ElevationProfileView(
                    elevationPoints = route.elevationPoints,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Route Duration & Start Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = route.formattedDuration,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Entfernung: ${route.formattedDistance}" + if (route.formattedElevation.isNotBlank()) " • ${route.formattedElevation}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isNavigating) {
                    Button(
                        onClick = onStartNavigation,
                        shape = CircleShape,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = "Starten")
                    }
                } else {
                    OutlinedButton(
                        onClick = onStopNavigation,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Beenden", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Next Step Anweisung in Navigation Mode
            if (isNavigating && route.steps.isNotEmpty()) {
                val safeIndex = currentStepIndex.coerceIn(0, route.steps.size - 1)
                val isLastStep = safeIndex == route.steps.size - 1 && safeIndex > 0
                val currentStep = route.steps[safeIndex]
                
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLastStep) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconVector = if (isLastStep) Icons.Default.Place else getManeuverIcon(currentStep.maneuverModifier)
                        val textColor = if (isLastStep) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = textColor
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isLastStep) "Ziel erreicht" else currentStep.instruction.ifBlank { "Dem Weg folgen" },
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor
                        )
                    }
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
