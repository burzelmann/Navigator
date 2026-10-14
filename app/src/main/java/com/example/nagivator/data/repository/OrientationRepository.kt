package com.example.nagivator.data.repository

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

class OrientationRepository(context: Context) {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    fun getOrientationUpdates(): Flow<Float> = callbackFlow {
        if (rotationSensor == null) {
            trySend(0f)
            close()
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val orientationValues = FloatArray(3)
        var lastEmittedAzimuth = -1f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationValues)
                    
                    var azimuthDeg = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                    if (azimuthDeg < 0f) {
                        azimuthDeg += 360f
                    }

                    // Only emit if angle changed by at least 2.5 degrees to prevent main-thread/Compose flooding
                    if (lastEmittedAzimuth < 0f || abs(azimuthDeg - lastEmittedAzimuth) >= 2.5f) {
                        lastEmittedAzimuth = azimuthDeg
                        trySend(azimuthDeg)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
