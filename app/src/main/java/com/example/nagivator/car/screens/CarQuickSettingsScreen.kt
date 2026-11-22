package com.example.nagivator.car.screens

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.car.CarVoiceGuidance
import com.example.nagivator.car.surface.CarMapSurfaceRenderer
import com.example.nagivator.car.surface.MapPerspective
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.domain.model.SpeedPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android Auto quick settings screen synced bidirectionally with the phone app's ProfileScreen:
 * Theme, Night Map Scheme, Map Perspective, Dynamic Auto-Zoom, Road Signals, Voice Mute, and Car Speed.
 */
class CarQuickSettingsScreen(
    carContext: CarContext,
    private val surfaceRenderer: CarMapSurfaceRenderer
) : Screen(carContext), DefaultLifecycleObserver {

    private val userSettingsRepository = UserSettingsRepository.getInstance(carContext)
    private val voiceGuidance = CarVoiceGuidance.getInstance(carContext)
    private var settingsJob: Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        settingsJob = lifecycleScope.launch {
            userSettingsRepository.settings.collectLatest {
                invalidate()
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        settingsJob?.cancel()
    }

    private fun maxListItems(): Int {
        return try {
            if (carContext.carAppApiLevel >= 2) {
                carContext.getCarService(ConstraintManager::class.java)
                    .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
            } else {
                6
            }
        } catch (_: Exception) {
            6
        }
    }

    private fun nextSpeedPreference(current: SpeedPreference): SpeedPreference {
        val all = SpeedPreference.entries
        return all[(all.indexOf(current) + 1) % all.size]
    }

    override fun onGetTemplate(): Template {
        val settings = userSettingsRepository.settings.value
        val currentTheme = settings.themeMode
        val currentScheme = settings.mapColorScheme
        val dynamicZoom = settings.dynamicZoomEnabled
        val showRoadSignals = settings.showRoadSignals
        val currentPerspective = settings.carMapPerspective
        val isMuted = settings.voiceMuted
        val carSpeed = settings.carSpeed

        val rows = listOf(
            Row.Builder()
                .setTitle("Darstellung: ${currentTheme.label}")
                .addText("Tippen zum Wechseln (System • Hell • Dunkel)")
                .setOnClickListener {
                    val nextTheme = CarNavigationHelper.nextThemeMode(currentTheme)
                    userSettingsRepository.setThemeMode(nextTheme)
                    surfaceRenderer.requestRender()
                    invalidate()
                }
                .build(),
            Row.Builder()
                .setTitle("Nachtschema: ${currentScheme.label}")
                .addText(currentScheme.description)
                .setOnClickListener {
                    val nextScheme = CarNavigationHelper.nextMapColorScheme(currentScheme)
                    userSettingsRepository.setMapColorScheme(nextScheme)
                    surfaceRenderer.requestRender()
                    invalidate()
                }
                .build(),
            Row.Builder()
                .setTitle(
                    "Kartenansicht: ${if (currentPerspective == MapPerspective.HEADING_UP) "Fahrtrichtung oben" else "Norden oben"}"
                )
                .addText("Wird für künftige Fahrten gespeichert")
                .setOnClickListener {
                    surfaceRenderer.togglePerspective()
                    invalidate()
                }
                .build(),
            Row.Builder()
                .setTitle("Auto-Zoom: ${if (dynamicZoom) "Aktiv" else "Aus"}")
                .addText("Passt Zoom automatisch an Geschwindigkeit an")
                .setOnClickListener {
                    userSettingsRepository.setDynamicZoomEnabled(!dynamicZoom)
                    invalidate()
                }
                .build(),
            Row.Builder()
                .setTitle("Kreuzungs-Symbole: ${if (showRoadSignals) "Aktiv" else "Aus"}")
                .addText("Ampeln, Stopp, Vorfahrt achten & Kreisverkehre")
                .setOnClickListener {
                    userSettingsRepository.setShowRoadSignals(!showRoadSignals)
                    surfaceRenderer.requestRender()
                    invalidate()
                }
                .build(),
            Row.Builder()
                .setTitle("Sprachausgabe: ${if (isMuted) "Stumm" else "Aktiv"}")
                .addText("Synchron mit der Smartphone-App")
                .setOnClickListener {
                    userSettingsRepository.setVoiceMuted(!isMuted)
                    invalidate()
                }
                .build(),
            Row.Builder()
                .setTitle("Fahrtempo (Auto): ${carSpeed.label}")
                .addText("Beeinflusst die berechnete Ankunftszeit (ETA)")
                .setOnClickListener {
                    userSettingsRepository.setCarSpeed(nextSpeedPreference(carSpeed))
                    invalidate()
                }
                .build()
        )

        val limit = maxListItems().coerceAtLeast(6)
        val listBuilder = ItemList.Builder()
        rows.take(limit).forEach { listBuilder.addItem(it) }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Testton")
                    .setOnClickListener {
                        voiceGuidance.speakTestPrompt()
                        CarToast.makeText(
                            carContext,
                            "Testansage läuft – jetzt Lautstärke regeln",
                            CarToast.LENGTH_LONG
                        ).show()
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("Schnelleinstellungen")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setSingleList(listBuilder.build())
            .build()
    }
}
