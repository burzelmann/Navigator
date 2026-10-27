package com.example.nagivator.car.screens

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.nagivator.car.surface.CarMapSurfaceRenderer
import com.example.nagivator.data.repository.LocationRepository
import com.example.nagivator.domain.NavigationStateHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CarHomeScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    val surfaceRenderer = CarMapSurfaceRenderer(carContext)
    private val locationRepository = LocationRepository(carContext)
    private var locationJob: Job? = null
    private var routeJob: Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceRenderer)

        // Observe GPS updates
        locationJob = lifecycleScope.launch {
            locationRepository.getLocationUpdates().collectLatest { point ->
                NavigationStateHolder.updateLocation(point)
                surfaceRenderer.updateLocation(point)
            }
        }

        // Auto-switch to navigation screen if navigation is active
        routeJob = lifecycleScope.launch {
            NavigationStateHolder.isNavigating.collectLatest { isNavigating ->
                if (isNavigating && NavigationStateHolder.activeRoute.value != null) {
                    screenManager.push(CarNavigationScreen(carContext, surfaceRenderer))
                }
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        locationJob?.cancel()
        routeJob?.cancel()
        surfaceRenderer.destroy()
    }

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Favoriten")
                    .setOnClickListener {
                        screenManager.push(CarFavoritesScreen(carContext, surfaceRenderer))
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Zentrieren")
                    .setOnClickListener {
                        surfaceRenderer.centerOnLocation()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("+")
                    .setOnClickListener {
                        surfaceRenderer.zoomIn()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("-")
                    .setOnClickListener {
                        surfaceRenderer.zoomOut()
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .setBackgroundColor(CarColor.SECONDARY)
            .build()
    }
}
