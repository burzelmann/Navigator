package com.example.nagivator.car.screens

import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.nagivator.car.surface.CarMapSurfaceRenderer
import com.example.nagivator.data.local.NagivatorDatabase
import com.example.nagivator.data.local.entity.PoiEntity
import com.example.nagivator.data.repository.PoiRepository
import com.example.nagivator.data.repository.RoutingRepository
import com.example.nagivator.data.repository.TripHistoryRepository
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CarFavoritesScreen(
    carContext: CarContext,
    private val surfaceRenderer: CarMapSurfaceRenderer
) : Screen(carContext), DefaultLifecycleObserver {

    private val db = NagivatorDatabase.getDatabase(carContext)
    private val poiRepository = PoiRepository(db.poiDao(), db.savedRouteDao())
    private val tripHistoryRepository = TripHistoryRepository(db.tripHistoryDao())
    private val routingRepository = RoutingRepository()

    private var favoritePois: List<PoiEntity> = emptyList()
    private var isCalculating = false

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        lifecycleScope.launch {
            favoritePois = poiRepository.favoritePois.first()
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        if (isCalculating) {
            return ListTemplate.Builder()
                .setTitle("Route wird berechnet...")
                .setHeaderAction(Action.BACK)
                .setLoading(true)
                .build()
        }

        val listBuilder = ItemList.Builder()

        if (favoritePois.isEmpty()) {
            listBuilder.setNoItemsMessage("Keine Favoriten gespeichert. Markiere Orte in der App als Favorit!")
        } else {
            val userLoc = NavigationStateHolder.currentLocation.value
            val distResults = FloatArray(1)

            favoritePois.forEach { poi ->
                val rowBuilder = Row.Builder()
                    .setTitle(poi.displayTitle)

                var desc = poi.description.ifBlank { "Gespeicherter Ort" }
                if (userLoc != null) {
                    Location.distanceBetween(
                        userLoc.latitude, userLoc.longitude,
                        poi.latitude, poi.longitude,
                        distResults
                    )
                    val distKm = "%.1f km".format(distResults[0] / 1000.0)
                    desc = "$distKm • $desc"
                }
                rowBuilder.addText(desc)

                rowBuilder.setOnClickListener {
                    if (!isCalculating) {
                        startNavigationToPoi(poi)
                    }
                }

                listBuilder.addItem(rowBuilder.build())
            }
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Suche")
                    .setOnClickListener {
                        screenManager.push(CarSearchScreen(carContext, surfaceRenderer))
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("Meine Favoriten")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setSingleList(listBuilder.build())
            .build()
    }

    private fun startNavigationToPoi(poi: PoiEntity) {
        val startLoc = NavigationStateHolder.currentLocation.value
        if (startLoc == null) {
            CarToast.makeText(
                carContext,
                "Warte auf GPS-Position...",
                CarToast.LENGTH_SHORT
            ).show()
            return
        }

        val destLoc = LocationPoint(
            latitude = poi.latitude,
            longitude = poi.longitude,
            name = poi.displayTitle
        )

        isCalculating = true
        invalidate()

        lifecycleScope.launch {
            val result = routingRepository.getRouteAlternatives(
                waypoints = listOf(startLoc, destLoc),
                mode = TravelMode.CAR
            )

            result.onSuccess { routes ->
                isCalculating = false
                if (routes.size > 1) {
                    invalidate()
                    screenManager.push(
                        CarRoutePreviewScreen(carContext, surfaceRenderer, routes, startLoc, destLoc)
                    )
                } else {
                    val route = routes.first()
                    tripHistoryRepository.recordTrip(startLoc, destLoc, TravelMode.CAR)
                    NavigationStateHolder.startNavigation(route)
                    surfaceRenderer.updateRoute(route)
                }
            }.onFailure { error ->
                isCalculating = false
                invalidate()
                CarToast.makeText(
                    carContext,
                    error.localizedMessage ?: "Routenberechnung fehlgeschlagen",
                    CarToast.LENGTH_LONG
                ).show()
            }
        }
    }
}
