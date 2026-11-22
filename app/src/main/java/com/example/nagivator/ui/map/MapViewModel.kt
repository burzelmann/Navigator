package com.example.nagivator.ui.map

import android.app.Application
import android.content.Context
import android.location.Location
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nagivator.data.local.NagivatorDatabase
import com.example.nagivator.data.local.entity.OfflineRegionEntity
import com.example.nagivator.data.local.entity.PoiEntity
import com.example.nagivator.data.local.entity.SavedRouteEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.nagivator.data.repository.LocationRepository
import com.example.nagivator.data.repository.OrientationRepository
import com.example.nagivator.data.repository.PoiRepository
import com.example.nagivator.data.repository.RoutingRepository
import com.example.nagivator.data.repository.TripHistoryRepository
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TravelMode
import com.example.nagivator.domain.NavigationStateHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque

data class DownloadEstimate(
    val suggestedName: String,
    val totalTiles: Int,
    val estimatedSizeMB: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

data class MapUiState(
    val currentLocation: LocationPoint? = null,
    val selectedPoi: PoiEntity? = null,
    val activeRoute: RouteResult? = null,
    val alternativeRoutes: List<RouteResult> = emptyList(),
    val travelMode: TravelMode = TravelMode.FOOT,
    val searchResults: List<LocationPoint> = emptyList(),
    val isSearching: Boolean = false,
    val isCalculatingRoute: Boolean = false,
    val routeError: String? = null,
    val isNavigating: Boolean = false,
    val newPoiCandidate: LocationPoint? = null,
    val centerOnLocationTrigger: Long = 0L,
    
    // Route Planning Mode (Multi-Waypoint)
    val isRoutePlanningMode: Boolean = false,
    val isRoutePlannerMinimized: Boolean = false,
    val plannedWaypoints: List<LocationPoint> = emptyList(),
    val loadedSavedRoute: SavedRouteEntity? = null,
    
    // Dynamic Navigation
    val currentRouteStepIndex: Int = 0,
    val isMuted: Boolean = false,
    val isOverviewMode: Boolean = false,
    
    // Offline Map Caching State
    val isDownloadingOfflineMap: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadTotalTiles: Int = 0,
    val downloadStatusMessage: String? = null,
    val downloadEstimatedTimeRemaining: String? = null,
    val offlineCacheSizeMB: String = "0 MB",
    val pendingDownloadEstimate: DownloadEstimate? = null
)

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val db = NagivatorDatabase.getDatabase(application)
    private val poiRepository = PoiRepository(db.poiDao(), db.savedRouteDao(), db.offlineRegionDao())
    private val tripHistoryRepository = TripHistoryRepository(db.tripHistoryDao())
    private val routingRepository = RoutingRepository()
    private val locationRepository = LocationRepository(application)
    private val orientationRepository = OrientationRepository(application)
    val userSettingsRepository = UserSettingsRepository.getInstance(application)

    val azimuthDegrees: StateFlow<Float> = orientationRepository.getOrientationUpdates().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0f
    )

    val allOfflineRegions: StateFlow<List<OfflineRegionEntity>> = poiRepository.allOfflineRegions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _uiState = MutableStateFlow(
        MapUiState(currentLocation = locationRepository.getLastSavedLocation())
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    val pois: StateFlow<List<PoiEntity>> = poiRepository.allPois.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            poiRepository.allPois.collect { allDbPois ->
                val currentSelected = _uiState.value.selectedPoi
                if (currentSelected != null && currentSelected.id > 0L) {
                    if (allDbPois.none { it.id == currentSelected.id }) {
                        _uiState.update { it.copy(selectedPoi = null, activeRoute = null, isNavigating = false) }
                    }
                }
            }
        }
        viewModelScope.launch {
            var lastFootSpeed = userSettingsRepository.settings.value.footSpeed
            var lastCarSpeed = userSettingsRepository.settings.value.carSpeed
            userSettingsRepository.settings.collect { settings ->
                if (_uiState.value.isMuted != settings.voiceMuted) {
                    _uiState.update { it.copy(isMuted = settings.voiceMuted) }
                }
                if (settings.footSpeed != lastFootSpeed || settings.carSpeed != lastCarSpeed) {
                    lastFootSpeed = settings.footSpeed
                    lastCarSpeed = settings.carSpeed
                    if (_uiState.value.isRoutePlanningMode && _uiState.value.plannedWaypoints.size >= 2) {
                        calculatePlannedRoute()
                    } else if (_uiState.value.selectedPoi != null) {
                        _uiState.value.selectedPoi?.let { calculateRouteTo(it) }
                    }
                }
            }
        }
        viewModelScope.launch {
            NavigationStateHolder.isNavigating.collect { isNavigating ->
                if (_uiState.value.isNavigating != isNavigating) {
                    val autoRoute = NavigationStateHolder.activeRoute.value
                    _uiState.update {
                        it.copy(
                            isNavigating = isNavigating,
                            activeRoute = autoRoute ?: it.activeRoute
                        )
                    }
                }
            }
        }
    }

    private var locationJob: Job? = null

    fun startLocationUpdates() {
        if (locationJob != null) return
        locationJob = viewModelScope.launch {
            locationRepository.getLocationUpdates()
                .catch { /* ignore location errors */ }
                .collect { location ->
                    val isFirstFix = _uiState.value.currentLocation == null
                    _uiState.update { it.copy(currentLocation = location) }
                    NavigationStateHolder.updateLocation(location)
                    
                    if (isFirstFix) {
                        centerOnCurrentLocation()
                    }
                    
                    checkNavigationProgress(location)
                }
        }
    }
    
    private var isRecalculatingOffRoute = false

    private fun checkNavigationProgress(currentLoc: LocationPoint) {
        val state = _uiState.value
        if (!state.isNavigating || state.activeRoute == null) return

        val route = state.activeRoute
        val steps = route.steps
        val currentIndex = state.currentRouteStepIndex

        // 1. Check distance to current route geometry (Off-Route Detection & Trailing Polyline Trimming)
        if (route.geometryPoints.isNotEmpty()) {
            val (minDist, closestIdx) = minDistanceAndIndex(currentLoc, route.geometryPoints)
            val offRouteThreshold = if (state.travelMode == TravelMode.FOOT) 35f else 50f

            if (minDist > offRouteThreshold && !isRecalculatingOffRoute) {
                // User has deviated from route -> Trigger automatic recalculation from current live position!
                recalculateRouteOffRoute(currentLoc, route)
                return
            } else if (closestIdx > 0 && closestIdx < route.geometryPoints.size - 1) {
                // Trim already driven/walked polyline points behind the user
                val trimmedPoints = route.geometryPoints.subList(closestIdx, route.geometryPoints.size)
                if (trimmedPoints.size >= 2 && trimmedPoints.size != route.geometryPoints.size) {
                    _uiState.update {
                        it.copy(
                            activeRoute = route.copy(geometryPoints = trimmedPoints)
                        )
                    }
                }
            }
        }

        // 2. Scan ahead for maneuver step progression (handles step completion and rejoining route ahead)
        if (steps.isNotEmpty() && currentIndex < steps.size) {
            val results = FloatArray(1)
            for (i in currentIndex until steps.size) {
                val maneuverLoc = steps[i].maneuverLocation ?: continue
                Location.distanceBetween(
                    currentLoc.latitude, currentLoc.longitude,
                    maneuverLoc.latitude, maneuverLoc.longitude,
                    results
                )
                if (results[0] < 35f) {
                    val nextIdx = (i + 1).coerceAtMost(steps.size - 1)
                    if (nextIdx != currentIndex) {
                        _uiState.update { it.copy(currentRouteStepIndex = nextIdx) }
                    }
                    break
                }
            }
        }
    }

    private fun minDistanceAndIndex(loc: LocationPoint, points: List<LocationPoint>): Pair<Float, Int> {
        var min = Float.MAX_VALUE
        var minIdx = 0
        val res = FloatArray(1)
        for (i in points.indices) {
            val p = points[i]
            Location.distanceBetween(loc.latitude, loc.longitude, p.latitude, p.longitude, res)
            if (res[0] < min) {
                min = res[0]
                minIdx = i
            }
        }
        return Pair(min, minIdx)
    }

    private fun recalculateRouteOffRoute(currentLoc: LocationPoint, currentRoute: RouteResult) {
        if (currentRoute.waypoints.isEmpty()) return
        isRecalculatingOffRoute = true

        viewModelScope.launch {
            try {
                // Keep the final destination, update starting point to current live GPS position
                val updatedWaypoints = currentRoute.waypoints.toMutableList()
                updatedWaypoints[0] = currentLoc.copy(name = "Mein Standort")

                val mode = _uiState.value.travelMode
                val footPref = userSettingsRepository.settings.value.footSpeed
                val carPref = userSettingsRepository.settings.value.carSpeed

                val result = routingRepository.getRoute(updatedWaypoints, mode, footPref, carPref)
                result.onSuccess { newRoute ->
                    _uiState.update {
                        it.copy(
                            activeRoute = newRoute,
                            currentRouteStepIndex = 0
                        )
                    }
                }
            } catch (_: Exception) {
            } finally {
                isRecalculatingOffRoute = false
            }
        }
    }

    fun centerOnCurrentLocation() {
        startLocationUpdates()
        _uiState.update { it.copy(centerOnLocationTrigger = System.currentTimeMillis()) }
    }

    fun selectPoi(poi: PoiEntity?) {
        _uiState.update {
            it.copy(
                selectedPoi = poi,
                activeRoute = null,
                isNavigating = false,
                newPoiCandidate = null
            )
        }
    }

    fun enterRoutePlanningMode() {
        val currentLoc = _uiState.value.currentLocation
            ?: LocationPoint(latitude = 52.5200, longitude = 13.4050, name = "Start")
        
        val initialWaypoints = mutableListOf(currentLoc)
        
        _uiState.value.selectedPoi?.let { poi ->
            initialWaypoints.add(LocationPoint(poi.latitude, poi.longitude, poi.displayTitle))
        }

        _uiState.update { 
            it.copy(
                isRoutePlanningMode = true,
                isRoutePlannerMinimized = false,
                plannedWaypoints = initialWaypoints,
                loadedSavedRoute = null,
                selectedPoi = null
            )
        }
        
        if (initialWaypoints.size >= 2) {
            calculatePlannedRoute()
        }
    }

    fun toggleRoutePlannerMinimized() {
        _uiState.update { it.copy(isRoutePlannerMinimized = !it.isRoutePlannerMinimized) }
    }

    fun exitRoutePlanningMode() {
        _uiState.update {
            it.copy(
                isRoutePlanningMode = false,
                isRoutePlannerMinimized = false,
                plannedWaypoints = emptyList(),
                loadedSavedRoute = null,
                activeRoute = null,
                isNavigating = false
            )
        }
    }

    fun addWaypoint(point: LocationPoint) {
        val currentWaypoints = _uiState.value.plannedWaypoints.toMutableList()
        if (currentWaypoints.size >= 2) {
            // Insert intermediate waypoint before the final destination
            currentWaypoints.add(currentWaypoints.size - 1, point)
        } else {
            currentWaypoints.add(point)
        }
        _uiState.update { it.copy(plannedWaypoints = currentWaypoints) }
        calculatePlannedRoute()
    }

    fun moveWaypoint(fromIndex: Int, toIndex: Int) {
        val currentWaypoints = _uiState.value.plannedWaypoints.toMutableList()
        if (fromIndex in currentWaypoints.indices && toIndex in currentWaypoints.indices && fromIndex != toIndex) {
            val item = currentWaypoints.removeAt(fromIndex)
            currentWaypoints.add(toIndex, item)
            _uiState.update { it.copy(plannedWaypoints = currentWaypoints) }
            calculatePlannedRoute()
        }
    }

    fun removeWaypoint(index: Int) {
        val currentWaypoints = _uiState.value.plannedWaypoints.toMutableList()
        if (index in currentWaypoints.indices) {
            currentWaypoints.removeAt(index)
            _uiState.update { it.copy(plannedWaypoints = currentWaypoints) }
            if (currentWaypoints.size >= 2) {
                calculatePlannedRoute()
            } else {
                _uiState.update { it.copy(activeRoute = null) }
            }
        }
    }

    private fun calculatePlannedRoute() {
        val waypoints = _uiState.value.plannedWaypoints
        if (waypoints.size < 2) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCalculatingRoute = true, routeError = null) }
            val mode = _uiState.value.travelMode
            val footPref = userSettingsRepository.settings.value.footSpeed
            val carPref = userSettingsRepository.settings.value.carSpeed
            val result = routingRepository.getRoute(waypoints, mode, footPref, carPref)
            result.onSuccess { route ->
                val loadedRoute = _uiState.value.loadedSavedRoute
                var updatedLoadedRoute: SavedRouteEntity? = loadedRoute
                if (loadedRoute != null) {
                    val waypointsJson = Json.encodeToString(route.waypoints)
                    val updated = loadedRoute.copy(
                        travelMode = route.mode.name,
                        distanceMeters = route.totalDistanceMeters,
                        durationSeconds = route.totalDurationSeconds,
                        waypointsJson = waypointsJson
                    )
                    poiRepository.saveRoute(updated)
                    updatedLoadedRoute = updated
                }

                _uiState.update {
                    it.copy(
                        activeRoute = route,
                        loadedSavedRoute = updatedLoadedRoute,
                        isCalculatingRoute = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCalculatingRoute = false,
                        routeError = error.localizedMessage ?: "Routenberechnung fehlgeschlagen"
                    )
                }
            }
        }
    }

    fun setTravelMode(mode: TravelMode) {
        _uiState.update { it.copy(travelMode = mode) }
        if (_uiState.value.isRoutePlanningMode && _uiState.value.plannedWaypoints.size >= 2) {
            calculatePlannedRoute()
        } else {
            _uiState.value.selectedPoi?.let { calculateRouteTo(it) }
        }
    }

    fun startRouteToPoi(poi: PoiEntity, mode: TravelMode) {
        _uiState.update { it.copy(travelMode = mode) }
        calculateRouteTo(poi, mode)
    }

    fun calculateRouteTo(poi: PoiEntity, mode: TravelMode = _uiState.value.travelMode) {
        val currentLoc = _uiState.value.currentLocation
            ?: LocationPoint(latitude = 52.5200, longitude = 13.4050, name = "Start")
        val dest = LocationPoint(
            latitude = poi.latitude,
            longitude = poi.longitude,
            name = poi.displayTitle
        )
        calculateRoute(currentLoc, dest, mode)
    }

    fun calculateRoute(start: LocationPoint, destination: LocationPoint, mode: TravelMode = _uiState.value.travelMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCalculatingRoute = true, routeError = null, travelMode = mode) }
            val footPref = userSettingsRepository.settings.value.footSpeed
            val carPref = userSettingsRepository.settings.value.carSpeed
            val result = routingRepository.getRouteAlternatives(listOf(start, destination), mode, footPref, carPref)
            result.onSuccess { routes ->
                _uiState.update {
                    it.copy(
                        activeRoute = routes.firstOrNull(),
                        alternativeRoutes = routes,
                        isCalculatingRoute = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCalculatingRoute = false,
                        alternativeRoutes = emptyList(),
                        routeError = error.localizedMessage ?: "Routenberechnung fehlgeschlagen"
                    )
                }
            }
        }
    }

    fun selectAlternativeRoute(route: RouteResult) {
        _uiState.update { it.copy(activeRoute = route) }
    }

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank() || query.length < 3) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(350)
            val result = routingRepository.searchPlaces(query)
            result.onSuccess { places ->
                _uiState.update { it.copy(searchResults = places, isSearching = false) }
            }.onFailure {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            }
        }
    }

    fun onMapSingleTap(lat: Double, lon: Double) {
        viewModelScope.launch {
            val result = routingRepository.reverseGeocode(lat, lon)
            result.onSuccess { location ->
                val tempPoi = PoiEntity(
                    title = location.name,
                    description = location.address,
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                _uiState.update {
                    it.copy(
                        selectedPoi = tempPoi,
                        activeRoute = null,
                        isNavigating = false
                    )
                }
            }
        }
    }

    fun onMapLongClick(lat: Double, lon: Double) {
        val point = LocationPoint(
            latitude = lat,
            longitude = lon,
            name = "Neuer Ort"
        )
        _uiState.update { it.copy(newPoiCandidate = point) }
    }

    fun dismissNewPoiCandidate() {
        _uiState.update { it.copy(newPoiCandidate = null) }
    }

    fun saveCandidateAsPoi(title: String, description: String = "", iconName: String = "place", isFavorite: Boolean = true) {
        val candidate = _uiState.value.newPoiCandidate ?: return
        val fallbackTitle = title.ifBlank {
            if (description.isNotBlank()) description
            else "Markierter Ort (%.3f, %.3f)".format(candidate.latitude, candidate.longitude)
        }
        viewModelScope.launch {
            val savedId = poiRepository.savePoi(
                PoiEntity(
                    title = fallbackTitle,
                    description = description,
                    latitude = candidate.latitude,
                    longitude = candidate.longitude,
                    iconName = iconName,
                    isFavorite = isFavorite
                )
            )
            _uiState.update { it.copy(newPoiCandidate = null) }
        }
    }

    fun savePoiAsFavorite(poi: PoiEntity, customTitle: String, description: String = "", iconName: String = "place") {
        val fallbackTitle = customTitle.ifBlank { poi.displayTitle }
        viewModelScope.launch {
            val updated = poi.copy(
                title = fallbackTitle,
                description = description,
                iconName = iconName,
                isFavorite = true
            )
            val savedId = poiRepository.savePoi(updated)
            val finalPoi = updated.copy(id = if (poi.id == 0L) savedId else poi.id)
            _uiState.update { state ->
                state.copy(selectedPoi = finalPoi)
            }
        }
    }

    fun updatePoi(poi: PoiEntity) {
        viewModelScope.launch {
            val savedId = poiRepository.savePoi(poi)
            val finalPoi = poi.copy(id = if (poi.id == 0L) savedId else poi.id)
            _uiState.update { state ->
                if (state.selectedPoi?.id == poi.id ||
                    (poi.id == 0L && state.selectedPoi?.latitude == poi.latitude && state.selectedPoi?.longitude == poi.longitude)) {
                    state.copy(selectedPoi = finalPoi)
                } else {
                    state
                }
            }
        }
    }

    fun toggleFavorite(poi: PoiEntity) {
        viewModelScope.launch {
            val isNowFavorite = !poi.isFavorite
            
            val savedPoiId = if (poi.id == 0L) {
                poiRepository.savePoi(poi.copy(isFavorite = isNowFavorite))
            } else {
                poiRepository.toggleFavorite(poi)
                poi.id
            }
            
            _uiState.update { state -> 
                if (state.selectedPoi?.id == poi.id || (poi.id == 0L && state.selectedPoi?.latitude == poi.latitude)) {
                    state.copy(selectedPoi = poi.copy(id = savedPoiId, isFavorite = isNowFavorite))
                } else {
                    state
                }
            }
        }
    }

    fun deletePoi(poi: PoiEntity) {
        viewModelScope.launch {
            if (poi.id > 0L) {
                poiRepository.deletePoi(poi)
            }
            _uiState.update {
                it.copy(
                    selectedPoi = null,
                    activeRoute = null,
                    isNavigating = false
                )
            }
        }
    }

    fun saveActiveRoute(title: String) {
        val activeRoute = _uiState.value.activeRoute ?: return
        viewModelScope.launch {
            val waypointsJson = Json.encodeToString(activeRoute.waypoints)
            val newId = poiRepository.saveRoute(
                SavedRouteEntity(
                    title = title.ifBlank { "Meine Route" },
                    travelMode = activeRoute.mode.name,
                    distanceMeters = activeRoute.totalDistanceMeters,
                    durationSeconds = activeRoute.totalDurationSeconds,
                    waypointsJson = waypointsJson
                )
            )
            _uiState.update { 
                it.copy(
                    loadedSavedRoute = SavedRouteEntity(
                        id = newId,
                        title = title.ifBlank { "Meine Route" },
                        travelMode = activeRoute.mode.name,
                        distanceMeters = activeRoute.totalDistanceMeters,
                        durationSeconds = activeRoute.totalDurationSeconds,
                        waypointsJson = waypointsJson
                    )
                ) 
            }
        }
    }

    fun loadSavedRouteEntity(routeEntity: SavedRouteEntity) {
        viewModelScope.launch {
            try {
                val waypoints = Json.decodeFromString<List<LocationPoint>>(routeEntity.waypointsJson)
                if (waypoints.size >= 2) {
                    val mode = TravelMode.valueOf(routeEntity.travelMode)
                    _uiState.update { 
                        it.copy(
                            isRoutePlanningMode = true,
                            isRoutePlannerMinimized = false,
                            plannedWaypoints = waypoints,
                            loadedSavedRoute = routeEntity,
                            travelMode = mode,
                            isCalculatingRoute = true,
                            routeError = null
                        ) 
                    }
                    val footPref = userSettingsRepository.settings.value.footSpeed
                    val carPref = userSettingsRepository.settings.value.carSpeed
                    val result = routingRepository.getRoute(waypoints, mode, footPref, carPref)
                    result.onSuccess { route ->
                        _uiState.update {
                            it.copy(
                                activeRoute = route,
                                isCalculatingRoute = false
                            )
                        }
                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isCalculatingRoute = false,
                                routeError = error.localizedMessage ?: "Routenberechnung fehlgeschlagen"
                            )
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun deleteLoadedSavedRoute() {
        val loaded = _uiState.value.loadedSavedRoute ?: return
        viewModelScope.launch {
            poiRepository.deleteRoute(loaded)
            _uiState.update {
                it.copy(
                    loadedSavedRoute = null,
                    isRoutePlanningMode = false,
                    activeRoute = null,
                    plannedWaypoints = emptyList()
                )
            }
        }
    }

    fun startNavigation() {
        val state = _uiState.value
        val currentLoc = state.currentLocation
        val activeRoute = state.activeRoute

        if (currentLoc != null && activeRoute != null && activeRoute.waypoints.isNotEmpty()) {
            val startWaypoint = activeRoute.waypoints.first()
            val results = FloatArray(1)
            Location.distanceBetween(
                currentLoc.latitude, currentLoc.longitude,
                startWaypoint.latitude, startWaypoint.longitude,
                results
            )

            // If user moved more than 30 meters from original start point, update start point to live GPS
            if (results[0] > 30f && activeRoute.waypoints.size >= 2) {
                val updatedWaypoints = activeRoute.waypoints.toMutableList()
                updatedWaypoints[0] = currentLoc.copy(name = "Mein Standort")

                viewModelScope.launch {
                    val footPref = userSettingsRepository.settings.value.footSpeed
                    val carPref = userSettingsRepository.settings.value.carSpeed
                    val result = routingRepository.getRoute(updatedWaypoints, state.travelMode, footPref, carPref)
                    result.onSuccess { newRoute ->
                        NavigationStateHolder.startNavigation(newRoute)
                        recordRouteToHistory(newRoute)
                        _uiState.update {
                            it.copy(
                                activeRoute = newRoute,
                                isNavigating = true,
                                isRoutePlanningMode = false,
                                currentRouteStepIndex = 0
                            )
                        }
                    }.onFailure {
                        _uiState.update {
                            it.copy(
                                isNavigating = true,
                                isRoutePlanningMode = false,
                                currentRouteStepIndex = 0
                            )
                        }
                    }
                }
                return
            }
        }

        activeRoute?.let {
            NavigationStateHolder.startNavigation(it)
            recordRouteToHistory(it)
        }
        _uiState.update { 
            it.copy(
                isNavigating = true,
                isRoutePlanningMode = false,
                currentRouteStepIndex = 0
            ) 
        }
    }

    private fun recordRouteToHistory(route: RouteResult) {
        if (route.waypoints.size < 2) return
        val start = route.waypoints.first()
        val dest = route.waypoints.last()
        viewModelScope.launch {
            tripHistoryRepository.recordTrip(start, dest, route.mode)
        }
    }

    fun navigateToPoint(destination: LocationPoint, mode: TravelMode = _uiState.value.travelMode) {
        val currentLoc = _uiState.value.currentLocation
            ?: NavigationStateHolder.currentLocation.value
            ?: LocationPoint(latitude = 52.5200, longitude = 13.4050, name = "Start")
        _uiState.update {
            it.copy(
                selectedPoi = null,
                isRoutePlanningMode = false,
                travelMode = mode
            )
        }
        calculateRoute(currentLoc, destination, mode)
    }

    fun toggleMute() {
        val nextMuted = !userSettingsRepository.settings.value.voiceMuted
        userSettingsRepository.setVoiceMuted(nextMuted)
        _uiState.update { it.copy(isMuted = nextMuted) }
    }

    fun toggleOverviewMode() {
        _uiState.update { it.copy(isOverviewMode = !it.isOverviewMode) }
    }

    fun stopNavigation() {
        val currentState = _uiState.value
        val activeRoute = currentState.activeRoute
        val currentLoc = currentState.currentLocation ?: NavigationStateHolder.currentLocation.value
        if (activeRoute?.mode == TravelMode.CAR && currentLoc != null) {
            val destLabel = activeRoute.waypoints.lastOrNull()?.name?.ifBlank { null } ?: "Geparktes Auto"
            userSettingsRepository.saveParkedCar(
                latitude = currentLoc.latitude,
                longitude = currentLoc.longitude,
                label = destLabel
            )
        }
        NavigationStateHolder.stopNavigation()
        _uiState.update { 
            it.copy(
                isNavigating = false, 
                activeRoute = null, 
                alternativeRoutes = emptyList(),
                selectedPoi = null,
                plannedWaypoints = emptyList(),
                isRoutePlanningMode = false
            ) 
        }
    }

    private val downloadTimeHistory = ArrayDeque<Pair<Long, Int>>()

    private fun estimateVectorTileCount(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        minZoom: Int,
        maxZoom: Int
    ): Int {
        var total = 0
        val clampedMax = maxZoom.coerceAtMost(14) // OpenFreeMap vector tiles cap at z14 (overzoomed smoothly to z22)
        for (z in minZoom.coerceAtMost(clampedMax)..clampedMax) {
            val n = 1 shl z
            val x1 = floor((minLon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            val x2 = floor((maxLon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            fun latToY(lat: Double): Int {
                val rad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
                return floor((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
            }
            val y1 = latToY(maxLat)
            val y2 = latToY(minLat)
            total += (kotlin.math.abs(x2 - x1) + 1) * (kotlin.math.abs(y2 - y1) + 1)
        }
        return total.coerceAtLeast(1)
    }

    fun prepareOfflineMapDownload(controller: MapLibreController) {
        val bounds = controller.visibleBounds()
        val currentZoom = controller.currentZoom().toInt().coerceIn(8, 14)
        val minZoom = currentZoom
        val maxZoom = 14

        val totalTiles = estimateVectorTileCount(
            minLat = bounds.latitudeSouth,
            maxLat = bounds.latitudeNorth,
            minLon = bounds.longitudeWest,
            maxLon = bounds.longitudeEast,
            minZoom = minZoom,
            maxZoom = maxZoom
        )
        // Vector tiles average ~25 KB each (far smaller than multi-zoom PNG pyramids)
        val estMb = (totalTiles * 25.0) / 1024.0

        val centerLat = (bounds.latitudeNorth + bounds.latitudeSouth) / 2.0
        val centerLon = (bounds.longitudeEast + bounds.longitudeWest) / 2.0

        viewModelScope.launch {
            val geocodeResult = routingRepository.reverseGeocode(centerLat, centerLon)
            val suggestedName = geocodeResult.getOrNull()?.name?.ifBlank { null }
                ?: "Kartenbereich (%.2f, %.2f)".format(centerLat, centerLon)

            _uiState.update {
                it.copy(
                    pendingDownloadEstimate = DownloadEstimate(
                        suggestedName = suggestedName,
                        totalTiles = totalTiles,
                        estimatedSizeMB = estMb,
                        minZoom = minZoom,
                        maxZoom = maxZoom,
                        minLat = bounds.latitudeSouth,
                        maxLat = bounds.latitudeNorth,
                        minLon = bounds.longitudeWest,
                        maxLon = bounds.longitudeEast
                    )
                )
            }
        }
    }

    fun dismissDownloadEstimate() {
        _uiState.update { it.copy(pendingDownloadEstimate = null) }
    }

    fun confirmOfflineMapDownload(context: Context, controller: MapLibreController?, customRegionName: String = "") {
        val estimate = _uiState.value.pendingDownloadEstimate ?: return
        val regionName = customRegionName.ifBlank { estimate.suggestedName }

        _uiState.update {
            it.copy(
                pendingDownloadEstimate = null,
                isDownloadingOfflineMap = true,
                downloadProgress = 0,
                downloadTotalTiles = estimate.totalTiles,
                downloadStatusMessage = "Vektor-Download gestartet...",
                downloadEstimatedTimeRemaining = "Berechne Restzeit..."
            )
        }

        downloadTimeHistory.clear()

        val bounds = LatLngBounds.Builder()
            .include(LatLng(estimate.maxLat, estimate.maxLon))
            .include(LatLng(estimate.minLat, estimate.minLon))
            .build()

        val definition = OfflineTilePyramidRegionDefinition(
            MapLibreStyleHelper.DAY_STYLE_URL,
            bounds,
            estimate.minZoom.toDouble(),
            estimate.maxZoom.toDouble(),
            context.resources.displayMetrics.density
        )
        val metadata = regionName.toByteArray(Charsets.UTF_8)

        val offlineManager = OfflineManager.getInstance(context.applicationContext)
        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val completed = status.completedResourceCount.toInt()
                            val required = status.requiredResourceCount.toInt().coerceAtLeast(1)
                            val now = System.currentTimeMillis()
                            downloadTimeHistory.addLast(Pair(now, completed))
                            while (downloadTimeHistory.isNotEmpty() && (now - downloadTimeHistory.first.first) > 10000L) {
                                downloadTimeHistory.removeFirst()
                            }
                            var etaStr: String? = null
                            if (downloadTimeHistory.size >= 2) {
                                val oldest = downloadTimeHistory.first
                                val timeDeltaSec = (now - oldest.first) / 1000.0
                                val progressDelta = completed - oldest.second
                                if (timeDeltaSec > 1.0 && progressDelta > 0) {
                                    val rate = progressDelta / timeDeltaSec
                                    val rem = required - completed
                                    etaStr = if (rem > 0 && rate > 0) formatEta((rem / rate).toInt()) else "fast fertig..."
                                }
                            }

                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                val actualMb = status.completedResourceSize / (1024.0 * 1024.0)
                                refreshOfflineCacheSize(context)
                                viewModelScope.launch {
                                    poiRepository.saveOfflineRegion(
                                        OfflineRegionEntity(
                                            name = regionName,
                                            minLat = estimate.minLat,
                                            maxLat = estimate.maxLat,
                                            minLon = estimate.minLon,
                                            maxLon = estimate.maxLon,
                                            minZoom = estimate.minZoom,
                                            maxZoom = estimate.maxZoom,
                                            tileCount = required,
                                            sizeMB = actualMb
                                        )
                                    )
                                }
                                _uiState.update {
                                    it.copy(
                                        isDownloadingOfflineMap = false,
                                        downloadProgress = required,
                                        downloadTotalTiles = required,
                                        downloadStatusMessage = "Vektor-Region '$regionName' offline gespeichert (%.1f MB)!".format(actualMb),
                                        downloadEstimatedTimeRemaining = null
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        downloadProgress = completed,
                                        downloadTotalTiles = required,
                                        downloadStatusMessage = "Lade Vektor-Daten ($completed / $required)...",
                                        downloadEstimatedTimeRemaining = etaStr
                                    )
                                }
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            refreshOfflineCacheSize(context)
                            _uiState.update {
                                it.copy(
                                    isDownloadingOfflineMap = false,
                                    downloadStatusMessage = "Download-Fehler: ${error.message}",
                                    downloadEstimatedTimeRemaining = null
                                )
                            }
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            _uiState.update {
                                it.copy(
                                    isDownloadingOfflineMap = false,
                                    downloadStatusMessage = "Kachellimit ($limit) überschritten – bitte weiter hineinzoomen.",
                                    downloadEstimatedTimeRemaining = null
                                )
                            }
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    _uiState.update {
                        it.copy(
                            isDownloadingOfflineMap = false,
                            downloadStatusMessage = "Offline-Region konnte nicht erstellt werden: $error",
                            downloadEstimatedTimeRemaining = null
                        )
                    }
                }
            }
        )
    }

    fun deleteOfflineRegion(context: Context, controller: MapLibreController?, region: OfflineRegionEntity) {
        viewModelScope.launch {
            poiRepository.deleteOfflineRegion(region)
            try {
                OfflineManager.getInstance(context.applicationContext)
                    .listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                        override fun onList(offlineRegions: Array<OfflineRegion>?) {
                            offlineRegions?.forEach { mlRegion ->
                                val name = runCatching { mlRegion.metadata.toString(Charsets.UTF_8) }.getOrNull()
                                if (name == region.name) {
                                    mlRegion.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                                        override fun onDelete() {
                                            refreshOfflineCacheSize(context)
                                        }
                                        override fun onError(error: String) {}
                                    })
                                }
                            }
                        }
                        override fun onError(error: String) {}
                    })
            } catch (_: Exception) {}
            refreshOfflineCacheSize(context)
            _uiState.update { it.copy(downloadStatusMessage = "Region '${region.name}' gelöscht.") }
        }
    }

    fun updateOfflineRegion(context: Context, controller: MapLibreController, region: OfflineRegionEntity) {
        _uiState.update {
            it.copy(
                pendingDownloadEstimate = DownloadEstimate(
                    suggestedName = region.name,
                    totalTiles = region.tileCount,
                    estimatedSizeMB = region.sizeMB,
                    minZoom = region.minZoom,
                    maxZoom = region.maxZoom,
                    minLat = region.minLat,
                    maxLat = region.maxLat,
                    minLon = region.minLon,
                    maxLon = region.maxLon
                )
            )
        }
        confirmOfflineMapDownload(context, controller, region.name)
    }

    private fun formatEta(totalSec: Int): String {
        if (totalSec <= 1) return "fast fertig..."
        val mins = totalSec / 60
        val secs = totalSec % 60
        return if (mins > 0) {
            "ca. $mins Min. $secs Sek. verbleibend"
        } else {
            "ca. $secs Sek. verbleibend"
        }
    }

    fun startOfflineMapDownload(context: Context, controller: MapLibreController) {
        prepareOfflineMapDownload(controller)
    }

    fun dismissOfflineDownloadStatus() {
        _uiState.update { it.copy(downloadStatusMessage = null) }
    }

    fun refreshOfflineCacheSize(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mbglDb = File(context.filesDir, "mbgl-offline.db")
                val sizeBytes = if (mbglDb.exists()) mbglDb.length() else 0L
                val sizeFormatted = if (sizeBytes < 1024 * 1024) {
                    "%.1f KB".format(sizeBytes / 1024.0)
                } else {
                    "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
                }
                _uiState.update { it.copy(offlineCacheSizeMB = sizeFormatted) }
            } catch (_: Exception) {}
        }
    }

    fun clearOfflineTileCache(context: Context, controller: MapLibreController?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                OfflineManager.getInstance(context.applicationContext)
                    .clearAmbientCache(object : OfflineManager.FileSourceCallback {
                        override fun onSuccess() {
                            refreshOfflineCacheSize(context)
                        }
                        override fun onError(message: String) {}
                    })
            } catch (_: Exception) {}
            refreshOfflineCacheSize(context)
            _uiState.update { it.copy(downloadStatusMessage = "Karten-Cache gelöscht.") }
        }
    }
}
