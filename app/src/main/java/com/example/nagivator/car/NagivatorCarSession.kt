package com.example.nagivator.car

import android.content.Intent
import android.content.res.Configuration
import androidx.car.app.Screen
import androidx.car.app.Session
import com.example.nagivator.car.screens.CarHomeScreen

class NagivatorCarSession : Session() {
    private var homeScreen: CarHomeScreen? = null

    override fun onCreateScreen(intent: Intent): Screen {
        val screen = CarHomeScreen(carContext)
        homeScreen = screen
        return screen
    }

    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        super.onCarConfigurationChanged(newConfiguration)
        homeScreen?.surfaceRenderer?.requestRender()
    }
}
