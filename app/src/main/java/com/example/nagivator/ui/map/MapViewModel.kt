package com.example.nagivator.ui.map

import android.app.Application
import android.content.Context
import android.location.Location
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
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
            userSettingsRepository.settings.collect {
                if (_uiState.value.isRoutePlanningMode && _uiState.value.plannedWaypoints.size >= 2) {
                    calculatePlannedRoute()
                } else if (_uiState.value.selectedPoi != null) {
                    _uiState.value.selectedPoi?.let { calculateRouteTo(it) }
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
            val result = routingRepository.getRoute(listOf(start, destination), mode, footPref, carPref)
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

        activeRoute?.let { NavigationStateHolder.startNavigation(it) }
        _uiState.update { 
            it.copy(
                isNavigating = true,
                isRoutePlanningMode = false,
                currentRouteStepIndex = 0
            ) 
        }
    }

    fun toggleMute() {
        NavigationStateHolder.toggleMute()
        _uiState.update { it.copy(isMuted = !it.isMuted) }
    }

    fun toggleOverviewMode() {
        _uiState.update { it.copy(isOverviewMode = !it.isOverviewMode) }
    }

    fun stopNavigation() {
        NavigationStateHolder.stopNavigation()
        _uiState.update { 
            it.copy(
                isNavigating = false, 
                activeRoute = null, 
                selectedPoi = null,
                plannedWaypoints = emptyList(),
                isRoutePlanningMode = false
            ) 
        }
    }

    private val downloadTimeHistory = ArrayDeque<Pair<Long, Int>>()

    fun prepareOfflineMapDownload(mapView: MapView) {
        val cacheManager = CacheManager(mapView)
        val boundingBox = mapView.boundingBox ?: return
        val currentZoom = mapView.zoomLevelDouble.toInt().coerceIn(1, 18)
        val minZoom = currentZoom
        val maxZoom = 18

        val totalTiles = cacheManager.possibleTilesInArea(boundingBox, minZoom, maxZoom)
        val estMb = (totalTiles * 18.0) / 1024.0

        val centerLat = (boundingBox.latNorth + boundingBox.latSouth) / 2.0
        val centerLon = (boundingBox.lonEast + boundingBox.lonWest) / 2.0

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
                        minLat = boundingBox.latSouth,
                        maxLat = boundingBox.latNorth,
                        minLon = boundingBox.lonWest,
                        maxLon = boundingBox.lonEast
                    )
                )
            }
        }
    }

    fun dismissDownloadEstimate() {
        _uiState.update { it.copy(pendingDownloadEstimate = null) }
    }

    fun confirmOfflineMapDownload(context: Context, mapView: MapView, customRegionName: String = "") {
        val estimate = _uiState.value.pendingDownloadEstimate ?: return
        val cacheManager = CacheManager(mapView)
        val boundingBox = mapView.boundingBox ?: return
        val regionName = customRegionName.ifBlank { estimate.suggestedName }

        _uiState.update {
            it.copy(
                pendingDownloadEstimate = null,
                isDownloadingOfflineMap = true,
                downloadProgress = 0,
                downloadTotalTiles = estimate.totalTiles,
                downloadStatusMessage = "Download gestartet (${estimate.totalTiles} Kacheln)...",
                downloadEstimatedTimeRemaining = "Berechne Restzeit..."
            )
        }

        downloadTimeHistory.clear()

        cacheManager.downloadAreaAsync(
            context.applicationContext,
            boundingBox,
            estimate.minZoom,
            estimate.maxZoom,
            object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() {
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
                                tileCount = estimate.totalTiles,
                                sizeMB = estimate.estimatedSizeMB
                            )
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isDownloadingOfflineMap = false,
                            downloadStatusMessage = "Region '$regionName' erfolgreich offline gespeichert!",
                            downloadEstimatedTimeRemaining = null
                        )
                    }
                }

                override fun onTaskFailed(errors: Int) {
                    refreshOfflineCacheSize(context)
                    _uiState.update {
                        it.copy(
                            isDownloadingOfflineMap = false,
                            downloadStatusMessage = "Karten-Download beendet ($errors Fehler).",
                            downloadEstimatedTimeRemaining = null
                        )
                    }
                }

                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                    val now = System.currentTimeMillis()
                    downloadTimeHistory.addLast(Pair(now, progress))

                    while (downloadTimeHistory.isNotEmpty() && (now - downloadTimeHistory.first.first) > 10000L) {
                        downloadTimeHistory.removeFirst()
                    }

                    var etaStr: String? = null
                    if (downloadTimeHistory.size >= 2) {
                        val oldest = downloadTimeHistory.first
                        val timeDeltaSec = (now - oldest.first) / 1000.0
                        val progressDelta = progress - oldest.second

                        if (timeDeltaSec > 1.0 && progressDelta > 0) {
                            val tilesPerSec = progressDelta / timeDeltaSec
                            val remainingTiles = estimate.totalTiles - progress
                            if (remainingTiles > 0 && tilesPerSec > 0) {
                                val remainingSec = (remainingTiles / tilesPerSec).toInt()
                                etaStr = formatEta(remainingSec)
                            } else {
                                etaStr = "fast fertig..."
                            }
                        }
                    }

                    _uiState.update {
                        it.copy(
                            downloadProgress = progress,
                            downloadStatusMessage = "Speichere Kachel $progress von ${estimate.totalTiles}...",
                            downloadEstimatedTimeRemaining = etaStr
                        )
                    }
                }

                override fun downloadStarted() {}
                override fun setPossibleTilesInArea(total: Int) {}
            }
        )
    }

    fun deleteOfflineRegion(context: Context, mapView: MapView?, region: OfflineRegionEntity) {
        viewModelScope.launch {
            poiRepository.deleteOfflineRegion(region)
            if (mapView != null) {
                try {
                    val cacheManager = CacheManager(mapView)
                    val bbox = BoundingBox(region.maxLat, region.maxLon, region.minLat, region.minLon)
                    cacheManager.cleanAreaAsync(context.applicationContext, bbox, region.minZoom, region.maxZoom)
                } catch (_: Exception) {}
            }
            refreshOfflineCacheSize(context)
            _uiState.update { it.copy(downloadStatusMessage = "Region '${region.name}' gelöscht.") }
        }
    }

    fun updateOfflineRegion(context: Context, mapView: MapView, region: OfflineRegionEntity) {
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
        confirmOfflineMapDownload(context, mapView, region.name)
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

    fun startOfflineMapDownload(context: Context, mapView: MapView) {
        prepareOfflineMapDownload(mapView)
    }

    fun cacheTilesAroundLocation(context: Context, mapView: MapView, lat: Double, lon: Double) {
        try {
            val cacheManager = CacheManager(mapView)
            val delta = 0.015 // ~1.5km radius
            val bbox = BoundingBox(lat + delta, lon + delta, lat - delta, lon - delta)
            cacheManager.downloadAreaAsync(context.applicationContext, bbox, 14, 17, object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() {}
                override fun onTaskFailed(errors: Int) {}
                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {}
                override fun downloadStarted() {}
                override fun setPossibleTilesInArea(total: Int) {}
            })
        } catch (_: Exception) {}
    }

    fun dismissOfflineDownloadStatus() {
        _uiState.update { it.copy(downloadStatusMessage = null) }
    }

    fun refreshOfflineCacheSize(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tileCacheDir = Configuration.getInstance().osmdroidTileCache
                val sizeBytes = getFolderSize(tileCacheDir)
                val sizeFormatted = if (sizeBytes < 1024 * 1024) {
                    "%.1f KB".format(sizeBytes / 1024.0)
                } else {
                    "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
                }
                _uiState.update { it.copy(offlineCacheSizeMB = sizeFormatted) }
            } catch (_: Exception) {}
        }
    }

    private fun getFolderSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        file.listFiles()?.forEach { child ->
            size += getFolderSize(child)
        }
        return size
    }

    fun clearOfflineTileCache(context: Context, mapView: MapView?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mapView?.tileProvider?.clearTileCache()
                val tileCacheDir = Configuration.getInstance().osmdroidTileCache
                if (tileCacheDir.exists()) {
                    tileCacheDir.deleteRecursively()
                    tileCacheDir.mkdirs()
                }
            } catch (_: Exception) {}
            refreshOfflineCacheSize(context)
            _uiState.update { it.copy(downloadStatusMessage = "Karten-Cache gelöscht.") }
        }
    }
}
