package com.example.nagivator.car.screens

import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.nagivator.car.CarManeuverConverter
import com.example.nagivator.car.CarVoiceGuidance
import com.example.nagivator.car.surface.CarMapSurfaceRenderer
import com.example.nagivator.data.repository.RoutingRepository
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.TimeZone

class CarNavigationScreen(
    carContext: CarContext,
    private val surfaceRenderer: CarMapSurfaceRenderer
) : Screen(carContext), DefaultLifecycleObserver {

    private val navManager = carContext.getCarService(NavigationManager::class.java)
    private val voiceGuidance = CarVoiceGuidance(carContext)
    private val routingRepository = RoutingRepository()

    private var locationJob: Job? = null
    private var isRecalculating = false
    private var lastAnnouncedStepIndex = -1

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        navManager.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() {
                endNavigation()
            }
        })
        navManager.navigationStarted()

        // Sync initial route with surface renderer
        surfaceRenderer.updateRoute(NavigationStateHolder.activeRoute.value)

        // Observe live GPS updates during navigation
        locationJob = lifecycleScope.launch {
            NavigationStateHolder.currentLocation.collectLatest { loc ->
                if (loc != null) {
                    onLocationUpdated(loc)
                }
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        locationJob?.cancel()
        navManager.navigationEnded()
        voiceGuidance.shutdown()
    }

    private fun onLocationUpdated(loc: LocationPoint) {
        val route = NavigationStateHolder.activeRoute.value ?: return
        val steps = route.steps
        val currentIndex = NavigationStateHolder.currentStepIndex.value

        surfaceRenderer.updateLocation(loc)

        // 1. Off-Route Detection & Trailing Polyline Trimming
        if (route.geometryPoints.isNotEmpty()) {
            val (minDist, closestIdx) = minDistanceAndIndex(loc, route.geometryPoints)
            if (minDist > 55f && !isRecalculating) {
                recalculateRouteOffRoute(loc, route)
                return
            } else if (closestIdx > 0 && closestIdx < route.geometryPoints.size - 1) {
                val trimmedPoints = route.geometryPoints.subList(closestIdx, route.geometryPoints.size)
                if (trimmedPoints.size >= 2 && trimmedPoints.size != route.geometryPoints.size) {
                    val updatedRoute = route.copy(geometryPoints = trimmedPoints)
                    NavigationStateHolder.updateRoute(updatedRoute)
                    surfaceRenderer.updateRoute(updatedRoute)
                }
            }
        }

        // 2. Maneuver Step Progression & Voice Announcements
        if (steps.isNotEmpty() && currentIndex < steps.size) {
            val currentStep = steps[currentIndex]
            val maneuverLoc = currentStep.maneuverLocation

            if (maneuverLoc != null) {
                val distResults = FloatArray(1)
                Location.distanceBetween(
                    loc.latitude, loc.longitude,
                    maneuverLoc.latitude, maneuverLoc.longitude,
                    distResults
                )
                val distToManeuver = distResults[0]

                // Step completion
                if (distToManeuver < 35f) {
                    val nextIdx = (currentIndex + 1).coerceAtMost(steps.size - 1)
                    if (nextIdx != currentIndex) {
                        NavigationStateHolder.updateStepIndex(nextIdx)
                        lastAnnouncedStepIndex = -1
                        invalidate()
                        return
                    }
                }

                // Voice guidance triggers
                if (lastAnnouncedStepIndex != currentIndex) {
                    voiceGuidance.speak(currentStep.instruction)
                    lastAnnouncedStepIndex = currentIndex
                } else if (distToManeuver in 40f..80f && distToManeuver > 0) {
                    voiceGuidance.speak("Jetzt: ${currentStep.instruction}")
                }
            }
        }

        invalidate()
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
        isRecalculating = true

        lifecycleScope.launch {
            try {
                val updatedWaypoints = currentRoute.waypoints.toMutableList()
                updatedWaypoints[0] = currentLoc.copy(name = "Mein Standort")

                val result = routingRepository.getRoute(updatedWaypoints, TravelMode.CAR)
                result.onSuccess { newRoute ->
                    NavigationStateHolder.updateRoute(newRoute)
                    NavigationStateHolder.updateStepIndex(0)
                    surfaceRenderer.updateRoute(newRoute)
                    voiceGuidance.speak("Route wird neu berechnet")
                    lastAnnouncedStepIndex = -1
                    invalidate()
                }
            } finally {
                isRecalculating = false
            }
        }
    }

    override fun onGetTemplate(): Template {
        val route = NavigationStateHolder.activeRoute.value
        if (route == null || route.steps.isEmpty()) {
            endNavigation()
            return NavigationTemplate.Builder().build()
        }

        val stepIndex = NavigationStateHolder.currentStepIndex.value.coerceIn(0, route.steps.size - 1)
        val currentStep = route.steps[stepIndex]
        val nextStep = if (stepIndex < route.steps.size - 1) route.steps[stepIndex + 1] else null

        // Maneuver
        val maneuver = CarManeuverConverter.toCarManeuver(currentStep.maneuverType, currentStep.maneuverModifier)
        val stepBuilder = Step.Builder(currentStep.instruction)
            .setManeuver(maneuver)

        // Travel Estimates
        val remainingDistMeters = route.totalDistanceMeters.coerceAtLeast(0.0)
        val remainingSeconds = route.totalDurationSeconds.coerceAtLeast(0.0)
        val etaMillis = System.currentTimeMillis() + (remainingSeconds * 1000).toLong()

        val distObj = Distance.create(remainingDistMeters / 1000.0, Distance.UNIT_KILOMETERS)
        val arrivalTime = DateTimeWithZone.create(etaMillis, TimeZone.getDefault())
        val travelEstimate = TravelEstimate.Builder(distObj, arrivalTime).build()

        val stepDistObj = Distance.create(currentStep.distanceMeters / 1000.0, Distance.UNIT_KILOMETERS)

        val routingInfoBuilder = RoutingInfo.Builder()
            .setCurrentStep(stepBuilder.build(), stepDistObj)

        if (nextStep != null) {
            val nextManeuver = CarManeuverConverter.toCarManeuver(nextStep.maneuverType, nextStep.maneuverModifier)
            val nextStepObj = Step.Builder(nextStep.instruction)
                .setManeuver(nextManeuver)
                .build()
            routingInfoBuilder.setNextStep(nextStepObj)
        }

        // Inform Car NavigationManager about the trip
        try {
            val trip = Trip.Builder()
                .addStep(stepBuilder.build(), travelEstimate)
                .setCurrentRoad(currentStep.instruction)
                .build()
            navManager.updateTrip(trip)
        } catch (_: Exception) {}

        // Action Strip
        val isMuted = NavigationStateHolder.isMuted.value
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(if (isMuted) "Ton an" else "Stumm")
                    .setOnClickListener {
                        NavigationStateHolder.toggleMute()
                        voiceGuidance.setMuted(NavigationStateHolder.isMuted.value)
                        invalidate()
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
                    .setTitle("Beenden")
                    .setOnClickListener {
                        endNavigation()
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setNavigationInfo(routingInfoBuilder.build())
            .setDestinationTravelEstimate(travelEstimate)
            .setActionStrip(actionStrip)
            .setBackgroundColor(CarColor.SECONDARY)
            .build()
    }

    private fun endNavigation() {
        NavigationStateHolder.stopNavigation()
        surfaceRenderer.updateRoute(null)
        screenManager.pop()
    }
}
