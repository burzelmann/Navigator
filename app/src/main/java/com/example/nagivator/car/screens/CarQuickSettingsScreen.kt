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
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.nagivator.car.CarVoiceGuidance
import com.example.nagivator.car.surface.CarMapSurfaceRenderer
import com.example.nagivator.car.surface.MapPerspective
import com.example.nagivator.data.repository.AppThemeMode
import com.example.nagivator.data.repository.MapColorScheme
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.domain.model.SpeedPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android Auto quick settings screen synced bidirectionally with the phone app's ProfileScreen.
 * Uses native Android Auto Toggle switches for binary options and browsable rows opening
 * native Radio-Button lists ([CarOptionPickerScreen]) for multi-choice options.
 */
class CarQuickSettingsScreen(
    carContext: CarContext,
    private val surfaceRenderer: CarMapSurfaceRenderer
) : Screen(carContext), DefaultLifecycleObserver {

    private val userSettingsRepository = UserSettingsRepository.getInstance(carContext)
    private val voiceGuidance = CarVoiceGuidance.getInstance(carContext)
    private var settingsJob: Job? = null
    private var voicesJob: Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        settingsJob = lifecycleScope.launch {
            userSettingsRepository.settings.collectLatest {
                invalidate()
            }
        }
        voicesJob = lifecycleScope.launch {
            voiceGuidance.availableVoices.collectLatest {
                invalidate()
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        settingsJob?.cancel()
        voicesJob?.cancel()
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

    private fun themeSubtitle(mode: AppThemeMode): String = when (mode) {
        AppThemeMode.SYSTEM -> "Folgt den Android-Systemeinstellungen"
        AppThemeMode.LIGHT -> "Karte & App immer im hellen Design"
        AppThemeMode.DARK -> "Karte & App immer im dunklen Design"
    }

    private fun speedSubtitle(speed: SpeedPreference): String = when (speed) {
        SpeedPreference.SLOW -> "Gemütlich / Vorsichtig (+25 % Zeit)"
        SpeedPreference.NORMAL -> "Standard-Tempo (Verkehrsfluss)"
        SpeedPreference.FAST -> "Zügig / Freie Fahrt (-15 % Zeit)"
    }

    private fun openThemePicker() {
        val modes = AppThemeMode.entries
        screenManager.push(
            CarOptionPickerScreen(
                carContext = carContext,
                title = "Darstellung",
                options = modes.map { CarPickerOption(it.label, themeSubtitle(it)) },
                selectedIndexProvider = {
                    modes.indexOf(userSettingsRepository.settings.value.themeMode).coerceAtLeast(0)
                },
                onOptionSelected = { index ->
                    userSettingsRepository.setThemeMode(modes[index])
                    surfaceRenderer.requestRender()
                }
            )
        )
    }

    private fun openNightSchemePicker() {
        val schemes = MapColorScheme.entries
        screenManager.push(
            CarOptionPickerScreen(
                carContext = carContext,
                title = "Nachtschema",
                options = schemes.map { CarPickerOption(it.label, it.description) },
                selectedIndexProvider = {
                    schemes.indexOf(userSettingsRepository.settings.value.mapColorScheme).coerceAtLeast(0)
                },
                onOptionSelected = { index ->
                    userSettingsRepository.setMapColorScheme(schemes[index])
                    surfaceRenderer.requestRender()
                }
            )
        )
    }

    private fun openCarSpeedPicker() {
        val speeds = SpeedPreference.entries
        screenManager.push(
            CarOptionPickerScreen(
                carContext = carContext,
                title = "Fahrtempo (Auto)",
                options = speeds.map { CarPickerOption(it.label, speedSubtitle(it)) },
                selectedIndexProvider = {
                    speeds.indexOf(userSettingsRepository.settings.value.carSpeed).coerceAtLeast(0)
                },
                onOptionSelected = { index ->
                    userSettingsRepository.setCarSpeed(speeds[index])
                }
            )
        )
    }

    private fun openVoicePicker() {
        val voices = voiceGuidance.availableVoices.value
        val options = buildList {
            add(CarPickerOption("Automatisch (Beste HD)", "Wählt beste lokale Offline-Stimme"))
            voices.forEach { voice ->
                add(CarPickerOption(voice.displayName, voice.qualityLabel))
            }
        }
        screenManager.push(
            CarOptionPickerScreen(
                carContext = carContext,
                title = "Stimme (Offline)",
                options = options,
                selectedIndexProvider = {
                    val currentName = userSettingsRepository.settings.value.voiceName
                    if (currentName == null) {
                        0
                    } else {
                        val idx = voices.indexOfFirst { it.name == currentName }
                        if (idx >= 0) idx + 1 else 0
                    }
                },
                onOptionSelected = { index ->
                    val chosenName = if (index <= 0) null else voices.getOrNull(index - 1)?.name
                    userSettingsRepository.setVoiceName(chosenName)
                    voiceGuidance.speakVoicePreview(
                        chosenName,
                        userSettingsRepository.settings.value.voiceSpeechRate
                    )
                }
            )
        )
    }

    override fun onGetTemplate(): Template {
        val settings = userSettingsRepository.settings.value
        val currentTheme = settings.themeMode
        val currentScheme = settings.mapColorScheme
        val dynamicZoom = settings.dynamicZoomEnabled
        val showRoadSignals = settings.showRoadSignals
        val isHeadingUp = settings.carMapPerspective == MapPerspective.HEADING_UP
        val voiceEnabled = !settings.voiceMuted
        val carSpeed = settings.carSpeed

        val toggleRows = listOf(
            Row.Builder()
                .setTitle("Sprachausgabe")
                .addText(if (voiceEnabled) "Aktiv (synchron mit Smartphone)" else "Stumm (synchron mit Smartphone)")
                .setToggle(
                    Toggle.Builder { isChecked ->
                        userSettingsRepository.setVoiceMuted(!isChecked)
                        invalidate()
                    }.setChecked(voiceEnabled).build()
                )
                .build(),
            Row.Builder()
                .setTitle("Fahrtrichtung oben")
                .addText(if (isHeadingUp) "Aktiv – Karte dreht mit" else "Aus – Norden oben")
                .setToggle(
                    Toggle.Builder { isChecked ->
                        val nextPerspective = if (isChecked) {
                            MapPerspective.HEADING_UP
                        } else {
                            MapPerspective.NORTH_UP
                        }
                        userSettingsRepository.setCarMapPerspective(nextPerspective)
                        surfaceRenderer.requestRender()
                        invalidate()
                    }.setChecked(isHeadingUp).build()
                )
                .build(),
            Row.Builder()
                .setTitle("Auto-Zoom")
                .addText("Passt Zoom automatisch an Geschwindigkeit an")
                .setToggle(
                    Toggle.Builder { isChecked ->
                        userSettingsRepository.setDynamicZoomEnabled(isChecked)
                        invalidate()
                    }.setChecked(dynamicZoom).build()
                )
                .build(),
            Row.Builder()
                .setTitle("Kreuzungs-Symbole")
                .addText("Ampeln, Stopp, Vorfahrt achten & Kreisverkehre")
                .setToggle(
                    Toggle.Builder { isChecked ->
                        userSettingsRepository.setShowRoadSignals(isChecked)
                        surfaceRenderer.requestRender()
                        invalidate()
                    }.setChecked(showRoadSignals).build()
                )
                .build()
        )

        val availableVoices = voiceGuidance.availableVoices.value
        val activeVoiceLabel = when (val selectedName = settings.voiceName) {
            null -> "Automatisch (Beste HD)"
            else -> availableVoices.firstOrNull { it.name == selectedName }?.displayName
                ?: "Automatisch (Beste HD)"
        }

        val choiceRows = buildList {
            add(
                Row.Builder()
                    .setTitle("Darstellung")
                    .addText(currentTheme.label)
                    .setBrowsable(true)
                    .setOnClickListener { openThemePicker() }
                    .build()
            )
            add(
                Row.Builder()
                    .setTitle("Nachtschema")
                    .addText("${currentScheme.label} • ${currentScheme.description}")
                    .setBrowsable(true)
                    .setOnClickListener { openNightSchemePicker() }
                    .build()
            )
            add(
                Row.Builder()
                    .setTitle("Fahrtempo (Auto)")
                    .addText("${carSpeed.label} • ${speedSubtitle(carSpeed)}")
                    .setBrowsable(true)
                    .setOnClickListener { openCarSpeedPicker() }
                    .build()
            )
            if (availableVoices.size > 1) {
                add(
                    Row.Builder()
                        .setTitle("Stimme (Offline)")
                        .addText("$activeVoiceLabel • ${settings.voiceSpeechRate.label}")
                        .setBrowsable(true)
                        .setOnClickListener { openVoicePicker() }
                        .build()
                )
            }
        }

        val limit = maxListItems().coerceAtLeast(6)
        val togglesToShow = toggleRows.take(limit)
        val remaining = (limit - togglesToShow.size).coerceAtLeast(0)
        val choicesToShow = choiceRows.take(remaining)

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

        val templateBuilder = ListTemplate.Builder()
            .setTitle("Schnelleinstellungen")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)

        if (togglesToShow.isNotEmpty()) {
            val toggleList = ItemList.Builder().apply {
                togglesToShow.forEach { addItem(it) }
            }.build()
            templateBuilder.addSectionedList(
                SectionedItemList.create(toggleList, "Schalter")
            )
        }

        if (choicesToShow.isNotEmpty()) {
            val choiceList = ItemList.Builder().apply {
                choicesToShow.forEach { addItem(it) }
            }.build()
            templateBuilder.addSectionedList(
                SectionedItemList.create(choiceList, "Auswahl")
            )
        }

        return templateBuilder.build()
    }
}
