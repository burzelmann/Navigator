package com.example.nagivator.ui.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nagivator.data.local.entity.OfflineRegionEntity
import com.example.nagivator.data.repository.OfflineRoutingCatalog
import com.example.nagivator.data.repository.OfflineRoutingSegmentInfo
import com.example.nagivator.data.repository.RoutingTileCatalogEntry
import com.example.nagivator.data.repository.RoutingTileGroup
import com.example.nagivator.ui.map.MapLibreController
import com.example.nagivator.ui.map.MapViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OfflineManagerScreen(
    viewModel: MapViewModel,
    mapController: MapLibreController?,
    onBack: () -> Unit,
    onSwitchToMap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val userSettings by viewModel.userSettingsRepository.settings.collectAsState()
    val isOfflineMode = userSettings.offlineModeEnabled
    val allOfflineRegions by viewModel.allOfflineRegions.collectAsState()
    val searchCounts by viewModel.offlineSearchCountsByRegion.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var pendingCellularSegment by remember { mutableStateOf<String?>(null) }
    var pendingDeleteRegion by remember { mutableStateOf<OfflineRegionEntity?>(null) }
    var pendingDeleteSegment by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshOfflineCacheSize(context)
    }

    val installedByFile = remember(uiState.installedRoutingSegments) {
        uiState.installedRoutingSegments.associateBy { it.fileName }
    }
    val installedNames = installedByFile.keys
    val totalRoutingMB = uiState.installedRoutingSegments.sumOf { it.sizeMB }
    val totalMapMB = allOfflineRegions.sumOf { it.sizeMB }
    val totalSearchPlaces = searchCounts.values.sum()
    val currentAreaSegment = remember(mapController, uiState.currentLocation) {
        viewModel.getCurrentAreaRoutingSegmentName(mapController)
    }

    fun requestRoutingDownload(segmentFileName: String) {
        if (isCellularWithoutWifi(context)) {
            pendingCellularSegment = segmentFileName
        } else {
            viewModel.downloadOfflineRoutingSegment(segmentFileName)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Offline-Inhalte") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Storage Summary Card
            item(key = "storage_summary") {
                StorageOverviewCard(
                    totalMapMB = totalMapMB,
                    totalRoutingMB = totalRoutingMB,
                    totalSearchPlaces = totalSearchPlaces,
                    cacheSizeFormatted = uiState.offlineCacheSizeMB,
                    onClearCache = { viewModel.clearOfflineTileCache(context, mapController) }
                )
            }

            // 1b. Offline-Mode Banner (blocks downloads until switched online)
            if (isOfflineMode) {
                item(key = "offline_mode_banner") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Offline-Modus ist aktiv",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Für neue Downloads bitte kurz auf Online umschalten.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            FilledTonalButton(
                                onClick = { viewModel.userSettingsRepository.setOfflineModeEnabled(false) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Online")
                            }
                        }
                    }
                }
            }

            // 2. Active Map Download / Status Banner
            if (uiState.isDownloadingOfflineMap || uiState.downloadStatusMessage != null || uiState.routingDownloadStatus != null) {
                item(key = "status_banner") {
                    StatusProgressCard(
                        isDownloadingMap = uiState.isDownloadingOfflineMap,
                        mapProgress = uiState.downloadProgress,
                        mapTotal = uiState.downloadTotalTiles,
                        mapStatus = uiState.downloadStatusMessage,
                        mapEta = uiState.downloadEstimatedTimeRemaining,
                        routingStatus = if (!uiState.isDownloadingRoutingSegment) uiState.routingDownloadStatus else null,
                        onDismiss = { viewModel.dismissOfflineDownloadStatus() }
                    )
                }
            }

            // 3. Two-Tab Switcher
            item(key = "tab_switcher") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) {
                        Text("Regionen (${allOfflineRegions.size})")
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) {
                        Text("Routing (${uiState.installedRoutingSegments.size})")
                    }
                }
            }

            if (selectedTab == 0) {
                // TAB 1: MEINE REGIONEN (Karte + Suchindex + Routing-Status pro Region)
                item(key = "add_region_cta") {
                    if (mapController != null) {
                        Button(
                            onClick = { viewModel.startOfflineMapDownload(context, mapController) },
                            enabled = !uiState.isDownloadingOfflineMap && !isOfflineMode,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Aktuellen Kartenausschnitt herunterladen")
                        }
                    } else if (onSwitchToMap != null) {
                        FilledTonalButton(
                            onClick = onSwitchToMap,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kartenausschnitt auf der Karte wählen")
                        }
                    }
                }

                if (allOfflineRegions.isEmpty()) {
                    item(key = "empty_regions") {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Map,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Noch keine Offline-Regionen gespeichert",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Lade einen Kartenausschnitt herunter, um Karte und Zielsuche auch ohne Mobilfunknetz zu nutzen.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(allOfflineRegions, key = { "region_${it.id}" }) { region ->
                        val placeCount = searchCounts[region.name] ?: 0
                        val missingRouting = OfflineRoutingCatalog.missingSegmentsFor(region, installedNames)
                        val isRebuildingIndex = uiState.rebuildingSearchIndexRegion == region.name
                        RegionCard(
                            region = region,
                            searchPlaceCount = placeCount,
                            isRebuildingIndex = isRebuildingIndex,
                            missingRoutingSegments = missingRouting,
                            isDownloadingRouting = uiState.isDownloadingRoutingSegment,
                            canInteractWithMap = mapController != null,
                            onShowOnMap = {
                                viewModel.focusMapOnRegion(mapController, region)
                                onBack()
                            },
                            onRebuildIndex = { viewModel.rebuildSearchIndexForRegion(region) },
                            onDownloadMissingRouting = { seg ->
                                selectedTab = 1
                                requestRoutingDownload(seg)
                            },
                            onUpdate = {
                                mapController?.let { controller ->
                                    viewModel.updateOfflineRegion(context, controller, region)
                                }
                            },
                            onDelete = { pendingDeleteRegion = region }
                        )
                    }
                }
            } else {
                // TAB 2: ROUTING-KARTEN (BRouter 5°×5° Katalog mit Klartext-Namen)
                item(key = "current_area_header") {
                    Text(
                        text = "Aktuelle Kartenposition",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item(key = "current_area_tile") {
                    val entry = OfflineRoutingCatalog.describeSegment(currentAreaSegment)
                    val installed = installedByFile[currentAreaSegment]
                    val covered = OfflineRoutingCatalog.coveredRegionNames(currentAreaSegment, allOfflineRegions)
                    RoutingTileCard(
                        entry = entry,
                        installedInfo = installed,
                        coveredRegions = covered,
                        isDownloadingThis = uiState.downloadingRoutingSegmentName == currentAreaSegment,
                        isAnyDownloadActive = uiState.isDownloadingRoutingSegment || isOfflineMode,
                        downloadProgress = uiState.routingDownloadProgress,
                        downloadStatus = uiState.routingDownloadStatus,
                        highlighted = true,
                        onDownload = { requestRoutingDownload(currentAreaSegment) },
                        onCancelDownload = { viewModel.cancelOfflineRoutingDownload() },
                        onDelete = { pendingDeleteSegment = currentAreaSegment }
                    )
                }

                // Installed tiles outside the catalog (e.g. worldwide tiles)
                val extraInstalled = uiState.installedRoutingSegments.filter { seg ->
                    seg.fileName != currentAreaSegment && OfflineRoutingCatalog.entries.none { it.fileName == seg.fileName }
                }
                if (extraInstalled.isNotEmpty()) {
                    item(key = "extra_installed_header") {
                        Text(
                            text = "Weitere installierte Kacheln",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(extraInstalled, key = { "extra_${it.fileName}" }) { seg ->
                        val entry = OfflineRoutingCatalog.describeSegment(seg.fileName)
                        val covered = OfflineRoutingCatalog.coveredRegionNames(seg.fileName, allOfflineRegions)
                        RoutingTileCard(
                            entry = entry,
                            installedInfo = seg,
                            coveredRegions = covered,
                            isDownloadingThis = uiState.downloadingRoutingSegmentName == seg.fileName,
                            isAnyDownloadActive = uiState.isDownloadingRoutingSegment,
                            downloadProgress = uiState.routingDownloadProgress,
                            downloadStatus = uiState.routingDownloadStatus,
                            highlighted = false,
                            onDownload = { requestRoutingDownload(seg.fileName) },
                            onCancelDownload = { viewModel.cancelOfflineRoutingDownload() },
                            onDelete = { pendingDeleteSegment = seg.fileName }
                        )
                    }
                }

                // Curated Catalog grouped by DACH & Neighbors / Vacation Europe
                RoutingTileGroup.entries.forEach { group ->
                    val groupItems = OfflineRoutingCatalog.entries.filter {
                        it.group == group && it.fileName != currentAreaSegment
                    }
                    if (groupItems.isNotEmpty()) {
                        item(key = "group_${group.name}") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = group.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(groupItems, key = { "cat_${it.fileName}" }) { entry ->
                            val installed = installedByFile[entry.fileName]
                            val covered = OfflineRoutingCatalog.coveredRegionNames(entry.fileName, allOfflineRegions)
                            RoutingTileCard(
                                entry = entry,
                                installedInfo = installed,
                                coveredRegions = covered,
                                isDownloadingThis = uiState.downloadingRoutingSegmentName == entry.fileName,
                                isAnyDownloadActive = uiState.isDownloadingRoutingSegment || isOfflineMode,
                                downloadProgress = uiState.routingDownloadProgress,
                                downloadStatus = uiState.routingDownloadStatus,
                                highlighted = false,
                                onDownload = { requestRoutingDownload(entry.fileName) },
                                onCancelDownload = { viewModel.cancelOfflineRoutingDownload() },
                                onDelete = { pendingDeleteSegment = entry.fileName }
                            )
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Cellular Warning Dialog (>100 MB BRouter tile)
    pendingCellularSegment?.let { segName ->
        val desc = OfflineRoutingCatalog.describeSegment(segName)
        AlertDialog(
            onDismissRequest = { pendingCellularSegment = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Mobilfunkverbindung aktiv") },
            text = {
                Text(
                    "Die Routing-Kachel „${desc.title}“ ($segName) ist ca. 100–185 MB groß. " +
                        "Möchtest du sie wirklich über Mobilfunk herunterladen?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = segName
                    pendingCellularSegment = null
                    viewModel.downloadOfflineRoutingSegment(target)
                }) {
                    Text("Trotzdem laden")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCellularSegment = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Confirm Delete Region Dialog
    pendingDeleteRegion?.let { region ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRegion = null },
            title = { Text("Region „${region.name}“ löschen?") },
            text = {
                Text("Die Offline-Kartenkacheln und der zugehörige Offline-Suchindex dieser Region werden entfernt.")
            },
            confirmButton = {
                Button(onClick = {
                    val target = region
                    pendingDeleteRegion = null
                    viewModel.deleteOfflineRegion(context, mapController, target)
                }) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRegion = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Confirm Delete Routing Tile Dialog
    pendingDeleteSegment?.let { segName ->
        val desc = OfflineRoutingCatalog.describeSegment(segName)
        AlertDialog(
            onDismissRequest = { pendingDeleteSegment = null },
            title = { Text("Routing-Kachel löschen?") },
            text = {
                Text("„${desc.title}“ ($segName) wird vom Gerät entfernt. Ohne diese Kachel ist in dem Gebiet kein straßengenaues Offline-Routing möglich.")
            },
            confirmButton = {
                Button(onClick = {
                    val target = segName
                    pendingDeleteSegment = null
                    viewModel.deleteOfflineRoutingSegment(target)
                }) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSegment = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageOverviewCard(
    totalMapMB: Double,
    totalRoutingMB: Double,
    totalSearchPlaces: Int,
    cacheSizeFormatted: String,
    onClearCache: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Offline-Speicher",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Gesamt: %.1f MB (ohne Cache)".format(totalMapMB + totalRoutingMB),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onClearCache) {
                    Text("Cache leeren")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricPill("Karten: %.1f MB".format(totalMapMB))
                MetricPill("Routing: %.1f MB".format(totalRoutingMB))
                MetricPill("Suchindex: $totalSearchPlaces Ziele")
                MetricPill("Cache: $cacheSizeFormatted")
            }
        }
    }
}

@Composable
private fun MetricPill(label: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun StatusProgressCard(
    isDownloadingMap: Boolean,
    mapProgress: Int,
    mapTotal: Int,
    mapStatus: String?,
    mapEta: String?,
    routingStatus: String?,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = mapStatus ?: routingStatus ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (!isDownloadingMap) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen", modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (mapEta != null) {
                Text(
                    text = mapEta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (isDownloadingMap) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        if (mapTotal > 0) (mapProgress.toFloat() / mapTotal.toFloat()).coerceIn(0f, 1f) else 0f
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionCard(
    region: OfflineRegionEntity,
    searchPlaceCount: Int,
    isRebuildingIndex: Boolean,
    missingRoutingSegments: List<String>,
    isDownloadingRouting: Boolean,
    canInteractWithMap: Boolean,
    onShowOnMap: () -> Unit,
    onRebuildIndex: () -> Unit,
    onDownloadMissingRouting: (String) -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(region.downloadedAt) {
        SimpleDateFormat("dd. MMM yyyy", Locale.GERMANY).format(Date(region.downloadedAt))
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = region.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "%.1f MB • ${region.tileCount} Kacheln • $dateStr".format(region.sizeMB),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    if (canInteractWithMap) {
                        IconButton(onClick = onShowOnMap) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Auf Karte zeigen", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onUpdate) {
                            Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Map badge
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Karte bereit") },
                    leadingIcon = {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                        disabledLeadingIconContentColor = MaterialTheme.colorScheme.primary
                    )
                )

                // 2. Search Index badge
                if (isRebuildingIndex) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Indiziere...") },
                        leadingIcon = { CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp) }
                    )
                } else if (searchPlaceCount > 0) {
                    AssistChip(
                        onClick = onRebuildIndex,
                        label = { Text("$searchPlaceCount Offline-Ziele") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                } else {
                    AssistChip(
                        onClick = onRebuildIndex,
                        label = { Text("Suchindex laden") },
                        leadingIcon = {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }

                // 3. Offline Routing badge
                if (missingRoutingSegments.isEmpty()) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Offline-Routing bereit") },
                        leadingIcon = {
                            Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                            disabledLeadingIconContentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                } else {
                    val firstMissing = missingRoutingSegments.first()
                    AssistChip(
                        onClick = {
                            if (!isDownloadingRouting) onDownloadMissingRouting(firstMissing)
                        },
                        label = { Text("Routing laden (${firstMissing.removeSuffix(".rd5")})") },
                        leadingIcon = {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutingTileCard(
    entry: RoutingTileCatalogEntry,
    installedInfo: OfflineRoutingSegmentInfo?,
    coveredRegions: List<String>,
    isDownloadingThis: Boolean,
    isAnyDownloadActive: Boolean,
    downloadProgress: Float,
    downloadStatus: String?,
    highlighted: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val meta = buildString {
                        append(entry.fileName)
                        if (installedInfo != null) {
                            append(" • %.1f MB".format(installedInfo.sizeMB))
                        }
                        if (coveredRegions.isNotEmpty()) {
                            append(" • Deckt ab: ")
                            append(coveredRegions.joinToString(", "))
                        }
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (installedInfo != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when {
                    isDownloadingThis -> {
                        IconButton(onClick = onCancelDownload) {
                            Icon(Icons.Default.Close, contentDescription = "Download abbrechen", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    installedInfo != null -> {
                        Row {
                            IconButton(
                                onClick = onDownload,
                                enabled = !isAnyDownloadActive
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Kachel aktualisieren", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(
                                onClick = onDelete,
                                enabled = !isAnyDownloadActive
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Kachel löschen", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    else -> {
                        FilledTonalButton(
                            onClick = onDownload,
                            enabled = !isAnyDownloadActive,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Laden")
                        }
                    }
                }
            }

            if (isDownloadingThis) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = downloadStatus ?: "Lade ${entry.fileName}...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun isCellularWithoutWifi(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
        !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
