package com.example.nagivator.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.example.nagivator.car.screens.CarHomeScreen

class NagivatorCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return CarHomeScreen(carContext)
    }
}
