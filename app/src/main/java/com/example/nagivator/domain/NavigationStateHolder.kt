package com.example.nagivator.domain

import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
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

    fun updateLocation(point: LocationPoint) {
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

    fun stopNavigation() {
        _isNavigating.value = false
        _activeRoute.value = null
        _currentStepIndex.value = 0
    }
}
