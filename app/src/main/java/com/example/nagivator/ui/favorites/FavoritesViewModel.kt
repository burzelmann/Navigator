package com.example.nagivator.ui.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nagivator.data.local.NagivatorDatabase
import com.example.nagivator.data.local.entity.PoiEntity
import com.example.nagivator.data.local.entity.SavedRouteEntity
import com.example.nagivator.data.local.entity.TripHistoryEntity
import com.example.nagivator.data.repository.ParkedCarLocation
import com.example.nagivator.data.repository.PoiRepository
import com.example.nagivator.data.repository.TripHistoryRepository
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.PredictedDestination
import com.example.nagivator.domain.RoutePredictionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = NagivatorDatabase.getDatabase(application)
    private val poiRepository = PoiRepository(db.poiDao(), db.savedRouteDao())
    private val tripHistoryRepository = TripHistoryRepository(db.tripHistoryDao())
    private val userSettingsRepository = UserSettingsRepository.getInstance(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val parkedCar: StateFlow<ParkedCarLocation?> = userSettingsRepository.settings
        .map { it.parkedCar }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = userSettingsRepository.settings.value.parkedCar
        )

    val recentTrips: StateFlow<List<TripHistoryEntity>> = combine(
        tripHistoryRepository.recentTrips(30),
        _searchQuery
    ) { trips, query ->
        if (query.isBlank()) trips
        else trips.filter {
            it.destinationName.contains(query, ignoreCase = true) ||
                it.startName.contains(query, ignoreCase = true)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val predictedDestinations: StateFlow<List<PredictedDestination>> = combine(
        tripHistoryRepository.recentTrips(100),
        NavigationStateHolder.currentLocation
    ) { trips, currentLoc ->
        RoutePredictionUseCase.predict(
            history = trips,
            currentLocation = currentLoc,
            maxResults = 3
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val favoritePois: StateFlow<List<PoiEntity>> = combine(
        poiRepository.favoritePois,
        _searchQuery
    ) { pois, query ->
        if (query.isBlank()) pois
        else pois.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val favoriteRoutes: StateFlow<List<SavedRouteEntity>> = combine(
        poiRepository.allSavedRoutes,
        _searchQuery
    ) { routes, query ->
        if (query.isBlank()) routes
        else routes.filter {
            it.title.contains(query, ignoreCase = true)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(poi: PoiEntity) {
        viewModelScope.launch {
            poiRepository.toggleFavorite(poi)
        }
    }

    fun deletePoi(poi: PoiEntity) {
        viewModelScope.launch {
            poiRepository.deletePoi(poi)
        }
    }

    fun updatePoi(poi: PoiEntity) {
        viewModelScope.launch {
            poiRepository.savePoi(poi)
        }
    }

    fun updateRouteTitle(route: SavedRouteEntity, newTitle: String) {
        viewModelScope.launch {
            poiRepository.saveRoute(route.copy(title = newTitle))
        }
    }

    fun deleteRoute(route: SavedRouteEntity) {
        viewModelScope.launch {
            poiRepository.deleteRoute(route)
        }
    }

    fun deleteTrip(id: Long) {
        viewModelScope.launch {
            tripHistoryRepository.deleteTrip(id)
        }
    }

    fun clearParkedCar() {
        userSettingsRepository.clearParkedCar()
    }
}
