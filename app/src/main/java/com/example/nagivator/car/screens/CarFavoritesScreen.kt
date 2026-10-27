package com.example.nagivator.car.screens

import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
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

        return ListTemplate.Builder()
            .setTitle(if (isCalculating) "Route wird berechnet..." else "Meine Favoriten")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setLoading(isCalculating)
            .build()
    }

    private fun startNavigationToPoi(poi: PoiEntity) {
        val startLoc = NavigationStateHolder.currentLocation.value
            ?: LocationPoint(latitude = 52.5200, longitude = 13.4050, name = "Start")

        val destLoc = LocationPoint(
            latitude = poi.latitude,
            longitude = poi.longitude,
            name = poi.displayTitle
        )

        isCalculating = true
        invalidate()

        lifecycleScope.launch {
            val result = routingRepository.getRoute(
                waypoints = listOf(startLoc, destLoc),
                mode = TravelMode.CAR
            )

            result.onSuccess { route ->
                isCalculating = false
                NavigationStateHolder.startNavigation(route)
                surfaceRenderer.updateRoute(route)
                screenManager.push(CarNavigationScreen(carContext, surfaceRenderer))
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
