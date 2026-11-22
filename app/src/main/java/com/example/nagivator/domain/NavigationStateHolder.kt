package com.example.nagivator.domain

import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RoadSignal
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TrafficIncident
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NavigationStateHolder {
    private val _currentLocation = MutableStateFlow<LocationPoint?>(null)
    val currentLocation: StateFlow<LocationPoint?> = _currentLocation.asStateFlow()

    private val _activeRoute = MutableStateFlow<RouteResult?>(null)
    val activeRoute: StateFlow<RouteResult?> = _activeRoute.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _currentSpeedLimitKmh = MutableStateFlow<Int?>(null)
    val currentSpeedLimitKmh: StateFlow<Int?> = _currentSpeedLimitKmh.asStateFlow()

    private val _roadSignals = MutableStateFlow<List<RoadSignal>>(emptyList())
    val roadSignals: StateFlow<List<RoadSignal>> = _roadSignals.asStateFlow()

    private val _activeTrafficIncidents = MutableStateFlow<List<TrafficIncident>>(emptyList())
    val activeTrafficIncidents: StateFlow<List<TrafficIncident>> = _activeTrafficIncidents.asStateFlow()

    @Volatile
    private var isSimulatedLocationActive = false

    fun setSimulatedLocationActive(active: Boolean) {
        isSimulatedLocationActive = active
    }

    fun updateLocation(point: LocationPoint, isSimulated: Boolean = false) {
        if (isSimulatedLocationActive && !isSimulated) return
        _currentLocation.value = point
    }

    fun startNavigation(route: RouteResult) {
        _activeRoute.value = route
        _currentStepIndex.value = 0
        _isNavigating.value = true
    }

    fun updateRoute(route: RouteResult) {
        _activeRoute.value = route
    }

    fun updateStepIndex(index: Int) {
        _currentStepIndex.value = index
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    fun updateRoadContext(speedLimitKmh: Int?, signals: List<RoadSignal>) {
        if (speedLimitKmh != null) {
            _currentSpeedLimitKmh.value = speedLimitKmh
        }
        _roadSignals.value = signals
    }

    fun updateTrafficIncidents(incidents: List<TrafficIncident>) {
        _activeTrafficIncidents.value = incidents
    }

    fun stopNavigation() {
        _isNavigating.value = false
        _activeRoute.value = null
        _currentStepIndex.value = 0
        _activeTrafficIncidents.value = emptyList()
    }
}
