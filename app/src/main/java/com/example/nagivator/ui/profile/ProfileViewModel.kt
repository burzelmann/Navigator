package com.example.nagivator.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.nagivator.car.surface.MapPerspective
import com.example.nagivator.data.repository.AppThemeMode
import com.example.nagivator.data.repository.MapColorScheme
import com.example.nagivator.data.repository.UserSettings
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.domain.model.SpeedPreference
import kotlinx.coroutines.flow.StateFlow

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    val userSettingsRepository = UserSettingsRepository.getInstance(application)
    val settings: StateFlow<UserSettings> = userSettingsRepository.settings

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
}
