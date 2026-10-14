package com.example.nagivator.ui.map

import android.Manifest
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Refresh
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import org.osmdroid.views.MapView
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nagivator.data.local.entity.PoiEntity
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.ui.components.IconPicker

@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(),
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val pois by viewModel.pois.collectAsState()
    val allOfflineRegions by viewModel.allOfflineRegions.collectAsState()
    val azimuthDegrees by viewModel.azimuthDegrees.collectAsState()
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Dialog state for route saving & offline manager
    var showSaveRouteDialog by remember { mutableStateOf(false) }
    var saveRouteTitleInput by remember { mutableStateOf("") }
    var showOfflineManagerDialog by remember { mutableStateOf(false) }
    var regionNameInput by remember { mutableStateOf("") }

    // Dialog state for adding a new POI candidate
    var poiTitleInput by remember { mutableStateOf("") }
    var poiDescInput by remember { mutableStateOf("") }
    var poiIconInput by remember { mutableStateOf("place") }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.startLocationUpdates()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Keep screen awake during active turn-by-turn navigation
    DisposableEffect(uiState.isNavigating) {
        val activity = context as? Activity
        if (uiState.isNavigating) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Map View
        OsmMapView(
            modifier = Modifier.fillMaxSize(),
            currentLocation = uiState.currentLocation,
            azimuthDegrees = azimuthDegrees,
            isDarkTheme = isDarkTheme,
            isNavigating = uiState.isNavigating,
            isOverviewMode = uiState.isOverviewMode,
            centerOnLocationTrigger = uiState.centerOnLocationTrigger,
            pois = pois,
            activeRoute = uiState.activeRoute,
            selectedPoi = uiState.selectedPoi,
            onPoiClick = { poi -> 
                if (uiState.isRoutePlanningMode) {
                    viewModel.addWaypoint(LocationPoint(poi.latitude, poi.longitude, poi.displayTitle))
                } else {
                    viewModel.selectPoi(poi) 
                }
            },
            onMapClick = { geoPoint ->
                if (uiState.isRoutePlanningMode) {
                    viewModel.addWaypoint(LocationPoint(geoPoint.latitude, geoPoint.longitude, "Wegpunkt"))
                } else {
                    viewModel.onMapSingleTap(geoPoint.latitude, geoPoint.longitude)
                }
            },
            onMapLongClick = { geoPoint ->
                viewModel.onMapLongClick(geoPoint.latitude, geoPoint.longitude)
            },
            onMapReady = { mapView ->
                mapViewInstance = mapView
            }
        )

        if (uiState.isNavigating && uiState.activeRoute != null) {
            // Fullscreen Dedicated Navigation View
            NavigationView(
                route = uiState.activeRoute!!,
                currentStepIndex = uiState.currentRouteStepIndex,
                isMuted = uiState.isMuted,
                isOverviewMode = uiState.isOverviewMode,
                mapOrientation = mapViewInstance?.mapOrientation ?: 0f,
                bottomPadding = bottomPadding,
                onToggleMute = { viewModel.toggleMute() },
                onToggleOverviewMode = { viewModel.toggleOverviewMode() },
                onResetNorth = {
                    mapViewInstance?.let { mapView ->
                        mapView.mapOrientation = 0f
                        mapView.invalidate()
                    }
                },
                onStopNavigation = { viewModel.stopNavigation() }
            )
        } else {
            // Standard Map Search Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.length >= 3) {
                                viewModel.search(it)
                                isSearchActive = true
                            } else {
                                isSearchActive = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Ort oder Adresse suchen...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Suchen",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (uiState.isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .height(20.dp)
                                        .width(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    isSearchActive = false
                                    viewModel.search("")
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Löschen",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }

                // Search Results Dropdown
                if (isSearchActive && uiState.searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        shadowElevation = 10.dp
                    ) {
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(uiState.searchResults) { point ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            point.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    },
                                    supportingContent = {
                                        if (point.address.isNotBlank()) {
                                            Text(
                                                point.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingContent = {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.clickable {
                                        if (uiState.isRoutePlanningMode) {
                                            viewModel.addWaypoint(LocationPoint(point.latitude, point.longitude, point.name))
                                        } else {
                                            val dummyPoi = PoiEntity(
                                                title = point.name,
                                                description = point.address,
                                                latitude = point.latitude,
                                                longitude = point.longitude
                                            )
                                            viewModel.selectPoi(dummyPoi)
                                        }
                                        isSearchActive = false
                                        searchQuery = ""
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Location Center & Offline Download FABs
            val fabBottomPadding = when {
                uiState.isRoutePlanningMode && uiState.isRoutePlannerMinimized -> 100.dp
                uiState.isRoutePlanningMode -> 380.dp
                uiState.activeRoute != null || uiState.selectedPoi != null -> 220.dp
                else -> 24.dp
            }
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = bottomPadding + fabBottomPadding, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        mapViewInstance?.let { mapView ->
                            mapView.mapOrientation = 0f
                            mapView.invalidate()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Karte nach Norden ausrichten",
                        modifier = Modifier.rotate(-(mapViewInstance?.mapOrientation ?: 0f))
                    )
                }

                FloatingActionButton(
                    onClick = {
                        viewModel.refreshOfflineCacheSize(context)
                        showOfflineManagerDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = "Offline-Karten & Speicher")
                }

                FloatingActionButton(
                    onClick = { viewModel.centerOnCurrentLocation() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Mein Standort")
                }
            }

            // 4. POI Details Popup OR Route Card Overlay at Bottom
            if (uiState.isRoutePlanningMode) {
                RoutePlannerCard(
                    waypoints = uiState.plannedWaypoints,
                    route = uiState.activeRoute,
                    currentTravelMode = uiState.travelMode,
                    isCalculatingRoute = uiState.isCalculatingRoute,
                    isMinimized = uiState.isRoutePlannerMinimized,
                    isSavedRoute = uiState.loadedSavedRoute != null,
                    onModeChange = { viewModel.setTravelMode(it) },
                    onRemoveWaypoint = { viewModel.removeWaypoint(it) },
                    onMoveWaypoint = { from, to -> viewModel.moveWaypoint(from, to) },
                    onStartNavigation = { viewModel.startNavigation() },
                    onSaveRoute = { showSaveRouteDialog = true },
                    onDeleteSavedRoute = { viewModel.deleteLoadedSavedRoute() },
                    onClosePlanner = { viewModel.exitRoutePlanningMode() },
                    onToggleMinimize = { viewModel.toggleRoutePlannerMinimized() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding)
                )
            } else if (uiState.activeRoute != null) {
                RouteCard(
                    route = uiState.activeRoute!!,
                    selectedPoi = uiState.selectedPoi,
                    currentTravelMode = uiState.travelMode,
                    isNavigating = uiState.isNavigating,
                    currentStepIndex = uiState.currentRouteStepIndex,
                    onModeChange = { mode ->
                        uiState.selectedPoi?.let { poi ->
                            viewModel.startRouteToPoi(poi, mode)
                        }
                    },
                    onStartNavigation = { viewModel.startNavigation() },
                    onStopNavigation = { viewModel.stopNavigation() },
                    onToggleFavorite = { poi -> viewModel.toggleFavorite(poi) },
                    onDeletePoi = { poi -> viewModel.deletePoi(poi) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding)
                )
            } else if (uiState.selectedPoi != null) {
                PoiDetailsCard(
                    poi = uiState.selectedPoi!!,
                    isCalculatingRoute = uiState.isCalculatingRoute,
                    onStartRoute = { mode ->
                        viewModel.startRouteToPoi(uiState.selectedPoi!!, mode)
                    },
                    onStartRoutePlanner = { viewModel.enterRoutePlanningMode() },
                    onClose = { viewModel.selectPoi(null) },
                    onToggleFavorite = { poi -> viewModel.toggleFavorite(poi) },
                    onDeletePoi = { poi -> viewModel.deletePoi(poi) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding)
                )
            }
        }

        // 5. New POI Candidate Dialog
        uiState.newPoiCandidate?.let { candidate ->
            AlertDialog(
                onDismissRequest = { viewModel.saveCandidateAsPoi("", "") },
                icon = {
                    Icon(
                        Icons.Default.AddLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        "Neuen Ort speichern",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    Column {
                        Text(
                            "Möchten Sie diesen Standort als Favorit/POI speichern?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = poiTitleInput,
                            onValueChange = { poiTitleInput = it },
                            label = { Text("Name / Titel") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = poiDescInput,
                            onValueChange = { poiDescInput = it },
                            label = { Text("Beschreibung (optional)") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        IconPicker(
                            selectedIconKey = poiIconInput,
                            onIconSelected = { poiIconInput = it }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.saveCandidateAsPoi(poiTitleInput, poiDescInput, poiIconInput)
                            poiTitleInput = ""
                            poiDescInput = ""
                            poiIconInput = "place"
                        }
                    ) {
                        Text("Speichern", style = MaterialTheme.typography.labelLarge)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.saveCandidateAsPoi("", "", "place")
                        poiIconInput = "place"
                    }) {
                        Text("Abbrechen", style = MaterialTheme.typography.labelLarge)
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // 6. Save Route Dialog
        if (showSaveRouteDialog && uiState.activeRoute != null) {
            AlertDialog(
                onDismissRequest = { showSaveRouteDialog = false },
                title = { Text("Route in Favoriten speichern", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = saveRouteTitleInput,
                            onValueChange = { saveRouteTitleInput = it },
                            label = { Text("Titel der Route") },
                            placeholder = { Text("z.B. Waldspaziergang oder Fahrradtour") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.saveActiveRoute(saveRouteTitleInput)
                            saveRouteTitleInput = ""
                            showSaveRouteDialog = false
                        }
                    ) {
                        Text("Speichern", style = MaterialTheme.typography.labelLarge)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveRouteDialog = false }) {
                        Text("Abbrechen", style = MaterialTheme.typography.labelLarge)
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // 7. Offline Map Storage & Management Dialog
        if (showOfflineManagerDialog || uiState.downloadStatusMessage != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!uiState.isDownloadingOfflineMap) {
                        showOfflineManagerDialog = false
                        viewModel.dismissOfflineDownloadStatus()
                    }
                },
                icon = {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Offline-Karten & Speicher", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Belegter Karten-Speicher:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = uiState.offlineCacheSizeMB,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (uiState.downloadStatusMessage != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = uiState.downloadStatusMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.downloadEstimatedTimeRemaining != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = uiState.downloadEstimatedTimeRemaining!!,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (uiState.isDownloadingOfflineMap) {
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (uiState.downloadTotalTiles > 0) {
                                            (uiState.downloadProgress.toFloat() / uiState.downloadTotalTiles.toFloat()).coerceIn(0f, 1f)
                                        } else 0f
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (!uiState.isDownloadingOfflineMap) {
                            if (allOfflineRegions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Gespeicherte Regionen (${allOfflineRegions.size}):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 160.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(allOfflineRegions) { region ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            ListItem(
                                                headlineContent = {
                                                    Text(region.name, style = MaterialTheme.typography.titleMedium)
                                                },
                                                supportingContent = {
                                                    val dateStr = SimpleDateFormat("dd. MMM", Locale.GERMANY).format(Date(region.downloadedAt))
                                                    Text("%.1f MB • ${region.tileCount} Kacheln • $dateStr".format(region.sizeMB), style = MaterialTheme.typography.bodySmall)
                                                },
                                                trailingContent = {
                                                    Row {
                                                        IconButton(onClick = {
                                                            mapViewInstance?.let { mapView ->
                                                                viewModel.updateOfflineRegion(context, mapView, region)
                                                            }
                                                        }) {
                                                            Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren", tint = MaterialTheme.colorScheme.primary)
                                                        }
                                                        IconButton(onClick = {
                                                            viewModel.deleteOfflineRegion(context, mapViewInstance, region)
                                                        }) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                                                        }
                                                    }
                                                },
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    mapViewInstance?.let { mapView ->
                                        viewModel.startOfflineMapDownload(context, mapView)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ausschnitt herunterladen")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    viewModel.clearOfflineTileCache(context, mapViewInstance)
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Karten-Cache leeren")
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!uiState.isDownloadingOfflineMap) {
                        TextButton(
                            onClick = {
                                showOfflineManagerDialog = false
                                viewModel.dismissOfflineDownloadStatus()
                            }
                        ) {
                            Text("Schließen", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // 8. Pre-Download Confirmation & Size Estimate Dialog
        uiState.pendingDownloadEstimate?.let { estimate ->
            LaunchedEffect(estimate) {
                regionNameInput = estimate.suggestedName
            }
            AlertDialog(
                onDismissRequest = { viewModel.dismissDownloadEstimate() },
                icon = {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                title = { Text("Kartenbereich herunterladen?", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = regionNameInput,
                            onValueChange = { regionNameInput = it },
                            label = { Text("Name der Region") },
                            placeholder = { Text("z.B. Düsseldorf oder Berlin") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Der gewählte Bildschirmausschnitt wird für alle tieferen Zoomstufen offline gespeichert.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Anzahl Kacheln: ${estimate.totalTiles}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Geschätzte Größe: ca. %.1f MB".format(estimate.estimatedSizeMB), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Zoomstufen: ${estimate.minZoom} bis ${estimate.maxZoom}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            mapViewInstance?.let { mapView ->
                                viewModel.confirmOfflineMapDownload(context, mapView, regionNameInput)
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Jetzt herunterladen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDownloadEstimate() }) {
                        Text("Abbrechen", style = MaterialTheme.typography.labelLarge)
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}
