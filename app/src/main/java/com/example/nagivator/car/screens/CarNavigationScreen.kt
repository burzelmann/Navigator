package com.example.nagivator.car.screens

import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.CarToast
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
import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.car.CarVoiceGuidance
import com.example.nagivator.car.surface.CarMapSurfaceRenderer
import com.example.nagivator.car.surface.MapPerspective
import com.example.nagivator.data.repository.LearnedRouteRepository
import com.example.nagivator.data.repository.RoutingRepository
import com.example.nagivator.data.repository.TrafficRepository
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.RouteDeviationTracker
import com.example.nagivator.domain.TurnGuidanceEngine
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TrafficIncident
import com.example.nagivator.domain.model.TrafficIncidentType
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.TimeZone

class CarNavigationScreen(
    carContext: CarContext,
    private val surfaceRenderer: CarMapSurfaceRenderer
) : Screen(carContext), DefaultLifecycleObserver {

    private val navManager = carContext.getCarService(NavigationManager::class.java)
    private val voiceGuidance = CarVoiceGuidance.getInstance(carContext)
    private val routingRepository = RoutingRepository()
    private val trafficRepository = TrafficRepository()
    private val learnedRouteRepository = LearnedRouteRepository.getInstance(carContext)
    private val deviationTracker = RouteDeviationTracker()

    private var locationJob: Job? = null
    private var trafficJob: Job? = null
    private var isRecalculating = false
    private val guidanceEngine = TurnGuidanceEngine()
    private val offRouteDetector = com.example.nagivator.domain.OffRouteDetector()
    private var hasArrived = false
    private val announcedIncidentIds = mutableSetOf<String>()

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        NavigationStateHolder.setCarNavigationActive(true)
        navManager.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() {
                endNavigation()
            }
        })
        navManager.navigationStarted()

        // Sync initial route with surface renderer and skip initial "depart" step if a turn follows
        val initialRoute = NavigationStateHolder.activeRoute.value
        surfaceRenderer.updateRoute(initialRoute)
        if (initialRoute != null) {
            if (UserSettingsRepository.getInstance(carContext).settings.value.learnPreferredRoutes) {
                deviationTracker.startTrip(initialRoute)
            }
            if (NavigationStateHolder.currentStepIndex.value == 0) {
                val startIdx = CarNavigationHelper.initialStepIndex(initialRoute.steps)
                if (startIdx != 0) {
                    NavigationStateHolder.updateStepIndex(startIdx)
                }
            }
        }

        // Observe live GPS updates during navigation
        locationJob = lifecycleScope.launch {
            NavigationStateHolder.currentLocation.collectLatest { loc ->
                if (loc != null) {
                    onLocationUpdated(loc)
                }
            }
        }

        // Observe external stop of navigation (e.g. from phone)
        lifecycleScope.launch {
            NavigationStateHolder.isNavigating.collectLatest { navigating ->
                if (!navigating) {
                    endNavigation()
                }
            }
        }

        // Sync mute state changes from Quick Settings or phone
        lifecycleScope.launch {
            NavigationStateHolder.isMuted.collectLatest { muted ->
                voiceGuidance.setMuted(muted)
                invalidate()
            }
        }

        // Periodic Autobahn traffic check (warnings + automatic re-routing on closures)
        trafficJob = lifecycleScope.launch {
            while (isActive && !hasArrived) {
                checkAutobahnTraffic()
                delay(TRAFFIC_POLL_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        NavigationStateHolder.setCarNavigationActive(false)
        locationJob?.cancel()
        trafficJob?.cancel()
        navManager.navigationEnded()
    }

    private suspend fun checkAutobahnTraffic() {
        val route = NavigationStateHolder.activeRoute.value ?: return
        val incidents = runCatching { trafficRepository.fetchIncidentsOnRoute(route) }.getOrNull() ?: return
        NavigationStateHolder.updateTrafficIncidents(incidents)
        surfaceRenderer.requestRender()

        val firstClosure = incidents.firstOrNull {
            (it.type == TrafficIncidentType.CLOSURE || it.isBlocked) && announcedIncidentIds.add(it.id)
        }
        if (firstClosure != null) {
            handleClosureReroute(firstClosure, route)
            return
        }

        val newWarnings = incidents.filter { announcedIncidentIds.add(it.id) }
        if (newWarnings.isNotEmpty()) {
            val first = newWarnings.first()
            val roadPrefix = if (first.roadId.isNotBlank() && first.roadId != "Strecke") {
                "auf ${first.roadId}"
            } else {
                "voraus"
            }
            val roadworksCount = newWarnings.count { it.type == TrafficIncidentType.ROADWORK }
            val delayMin = (first.delaySeconds ?: 0) / 60
            val msg = when {
                delayMin >= 2 -> "Achtung $roadPrefix: Stau mit $delayMin Minuten Verzögerung"
                roadworksCount > 1 -> "Achtung $roadPrefix: $roadworksCount Baustellen voraus"
                newWarnings.size > 1 -> "Achtung $roadPrefix: ${newWarnings.size} Verkehrsmeldungen voraus"
                first.type == TrafficIncidentType.ROADWORK -> "Achtung $roadPrefix: Baustelle voraus"
                else -> "Achtung $roadPrefix: ${first.title.ifBlank { "Verkehrsmeldung voraus" }}"
            }
            voiceGuidance.speak(msg)
            CarToast.makeText(carContext, msg, CarToast.LENGTH_LONG).show()
        }
    }

    private suspend fun handleClosureReroute(closure: TrafficIncident, currentRoute: RouteResult) {
        val start = NavigationStateHolder.currentLocation.value
            ?: currentRoute.geometryPoints.firstOrNull()
            ?: return
        val dest = currentRoute.waypoints.lastOrNull()
            ?: currentRoute.geometryPoints.lastOrNull()
            ?: return

        val alertMsg = "Sperrung auf ${closure.roadId} voraus. Berechne Ausweichroute."
        deviationTracker.markForcedClosureReroute()
        voiceGuidance.speak(alertMsg)
        CarToast.makeText(carContext, alertMsg, CarToast.LENGTH_LONG).show()

        if (isRecalculating) return
        isRecalculating = true
        val remainingWaypoints = if (currentRoute.waypoints.size >= 2) {
            listOf(start) + currentRoute.waypoints.drop(1)
        } else {
            listOf(start, dest)
        }
        try {
            val result = routingRepository.getAlternativeRouteAvoiding(
                waypoints = remainingWaypoints,
                blockedPoint = LocationPoint(closure.latitude, closure.longitude),
                mode = TravelMode.CAR
            )
            result.onSuccess { newRoute ->
                if (!TrafficRepository.isPlausibleClosureDetour(newRoute.totalDistanceMeters, currentRoute.totalDistanceMeters)) {
                    CarToast.makeText(carContext, "Sperrung voraus – keine sinnvolle Umfahrung gefunden", CarToast.LENGTH_LONG).show()
                    return@onSuccess
                }
                NavigationStateHolder.updateRoute(newRoute)
                NavigationStateHolder.updateStepIndex(CarNavigationHelper.initialStepIndex(newRoute.steps))
                surfaceRenderer.updateRoute(newRoute)
                guidanceEngine.reset()
                CarToast.makeText(carContext, "Ausweichroute aktiv", CarToast.LENGTH_SHORT).show()
                invalidate()
            }
        } finally {
            isRecalculating = false
        }
    }

    private fun onLocationUpdated(loc: LocationPoint) {
        val route = NavigationStateHolder.activeRoute.value ?: return
        val steps = route.steps
        val currentIndex = NavigationStateHolder.currentStepIndex.value

        surfaceRenderer.updateLocation(loc)
        if (UserSettingsRepository.getInstance(carContext).settings.value.learnPreferredRoutes) {
            deviationTracker.recordLocation(loc)
        }

        // 1. Fast Off-Route Detection (heading + lateral + severe tiers) & Trailing Polyline Trimming
        if (route.geometryPoints.isNotEmpty() && !hasArrived) {
            val eval = offRouteDetector.evaluate(loc, route.geometryPoints, TravelMode.CAR)
            if (eval.shouldReroute && !isRecalculating) {
                recalculateRouteOffRoute(loc, route)
                return
            } else if (!eval.isOffRoute) {
                val closestIdx = eval.closestSegmentIndex
                if (closestIdx > 0 && closestIdx < route.geometryPoints.size - 1) {
                    val trimmedPoints = route.geometryPoints.subList(closestIdx, route.geometryPoints.size)
                    if (trimmedPoints.size >= 2 && trimmedPoints.size != route.geometryPoints.size) {
                        val updatedRoute = route.copy(geometryPoints = trimmedPoints)
                        NavigationStateHolder.updateRoute(updatedRoute)
                        surfaceRenderer.updateRoute(updatedRoute)
                    }
                }
            }
        }

        // 2. Destination Arrival Detection (independent of intermediate step index)
        val destinationLoc = steps.lastOrNull()?.maneuverLocation ?: route.geometryPoints.lastOrNull()
        if (destinationLoc != null && !hasArrived) {
            val destResults = FloatArray(1)
            Location.distanceBetween(
                loc.latitude, loc.longitude,
                destinationLoc.latitude, destinationLoc.longitude,
                destResults
            )
            if (destResults[0] < ARRIVAL_THRESHOLD_METERS) {
                hasArrived = true
                if (UserSettingsRepository.getInstance(carContext).settings.value.learnPreferredRoutes) {
                    deviationTracker.evaluateAtArrival()?.let { outcome ->
                        lifecycleScope.launch {
                            runCatching { learnedRouteRepository.recordTripOutcome(outcome) }
                        }
                    }
                }
                voiceGuidance.speak("Sie haben Ihr Ziel erreicht")
                CarToast.makeText(carContext, "Ziel erreicht!", CarToast.LENGTH_LONG).show()
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(2500L)
                    endNavigation()
                }
                return
            }
        }

        // 3. Maneuver Step Progression & Voice Announcements
        if (steps.isNotEmpty() && currentIndex < steps.size && !hasArrived) {
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

                // Step completion (speed-adaptive threshold + recovery if maneuvers were skipped at speed / GPS gap)
                val stepCompletionRadius = if (loc.speedMps > 20f) 65f else 45f
                var targetIdx: Int? = null
                if (currentIndex < steps.size - 1) {
                    if (distToManeuver < stepCompletionRadius) {
                        targetIdx = currentIndex + 1
                    } else if (distToManeuver > 150f) {
                        for (k in (currentIndex + 1) until steps.size) {
                            val kLoc = steps[k].maneuverLocation ?: continue
                            Location.distanceBetween(loc.latitude, loc.longitude, kLoc.latitude, kLoc.longitude, distResults)
                            if (distResults[0] < 60f) {
                                targetIdx = (k + 1).coerceAtMost(steps.size - 1)
                                break
                            }
                        }
                    }
                }

                if (targetIdx != null && targetIdx != currentIndex) {
                    NavigationStateHolder.updateStepIndex(targetIdx)
                    invalidate()
                    return
                }

                // Speed-adaptive 3-stage voice guidance (Time-to-Maneuver, landmarks, compound turns)
                val nextStep = steps.getOrNull(currentIndex + 1)
                val prompt = guidanceEngine.evaluate(
                    stepIndex = currentIndex,
                    currentStep = currentStep,
                    nextStep = nextStep,
                    distanceToManeuverMeters = distToManeuver,
                    speedMps = loc.speedMps,
                    travelMode = TravelMode.CAR,
                    roadSignals = NavigationStateHolder.roadSignals.value
                )
                if (!prompt.isNullOrBlank()) {
                    voiceGuidance.speak(prompt)
                }
            }
        }

        invalidate()
    }

    private fun recalculateRouteOffRoute(currentLoc: LocationPoint, currentRoute: RouteResult) {
        if (currentRoute.waypoints.isEmpty()) return
        isRecalculating = true
        offRouteDetector.markRecalculateStarted()
        // Immediate silent visual loading indicator in the Android Auto navigation banner
        invalidate()

        lifecycleScope.launch {
            try {
                val updatedWaypoints = currentRoute.waypoints.toMutableList()
                updatedWaypoints[0] = currentLoc.copy(name = "Mein Standort")

                val result = routingRepository.getRoute(updatedWaypoints, TravelMode.CAR)
                result.onSuccess { newRoute ->
                    if (newRoute.isOfflineFallback && !currentRoute.isOfflineFallback) {
                        CarToast.makeText(
                            carContext,
                            "Kein Netz – bestehende Route bleibt aktiv",
                            CarToast.LENGTH_SHORT
                        ).show()
                        return@onSuccess
                    }
                    NavigationStateHolder.updateRoute(newRoute)
                    NavigationStateHolder.updateStepIndex(CarNavigationHelper.initialStepIndex(newRoute.steps))
                    surfaceRenderer.updateRoute(newRoute)
                    guidanceEngine.reset()
                }
            } finally {
                isRecalculating = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val route = NavigationStateHolder.activeRoute.value
        if (route == null || route.steps.isEmpty()) {
            endNavigation()
            val fallbackActionStrip = ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("Zurück")
                        .setOnClickListener { endNavigation() }
                        .build()
                )
                .build()
            return NavigationTemplate.Builder()
                .setActionStrip(fallbackActionStrip)
                .setBackgroundColor(CarColor.SECONDARY)
                .build()
        }

        val stepIndex = NavigationStateHolder.currentStepIndex.value.coerceIn(0, route.steps.size - 1)
        val currentStep = route.steps[stepIndex]
        // Skip redundant "exit roundabout" / "exit rotary" step in the sub-banner so the next real turn is shown
        val nextStep = ((stepIndex + 1) until route.steps.size)
            .map { route.steps[it] }
            .firstOrNull { s ->
                val t = s.maneuverType.lowercase().trim()
                t != "exit roundabout" && t != "exit rotary"
            }

        // Maneuver (with roundabout exit number + native lane guidance)
        val maneuver = CarManeuverConverter.toCarManeuver(
            type = currentStep.maneuverType,
            modifier = currentStep.maneuverModifier,
            exitNumber = currentStep.roundaboutExit,
            carContext = carContext
        )
        val instruction = currentStep.instruction.ifBlank { "Route folgen" }
        val stepBuilder = Step.Builder(instruction)
            .setManeuver(maneuver)

        if (currentStep.lanes.isNotEmpty()) {
            val carLanes = CarManeuverConverter.toCarLanes(currentStep.lanes)
            val lanesIcon = CarManeuverConverter.renderLanesIcon(currentStep.lanes)
            if (carLanes.isNotEmpty() && lanesIcon != null) {
                carLanes.forEach { stepBuilder.addLane(it) }
                stepBuilder.setLanesImage(lanesIcon)
            }
        }

        // Travel Estimates (dynamic remaining distance + ETA)
        val currentLoc = NavigationStateHolder.currentLocation.value
        val remainingDistMeters = if (route.geometryPoints.isNotEmpty()) {
            CarNavigationHelper.remainingPolylineMeters(currentLoc, route.geometryPoints)
        } else {
            route.totalDistanceMeters.coerceAtLeast(0.0)
        }
        val remainingSeconds = CarNavigationHelper.estimateRemainingSeconds(route, remainingDistMeters)
        val etaMillis = System.currentTimeMillis() + (remainingSeconds * 1000).toLong()

        val distObj = CarNavigationHelper.createCarDistance(remainingDistMeters)
        val arrivalTime = DateTimeWithZone.create(etaMillis, TimeZone.getDefault())
        val travelEstimate = TravelEstimate.Builder(distObj, arrivalTime)
            .setRemainingTimeSeconds(remainingSeconds.toLong())
            .build()

        // Live distance to upcoming maneuver
        val maneuverLoc = currentStep.maneuverLocation
        val stepDistMeters = if (currentLoc != null && maneuverLoc != null) {
            CarNavigationHelper.haversineMeters(
                currentLoc.latitude, currentLoc.longitude,
                maneuverLoc.latitude, maneuverLoc.longitude
            )
        } else {
            currentStep.distanceMeters.coerceAtLeast(0.0)
        }
        val stepDistObj = CarNavigationHelper.createCarDistance(stepDistMeters)

        val routingInfoBuilder = RoutingInfo.Builder()
            .setCurrentStep(stepBuilder.build(), stepDistObj)

        if (nextStep != null) {
            val nextManeuver = CarManeuverConverter.toCarManeuver(
                type = nextStep.maneuverType,
                modifier = nextStep.maneuverModifier,
                exitNumber = nextStep.roundaboutExit,
                carContext = carContext
            )
            val nextInstruction = nextStep.instruction.ifBlank { "Route folgen" }
            val nextStepObj = Step.Builder(nextInstruction)
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
                        val nextMuted = !NavigationStateHolder.isMuted.value
                        UserSettingsRepository.getInstance(carContext).setVoiceMuted(nextMuted)
                        voiceGuidance.setMuted(nextMuted)
                        invalidate()
                    }
                    .build()
            )
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
                    .setTitle("Optionen")
                    .setOnClickListener {
                        screenManager.push(CarQuickSettingsScreen(carContext, surfaceRenderer))
                    }
                    .build()
            )
            .build()

        val navigationInfo = if (isRecalculating) {
            RoutingInfo.Builder()
                .setLoading(true)
                .build()
        } else {
            routingInfoBuilder.build()
        }

        return NavigationTemplate.Builder()
            .setNavigationInfo(navigationInfo)
            .setDestinationTravelEstimate(travelEstimate)
            .setActionStrip(actionStrip)
            .setBackgroundColor(CarColor.SECONDARY)
            .build()
    }

    private fun endNavigation() {
        val currentLoc = NavigationStateHolder.currentLocation.value
        val activeRoute = NavigationStateHolder.activeRoute.value
        if (currentLoc != null) {
            val destLabel = activeRoute?.waypoints?.lastOrNull()?.name?.ifBlank { null } ?: "Geparktes Auto"
            UserSettingsRepository.getInstance(carContext).saveParkedCar(
                latitude = currentLoc.latitude,
                longitude = currentLoc.longitude,
                label = destLabel
            )
        }
        NavigationStateHolder.stopNavigation()
        surfaceRenderer.updateRoute(null)
        screenManager.pop()
    }

    companion object {
        private const val ARRIVAL_THRESHOLD_METERS = 35f
        private const val TRAFFIC_POLL_INTERVAL_MS = 180_000L
    }
}
