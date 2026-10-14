package com.example.nagivator.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TravelMode
import com.example.nagivator.ui.components.ElevationProfileView

@Composable
fun RoutePlannerCard(
    waypoints: List<LocationPoint>,
    route: RouteResult?,
    currentTravelMode: TravelMode,
    isCalculatingRoute: Boolean,
    isMinimized: Boolean,
    isSavedRoute: Boolean = false,
    onModeChange: (TravelMode) -> Unit,
    onRemoveWaypoint: (Int) -> Unit,
    onMoveWaypoint: (Int, Int) -> Unit,
    onStartNavigation: () -> Unit,
    onSaveRoute: (() -> Unit)? = null,
    onDeleteSavedRoute: (() -> Unit)? = null,
    onClosePlanner: () -> Unit,
    onToggleMinimize: () -> Unit,
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
                Text(
                    text = "Routenplaner" + if (isMinimized) " (${waypoints.size} Stopps)" else "",
                    style = MaterialTheme.typography.titleLarge
                )
                Row {
                    if (isSavedRoute && onDeleteSavedRoute != null) {
                        IconButton(onClick = onDeleteSavedRoute) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Gespeicherte Route löschen",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (onSaveRoute != null && route != null) {
                        IconButton(onClick = onSaveRoute) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "Route in Favoriten speichern",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onToggleMinimize) {
                        Icon(
                            imageVector = if (isMinimized) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isMinimized) "Ausklappen" else "Minimieren",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onClosePlanner) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !isMinimized,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Travel Mode Segmented Row
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = currentTravelMode == TravelMode.FOOT,
                            onClick = { onModeChange(TravelMode.FOOT) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null) }
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Waypoints List
                    LazyColumn(
                        modifier = Modifier.height(180.dp)
                    ) {
                        itemsIndexed(waypoints) { index, point ->
                            val letter = ('A' + index).toString()
                            ListItem(
                                headlineContent = { Text(point.name.ifBlank { "Unbekannter Ort" }) },
                                leadingContent = {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = letter,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (index > 0) {
                                            IconButton(onClick = { onMoveWaypoint(index, index - 1) }) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "Nach oben verschieben",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (index < waypoints.size - 1) {
                                            IconButton(onClick = { onMoveWaypoint(index, index + 1) }) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Nach unten verschieben",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (waypoints.size > 1) {
                                            IconButton(onClick = { onRemoveWaypoint(index) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Entfernen",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        item {
                            Text(
                                text = "Tippe auf die Karte oder suche nach Orten, um Stopps hinzuzufügen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Footer / Stats / Start Nav
                    if (isCalculatingRoute) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(24.dp).width(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Berechne...", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else if (route != null) {
                        if (route.elevationPoints.size >= 2) {
                            ElevationProfileView(
                                elevationPoints = route.elevationPoints,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = route.formattedDuration,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Entfernung: ${route.formattedDistance}" + if (route.formattedElevation.isNotBlank()) " • ${route.formattedElevation}" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = onStartNavigation,
                                shape = CircleShape,
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = "Starten")
                            }
                        }
                    }
                }
            }
        }
    }
}
