package com.example.nagivator.car.screens

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.nagivator.R
import com.example.nagivator.car.surface.CarMapSurfaceRenderer
import com.example.nagivator.car.surface.MapPerspective
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

        // Auto-switch to navigation screen if navigation is active (e.g. started from phone)
        routeJob = lifecycleScope.launch {
            NavigationStateHolder.isNavigating.collectLatest { isNavigating ->
                if (isNavigating && NavigationStateHolder.activeRoute.value != null) {
                    screenManager.popToRoot()
                    if (screenManager.top !is CarNavigationScreen) {
                        screenManager.push(CarNavigationScreen(carContext, surfaceRenderer))
                    }
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
        val searchAction = Action.Builder()
            .setTitle("Suche")
            .setOnClickListener {
                screenManager.push(CarSearchScreen(carContext, surfaceRenderer))
            }
            .build()

        val favoritesAction = Action.Builder()
            .setTitle("Favoriten")
            .setOnClickListener {
                screenManager.push(CarFavoritesScreen(carContext, surfaceRenderer))
            }
            .build()

        val templateBuilder = NavigationTemplate.Builder()
            .setBackgroundColor(CarColor.SECONDARY)

        if (carContext.carAppApiLevel >= 5) {
            val actionStrip = ActionStrip.Builder()
                .addAction(searchAction)
                .addAction(favoritesAction)
                .build()

            val mapActionStrip = ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(carContext, R.drawable.ic_explore)
                            ).build()
                        )
                        .setOnClickListener {
                            surfaceRenderer.togglePerspective()
                            invalidate()
                        }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(carContext, R.drawable.ic_zoom_in)
                            ).build()
                        )
                        .setOnClickListener {
                            surfaceRenderer.zoomIn()
                        }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(carContext, R.drawable.ic_zoom_out)
                            ).build()
                        )
                        .setOnClickListener {
                            surfaceRenderer.zoomOut()
                        }
                        .build()
                )
                .build()

            templateBuilder
                .setActionStrip(actionStrip)
                .setMapActionStrip(mapActionStrip)
        } else {
            val actionStrip = ActionStrip.Builder()
                .addAction(searchAction)
                .addAction(favoritesAction)
                .addAction(
                    Action.Builder()
                        .setTitle(
                            if (surfaceRenderer.getPerspective() == MapPerspective.HEADING_UP) "Norden" else "Fahrt"
                        )
                        .setOnClickListener {
                            surfaceRenderer.togglePerspective()
                            invalidate()
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
                .build()

            templateBuilder.setActionStrip(actionStrip)
        }

        return templateBuilder.build()
    }
}
