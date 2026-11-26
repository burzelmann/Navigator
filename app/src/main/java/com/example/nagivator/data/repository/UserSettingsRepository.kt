package com.example.nagivator.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.nagivator.car.surface.MapPerspective
import com.example.nagivator.domain.NavigationStateHolder
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

enum class MapColorScheme(val label: String, val description: String) {
    GRAPHITE("Graphit", "Neutrales Dunkelgrau"),
    MIDNIGHT_BLUE("Mitternachtsblau", "Tiefes Blau für Nachtfahrten"),
    WARM_AMBER("Warm-Bernstein", "Augenschonend, wenig Blauanteil"),
    HIGH_CONTRAST("Hochkontrast", "Maximale Straßenerkennung")
}

enum class VoiceSpeechRate(val label: String, val rate: Float) {
    RELAXED("Ruhig", 0.92f),
    NORMAL("Normal", 1.0f),
    BRISK("Zügig", 1.12f)
}

data class ParkedCarLocation(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val label: String = "Geparktes Auto"
)

data class UserSettings(
    val footSpeed: SpeedPreference = SpeedPreference.NORMAL,
    val carSpeed: SpeedPreference = SpeedPreference.NORMAL,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val mapColorScheme: MapColorScheme = MapColorScheme.GRAPHITE,
    val carMapPerspective: MapPerspective = MapPerspective.NORTH_UP,
    val dynamicZoomEnabled: Boolean = true,
    val showRoadSignals: Boolean = true,
    val voiceMuted: Boolean = false,
    val voiceName: String? = null,
    val voiceSpeechRate: VoiceSpeechRate = VoiceSpeechRate.NORMAL,
    val learnPreferredRoutes: Boolean = false,
    val parkedCar: ParkedCarLocation? = null
)

class UserSettingsRepository private constructor(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs = context.applicationContext.getSharedPreferences("user_settings_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
        NavigationStateHolder.setMuted(_settings.value.voiceMuted)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val loaded = loadSettings()
        _settings.value = loaded
        NavigationStateHolder.setMuted(loaded.voiceMuted)
    }

    private fun loadSettings(): UserSettings {
        val footName = prefs.getString("foot_speed", SpeedPreference.NORMAL.name)
        val carName = prefs.getString("car_speed", SpeedPreference.NORMAL.name)
        val themeName = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name)
        val schemeName = prefs.getString("map_color_scheme", MapColorScheme.GRAPHITE.name)
        val perspectiveName = prefs.getString("car_map_perspective", MapPerspective.NORTH_UP.name)
        val dynamicZoom = prefs.getBoolean("dynamic_zoom_enabled", true)
        val showRoadSignals = prefs.getBoolean("show_road_signals", true)
        val voiceMuted = prefs.getBoolean("voice_muted", false)
        val voiceName = prefs.getString("voice_name", null)?.takeIf { it.isNotBlank() }
        val voiceRateName = prefs.getString("voice_speech_rate", VoiceSpeechRate.NORMAL.name)
        val learnPreferredRoutes = prefs.getBoolean("learn_preferred_routes", false)
        val parkedCar = if (prefs.contains("parked_car_lat") && prefs.contains("parked_car_lon")) {
            ParkedCarLocation(
                latitude = prefs.getFloat("parked_car_lat", 0f).toDouble(),
                longitude = prefs.getFloat("parked_car_lon", 0f).toDouble(),
                timestampMs = prefs.getLong("parked_car_ts", 0L),
                label = prefs.getString("parked_car_label", "Geparktes Auto") ?: "Geparktes Auto"
            )
        } else null

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

        val scheme = try {
            MapColorScheme.valueOf(schemeName ?: MapColorScheme.GRAPHITE.name)
        } catch (_: Exception) {
            MapColorScheme.GRAPHITE
        }

        val perspective = try {
            MapPerspective.valueOf(perspectiveName ?: MapPerspective.NORTH_UP.name)
        } catch (_: Exception) {
            MapPerspective.NORTH_UP
        }

        val voiceRate = try {
            VoiceSpeechRate.valueOf(voiceRateName ?: VoiceSpeechRate.NORMAL.name)
        } catch (_: Exception) {
            VoiceSpeechRate.NORMAL
        }

        return UserSettings(
            footSpeed = foot,
            carSpeed = car,
            themeMode = theme,
            mapColorScheme = scheme,
            carMapPerspective = perspective,
            dynamicZoomEnabled = dynamicZoom,
            showRoadSignals = showRoadSignals,
            voiceMuted = voiceMuted,
            voiceName = voiceName,
            voiceSpeechRate = voiceRate,
            learnPreferredRoutes = learnPreferredRoutes,
            parkedCar = parkedCar
        )
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

    fun setMapColorScheme(scheme: MapColorScheme) {
        prefs.edit().putString("map_color_scheme", scheme.name).apply()
        _settings.update { it.copy(mapColorScheme = scheme) }
    }

    fun setCarMapPerspective(perspective: MapPerspective) {
        prefs.edit().putString("car_map_perspective", perspective.name).apply()
        _settings.update { it.copy(carMapPerspective = perspective) }
    }

    fun setDynamicZoomEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_zoom_enabled", enabled).apply()
        _settings.update { it.copy(dynamicZoomEnabled = enabled) }
    }

    fun setShowRoadSignals(enabled: Boolean) {
        prefs.edit().putBoolean("show_road_signals", enabled).apply()
        _settings.update { it.copy(showRoadSignals = enabled) }
    }

    fun setVoiceMuted(muted: Boolean) {
        prefs.edit().putBoolean("voice_muted", muted).apply()
        NavigationStateHolder.setMuted(muted)
        _settings.update { it.copy(voiceMuted = muted) }
    }

    fun setVoiceName(name: String?) {
        val clean = name?.takeIf { it.isNotBlank() }
        prefs.edit().apply {
            if (clean == null) remove("voice_name") else putString("voice_name", clean)
        }.apply()
        _settings.update { it.copy(voiceName = clean) }
    }

    fun setVoiceSpeechRate(rate: VoiceSpeechRate) {
        prefs.edit().putString("voice_speech_rate", rate.name).apply()
        _settings.update { it.copy(voiceSpeechRate = rate) }
    }

    fun setLearnPreferredRoutes(enabled: Boolean) {
        prefs.edit().putBoolean("learn_preferred_routes", enabled).apply()
        _settings.update { it.copy(learnPreferredRoutes = enabled) }
    }

    fun saveParkedCar(
        latitude: Double,
        longitude: Double,
        label: String = "Geparktes Auto",
        timestampMs: Long = System.currentTimeMillis()
    ) {
        prefs.edit()
            .putFloat("parked_car_lat", latitude.toFloat())
            .putFloat("parked_car_lon", longitude.toFloat())
            .putLong("parked_car_ts", timestampMs)
            .putString("parked_car_label", label)
            .apply()
        _settings.update {
            it.copy(parkedCar = ParkedCarLocation(latitude, longitude, timestampMs, label))
        }
    }

    fun clearParkedCar() {
        prefs.edit()
            .remove("parked_car_lat")
            .remove("parked_car_lon")
            .remove("parked_car_ts")
            .remove("parked_car_label")
            .apply()
        _settings.update { it.copy(parkedCar = null) }
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
