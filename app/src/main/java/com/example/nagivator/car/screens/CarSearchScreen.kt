package com.example.nagivator.car.screens

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.car.surface.CarMapSurfaceRenderer
import com.example.nagivator.data.local.NagivatorDatabase
import com.example.nagivator.data.repository.RoutingRepository
import com.example.nagivator.data.repository.TripHistoryRepository
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Android Auto SearchTemplate screen for finding non-favorite destinations via Photon geocoding.
 */
class CarSearchScreen(
    carContext: CarContext,
    private val surfaceRenderer: CarMapSurfaceRenderer
) : Screen(carContext) {

    private val db = NagivatorDatabase.getDatabase(carContext)
    private val tripHistoryRepository = TripHistoryRepository(db.tripHistoryDao())
    private val routingRepository = RoutingRepository()

    private var currentQuery: String = ""
    private var searchResults: List<LocationPoint> = emptyList()
    private var isSearching = false
    private var isCalculatingRoute = false
    private var hasSearched = false
    private var searchJob: Job? = null

    private val searchCallback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            val trimmed = searchText.trim()
            currentQuery = searchText
            searchJob?.cancel()

            if (trimmed.isEmpty()) {
                searchResults = emptyList()
                hasSearched = false
                isSearching = false
                invalidate()
                return
            }

            if (trimmed.length < 3) {
                return
            }

            searchJob = lifecycleScope.launch {
                delay(400L)
                performSearch(trimmed)
            }
        }

        override fun onSearchSubmitted(searchText: String) {
            val trimmed = searchText.trim()
            currentQuery = searchText
            searchJob?.cancel()
            if (trimmed.isEmpty()) return

            searchJob = lifecycleScope.launch {
                performSearch(trimmed)
            }
        }
    }

    private suspend fun performSearch(query: String) {
        isSearching = true
        invalidate()

        val userLoc = NavigationStateHolder.currentLocation.value
        val result = routingRepository.searchPlaces(
            query = query,
            biasLat = userLoc?.latitude,
            biasLon = userLoc?.longitude,
            limit = 6
        )

        isSearching = false
        hasSearched = true
        result.onSuccess { places ->
            searchResults = places.take(6)
        }.onFailure { error ->
            searchResults = emptyList()
            CarToast.makeText(
                carContext,
                error.localizedMessage ?: "Suche fehlgeschlagen",
                CarToast.LENGTH_SHORT
            ).show()
        }
        invalidate()
    }

    private fun startNavigationToPlace(destination: LocationPoint) {
        val startLoc = NavigationStateHolder.currentLocation.value
        if (startLoc == null) {
            CarToast.makeText(
                carContext,
                "Warte auf GPS-Position...",
                CarToast.LENGTH_SHORT
            ).show()
            return
        }

        isCalculatingRoute = true
        invalidate()

        lifecycleScope.launch {
            val result = routingRepository.getRoute(
                waypoints = listOf(startLoc, destination),
                mode = TravelMode.CAR
            )

            result.onSuccess { route ->
                isCalculatingRoute = false
                tripHistoryRepository.recordTrip(startLoc, destination, TravelMode.CAR)
                NavigationStateHolder.startNavigation(route)
                surfaceRenderer.updateRoute(route)
            }.onFailure { error ->
                isCalculatingRoute = false
                invalidate()
                CarToast.makeText(
                    carContext,
                    error.localizedMessage ?: "Routenberechnung fehlgeschlagen",
                    CarToast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val builder = SearchTemplate.Builder(searchCallback)
            .setHeaderAction(Action.BACK)
            .setSearchHint("Ziel, Adresse oder Ort suchen...")
            .setShowKeyboardByDefault(true)

        if (currentQuery.isNotEmpty()) {
            builder.setInitialSearchText(currentQuery)
        }

        if (isSearching || isCalculatingRoute) {
            return builder
                .setLoading(true)
                .build()
        }

        val userLoc = NavigationStateHolder.currentLocation.value
        val listBuilder = ItemList.Builder()

        if (searchResults.isEmpty()) {
            val emptyMsg = if (hasSearched && currentQuery.isNotBlank()) {
                "Keine Ziele für \"$currentQuery\" gefunden"
            } else {
                "Ziel, Straße oder Ort eingeben"
            }
            listBuilder.setNoItemsMessage(emptyMsg)
        } else {
            searchResults.take(6).forEach { place ->
                val title = place.name.ifBlank { "Gefundenes Ziel" }
                val subtitle = CarNavigationHelper.formatSearchSubtitle(userLoc, place)
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(title)
                        .addText(subtitle)
                        .setOnClickListener {
                            if (!isCalculatingRoute) {
                                startNavigationToPlace(place)
                            }
                        }
                        .build()
                )
            }
        }

        return builder
            .setItemList(listBuilder.build())
            .setLoading(false)
            .build()
    }
}

