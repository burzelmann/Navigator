package com.example.nagivator.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nagivator.data.local.entity.PoiEntity
import com.example.nagivator.data.local.entity.SavedRouteEntity
import com.example.nagivator.domain.model.PoiIconRegistry
import com.example.nagivator.ui.components.IconPicker

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = viewModel(),
    onNavigateToMapWithPoi: (Double, Double) -> Unit = { _, _ -> },
    onNavigateToMapWithRoute: (SavedRouteEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val favoritePois by viewModel.favoritePois.collectAsState()
    val favoriteRoutes by viewModel.favoriteRoutes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var editingPoi by remember { mutableStateOf<PoiEntity?>(null) }
    var editPoiTitle by remember { mutableStateOf("") }
    var editPoiDesc by remember { mutableStateOf("") }
    var editPoiIcon by remember { mutableStateOf("place") }

    var editingRoute by remember { mutableStateOf<SavedRouteEntity?>(null) }
    var editRouteTitle by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = "Meine Favoriten",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${favoritePois.size} Orte • ${favoriteRoutes.size} Routen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            label = { Text("Favoriten durchsuchen...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Suchen") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Löschen")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (favoritePois.isEmpty() && favoriteRoutes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank()) {
                        "Noch keine Favoriten vorhanden.\nMarkiere Orte auf der Karte mit dem Herz-Symbol\noder speichere geplante Routen ab!"
                    } else {
                        "Keine Favoriten gefunden für '$searchQuery'."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Gespeicherte Routen Section
                if (favoriteRoutes.isNotEmpty()) {
                    item {
                        Text(
                            text = "Gespeicherte Routen (${favoriteRoutes.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    items(favoriteRoutes) { route ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onNavigateToMapWithRoute(route)
                                }
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        route.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                },
                                supportingContent = {
                                    val distKm = "%.1f km".format(route.distanceMeters / 1000.0)
                                    val durationMins = (route.durationSeconds / 60.0).toInt()
                                    Text(
                                        "$distKm • $durationMins Min.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = if (route.travelMode == "FOOT") Icons.AutoMirrored.Filled.DirectionsWalk else Icons.Default.DirectionsCar,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            editingRoute = route
                                            editRouteTitle = route.title
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Route umbenennen",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteRoute(route) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Route löschen",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            )
                        }
                    }
                }

                // Lieblingsorte Section
                if (favoritePois.isNotEmpty()) {
                    item {
                        Text(
                            text = "Lieblingsorte (${favoritePois.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    items(favoritePois) { poi ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onNavigateToMapWithPoi(poi.latitude, poi.longitude)
                                }
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        poi.displayTitle,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                },
                                supportingContent = {
                                    if (poi.description.isNotBlank()) {
                                        Text(
                                            poi.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = PoiIconRegistry.getIcon(poi.iconName),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            editingPoi = poi
                                            editPoiTitle = poi.title.ifBlank { poi.displayTitle }
                                            editPoiDesc = poi.description
                                            editPoiIcon = poi.iconName
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Favorit bearbeiten",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(onClick = { viewModel.toggleFavorite(poi) }) {
                                            Icon(
                                                Icons.Default.Favorite,
                                                contentDescription = "Aus Favoriten entfernen",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Route Dialog
    editingRoute?.let { route ->
        AlertDialog(
            onDismissRequest = { editingRoute = null },
            icon = {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Route umbenennen",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column {
                    Text(
                        text = "Geben Sie der gespeicherten Route einen neuen Namen:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editRouteTitle,
                        onValueChange = { editRouteTitle = it },
                        label = { Text("Name der Route") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = editRouteTitle.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.updateRouteTitle(route, trimmed)
                        }
                        editingRoute = null
                    }
                ) {
                    Text("Speichern", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRoute = null }) {
                    Text("Abbrechen", style = MaterialTheme.typography.labelLarge)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Edit POI Dialog
    editingPoi?.let { poi ->
        AlertDialog(
            onDismissRequest = { editingPoi = null },
            icon = {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Favorit bearbeiten",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column {
                    Text(
                        text = "Passen Sie den Namen, die Notiz oder das Symbol des Favoriten an:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editPoiTitle,
                        onValueChange = { editPoiTitle = it },
                        label = { Text("Name des Favoriten") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editPoiDesc,
                        onValueChange = { editPoiDesc = it },
                        label = { Text("Notiz / Beschreibung (optional)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    IconPicker(
                        selectedIconKey = editPoiIcon,
                        onIconSelected = { editPoiIcon = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedTitle = editPoiTitle.trim()
                        val updated = poi.copy(
                            title = if (trimmedTitle.isNotBlank()) trimmedTitle else poi.displayTitle,
                            description = editPoiDesc.trim(),
                            iconName = editPoiIcon,
                            isFavorite = true
                        )
                        viewModel.updatePoi(updated)
                        editingPoi = null
                    }
                ) {
                    Text("Speichern", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPoi = null }) {
                    Text("Abbrechen", style = MaterialTheme.typography.labelLarge)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
