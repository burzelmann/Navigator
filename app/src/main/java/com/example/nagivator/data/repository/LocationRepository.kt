package com.example.nagivator.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.example.nagivator.domain.model.LocationPoint
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private val prefs = appContext.getSharedPreferences("last_known_location_prefs", Context.MODE_PRIVATE)

    fun getLastSavedLocation(): LocationPoint? {
        val lat = prefs.getFloat("last_lat", Float.MIN_VALUE)
        val lon = prefs.getFloat("last_lon", Float.MIN_VALUE)
        if (lat == Float.MIN_VALUE || lon == Float.MIN_VALUE) return null
        return LocationPoint(
            latitude = lat.toDouble(),
            longitude = lon.toDouble(),
            name = "Aktueller Standort"
        )
    }

    private fun saveLocationToPrefs(lat: Double, lon: Double) {
        prefs.edit()
            .putFloat("last_lat", lat.toFloat())
            .putFloat("last_lon", lon.toFloat())
            .apply()
    }

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long = 1000L): Flow<LocationPoint> = callbackFlow {
        // 1. Immediately emit last saved location from SharedPreferences (0ms delay)
        getLastSavedLocation()?.let { savedLoc ->
            trySend(savedLoc)
        }

        // 2. Immediately query System Fused Location lastLocation (< 10ms)
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                saveLocationToPrefs(loc.latitude, loc.longitude)
                trySend(
                    LocationPoint(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        name = "Aktueller Standort",
                        bearing = if (loc.hasBearing()) loc.bearing else 0f,
                        speedMps = if (loc.hasSpeed()) loc.speed else 0f
                    )
                )
            }
        }

        // 3. Fast coarse/balanced fix (< 100ms)
        val fastRequest = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setDurationMillis(3000L)
            .build()

        fusedLocationClient.getCurrentLocation(fastRequest, null).addOnSuccessListener { loc ->
            if (loc != null) {
                saveLocationToPrefs(loc.latitude, loc.longitude)
                trySend(
                    LocationPoint(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        name = "Aktueller Standort",
                        bearing = if (loc.hasBearing()) loc.bearing else 0f,
                        speedMps = if (loc.hasSpeed()) loc.speed else 0f
                    )
                )
            }
        }

        // 4. Continuous high accuracy updates
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    saveLocationToPrefs(location.latitude, location.longitude)
                    trySend(
                        LocationPoint(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            name = "Aktueller Standort",
                            bearing = if (location.hasBearing()) location.bearing else 0f,
                            speedMps = if (location.hasSpeed()) location.speed else 0f
                        )
                    )
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }
}
