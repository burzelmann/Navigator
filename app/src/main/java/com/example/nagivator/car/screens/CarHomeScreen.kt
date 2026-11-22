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
import com.example.nagivator.data.repository.RoadContextRepository
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.domain.NavigationStateHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CarHomeScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    val surfaceRenderer = CarMapSurfaceRenderer(carContext)
    private val locationRepository = LocationRepository(carContext)
    private val roadContextRepository = RoadContextRepository()
    private val userSettingsRepository = UserSettingsRepository.getInstance(carContext)
    private var locationJob: Job? = null
    private var roadContextJob: Job? = null
    private var roadFetchJob: Job? = null
    private var routeJob: Job? = null
    private var themeJob: Job? = null
    private var speakingJob: Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceRenderer)

        // Observe GPS updates
        locationJob = lifecycleScope.launch {
            locationRepository.getLocationUpdates().collectLatest { point ->
                NavigationStateHolder.updateLocation(point)
            }
        }

        // Render any location updates (real GPS or debug simulation) on the car map surface
        roadContextJob = lifecycleScope.launch {
            NavigationStateHolder.currentLocation.collectLatest { point ->
                if (point != null) {
                    surfaceRenderer.updateLocation(point)
                    if (roadFetchJob?.isActive != true) {
                        roadFetchJob = lifecycleScope.launch {
                            val snapshot = roadContextRepository.fetchAround(point.latitude, point.longitude)
                            if (snapshot != null) {
                                NavigationStateHolder.updateRoadContext(snapshot.speedLimitKmh, snapshot.signals)
                                surfaceRenderer.requestRender()
                            }
                        }
                    }
                }
            }
        }

        // Auto-switch to navigation screen if navigation is active (e.g. started from phone)
        routeJob = lifecycleScope.launch {
            NavigationStateHolder.isNavigating.collectLatest { isNavigating ->
                if (isNavigating && NavigationStateHolder.activeRoute.value != null) {
                    if (screenManager.top !is CarNavigationScreen) {
                        screenManager.popToRoot()
                        screenManager.push(CarNavigationScreen(carContext, surfaceRenderer))
                    }
                }
            }
        }

        // Re-render car surface when theme setting changes (in car or on phone)
        themeJob = lifecycleScope.launch {
            userSettingsRepository.settings.collectLatest {
                surfaceRenderer.requestRender()
            }
        }

        // Show live speaking indicator on HUD whenever TTS is active
        speakingJob = lifecycleScope.launch {
            NavigationStateHolder.isSpeaking.collectLatest { speaking ->
                surfaceRenderer.updateSpeaking(speaking)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        locationJob?.cancel()
        roadContextJob?.cancel()
        roadFetchJob?.cancel()
        routeJob?.cancel()
        themeJob?.cancel()
        speakingJob?.cancel()
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

        val destinationsAction = Action.Builder()
            .setTitle("Ziele")
            .setOnClickListener {
                screenManager.push(CarDestinationsScreen(carContext, surfaceRenderer))
            }
            .build()

        val settingsAction = Action.Builder()
            .setTitle("Optionen")
            .setOnClickListener {
                screenManager.push(CarQuickSettingsScreen(carContext, surfaceRenderer))
            }
            .build()

        val templateBuilder = NavigationTemplate.Builder()
            .setBackgroundColor(CarColor.SECONDARY)

        if (carContext.carAppApiLevel >= 5) {
            val actionStrip = ActionStrip.Builder()
                .addAction(searchAction)
                .addAction(favoritesAction)
                .addAction(destinationsAction)
                .addAction(settingsAction)
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
                .addAction(settingsAction)
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
                .build()

            templateBuilder.setActionStrip(actionStrip)
        }

        return templateBuilder.build()
    }
}
