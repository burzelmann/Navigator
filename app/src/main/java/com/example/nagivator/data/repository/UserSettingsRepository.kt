package com.example.nagivator.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.nagivator.domain.model.SpeedPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Immer hell"),
    DARK("Immer dunkel")
}

data class UserSettings(
    val footSpeed: SpeedPreference = SpeedPreference.NORMAL,
    val carSpeed: SpeedPreference = SpeedPreference.NORMAL,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM
)

class UserSettingsRepository private constructor(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs = context.applicationContext.getSharedPreferences("user_settings_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        _settings.value = loadSettings()
    }

    private fun loadSettings(): UserSettings {
        val footName = prefs.getString("foot_speed", SpeedPreference.NORMAL.name)
        val carName = prefs.getString("car_speed", SpeedPreference.NORMAL.name)
        val themeName = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name)

        val foot = try {
            SpeedPreference.valueOf(footName ?: SpeedPreference.NORMAL.name)
        } catch (_: Exception) {
            SpeedPreference.NORMAL
        }

        val car = try {
            SpeedPreference.valueOf(carName ?: SpeedPreference.NORMAL.name)
        } catch (_: Exception) {
            SpeedPreference.NORMAL
        }

        val theme = try {
            AppThemeMode.valueOf(themeName ?: AppThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }

        return UserSettings(footSpeed = foot, carSpeed = car, themeMode = theme)
    }

    fun setFootSpeed(pref: SpeedPreference) {
        prefs.edit().putString("foot_speed", pref.name).apply()
        _settings.update { it.copy(footSpeed = pref) }
    }

    fun setCarSpeed(pref: SpeedPreference) {
        prefs.edit().putString("car_speed", pref.name).apply()
        _settings.update { it.copy(carSpeed = pref) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _settings.update { it.copy(themeMode = mode) }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserSettingsRepository? = null

        fun getInstance(context: Context): UserSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = UserSettingsRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
