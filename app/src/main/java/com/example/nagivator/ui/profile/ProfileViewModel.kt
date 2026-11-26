package com.example.nagivator.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nagivator.car.surface.MapPerspective
import com.example.nagivator.data.local.entity.LearnedRouteEntity
import com.example.nagivator.data.repository.AppThemeMode
import com.example.nagivator.data.repository.LearnedRouteRepository
import com.example.nagivator.data.repository.MapColorScheme
import com.example.nagivator.data.repository.UserSettings
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.data.repository.VoiceSpeechRate
import com.example.nagivator.domain.model.SpeedPreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    val userSettingsRepository = UserSettingsRepository.getInstance(application)
    private val learnedRouteRepository = LearnedRouteRepository.getInstance(application)
    val settings: StateFlow<UserSettings> = userSettingsRepository.settings
    val learnedRoutes: StateFlow<List<LearnedRouteEntity>> = learnedRouteRepository.allLearnedRoutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFootSpeed(pref: SpeedPreference) {
        userSettingsRepository.setFootSpeed(pref)
    }

    fun setCarSpeed(pref: SpeedPreference) {
        userSettingsRepository.setCarSpeed(pref)
    }

    fun setThemeMode(mode: AppThemeMode) {
        userSettingsRepository.setThemeMode(mode)
    }

    fun setMapColorScheme(scheme: MapColorScheme) {
        userSettingsRepository.setMapColorScheme(scheme)
    }

    fun setDynamicZoomEnabled(enabled: Boolean) {
        userSettingsRepository.setDynamicZoomEnabled(enabled)
    }

    fun setShowRoadSignals(enabled: Boolean) {
        userSettingsRepository.setShowRoadSignals(enabled)
    }

    fun setCarMapPerspective(perspective: MapPerspective) {
        userSettingsRepository.setCarMapPerspective(perspective)
    }

    fun setVoiceMuted(muted: Boolean) {
        userSettingsRepository.setVoiceMuted(muted)
    }

    fun setVoiceName(name: String?) {
        userSettingsRepository.setVoiceName(name)
    }

    fun setVoiceSpeechRate(rate: VoiceSpeechRate) {
        userSettingsRepository.setVoiceSpeechRate(rate)
    }

    fun setLearnPreferredRoutes(enabled: Boolean) {
        userSettingsRepository.setLearnPreferredRoutes(enabled)
    }

    fun deleteLearnedRoute(id: Long) {
        viewModelScope.launch {
            learnedRouteRepository.deleteById(id)
        }
    }

    fun deleteAllLearnedRoutes() {
        viewModelScope.launch {
            learnedRouteRepository.deleteAll()
        }
    }
}
