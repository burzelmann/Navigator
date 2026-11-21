package com.example.nagivator.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * ADB broadcast receiver (debug builds only) to start/stop simulated driving on DHU.
 */
class DebugDriveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START -> {
                val speed = intent.getFloatExtra("speed_mps", 13.9f)
                DebugDriveSimulator.start(speed)
            }
            ACTION_OFF_ROUTE -> DebugDriveSimulator.simulateOffRoute()
            ACTION_TELEPORT -> {
                val lat = intent.getFloatExtra("lat", 51.7895f).toDouble()
                val lon = intent.getFloatExtra("lon", 6.1365f).toDouble()
                val speed = intent.getFloatExtra("speed_mps", 11.1f)
                DebugDriveSimulator.teleport(lat, lon, speed)
            }
            ACTION_STOP -> DebugDriveSimulator.stop()
        }
    }

    companion object {
        const val ACTION_START = "com.verhuelsdonk.navigator.DEBUG_DRIVE_START"
        const val ACTION_OFF_ROUTE = "com.verhuelsdonk.navigator.DEBUG_DRIVE_OFF_ROUTE"
        const val ACTION_TELEPORT = "com.verhuelsdonk.navigator.DEBUG_DRIVE_TELEPORT"
        const val ACTION_STOP = "com.verhuelsdonk.navigator.DEBUG_DRIVE_STOP"
    }
}
