package com.example.nagivator.car.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.car.surface.CarMapSurfaceRenderer
import com.example.nagivator.car.surface.MapPerspective
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.domain.NavigationStateHolder

/**
 * Android Auto quick settings screen synced with app preferences (Theme, Map Perspective, Voice Mute).
 */
class CarQuickSettingsScreen(
    carContext: CarContext,
    private val surfaceRenderer: CarMapSurfaceRenderer
) : Screen(carContext) {

    private val userSettingsRepository = UserSettingsRepository.getInstance(carContext)

    override fun onGetTemplate(): Template {
        val settings = userSettingsRepository.settings.value
        val currentTheme = settings.themeMode
        val currentScheme = settings.mapColorScheme
        val dynamicZoom = settings.dynamicZoomEnabled
        val showRoadSignals = settings.showRoadSignals
        val currentPerspective = surfaceRenderer.getPerspective()
        val isMuted = NavigationStateHolder.isMuted.value

        val listBuilder = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Darstellung: ${currentTheme.label}")
                    .addText("Tippen zum Wechseln (System • Hell • Dunkel)")
                    .setOnClickListener {
                        val nextTheme = CarNavigationHelper.nextThemeMode(currentTheme)
                        userSettingsRepository.setThemeMode(nextTheme)
                        surfaceRenderer.requestRender()
                        invalidate()
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Nachtschema: ${currentScheme.label}")
                    .addText(currentScheme.description)
                    .setOnClickListener {
                        val nextScheme = CarNavigationHelper.nextMapColorScheme(currentScheme)
                        userSettingsRepository.setMapColorScheme(nextScheme)
                        surfaceRenderer.requestRender()
                        invalidate()
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(
                        "Kartenansicht: ${if (currentPerspective == MapPerspective.HEADING_UP) "Fahrtrichtung oben" else "Norden oben"}"
                    )
                    .addText("Wird für künftige Fahrten gespeichert")
                    .setOnClickListener {
                        surfaceRenderer.togglePerspective()
                        invalidate()
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Auto-Zoom: ${if (dynamicZoom) "Aktiv" else "Aus"}")
                    .addText("Passt Zoom automatisch an Geschwindigkeit an")
                    .setOnClickListener {
                        userSettingsRepository.setDynamicZoomEnabled(!dynamicZoom)
                        invalidate()
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Kreuzungs-Symbole: ${if (showRoadSignals) "Aktiv" else "Aus"}")
                    .addText("Ampeln, Stopp, Vorfahrt achten & Kreisverkehre")
                    .setOnClickListener {
                        userSettingsRepository.setShowRoadSignals(!showRoadSignals)
                        surfaceRenderer.requestRender()
                        invalidate()
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Sprachausgabe: ${if (isMuted) "Stumm" else "Aktiv"}")
                    .addText("Tippen zum Umschalten")
                    .setOnClickListener {
                        NavigationStateHolder.toggleMute()
                        invalidate()
                    }
                    .build()
            )

        return ListTemplate.Builder()
            .setTitle("Schnelleinstellungen")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
