package com.example.nagivator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val travelMode: String, // "FOOT" or "CAR"
    val distanceMeters: Double,
    val durationSeconds: Double,
    val waypointsJson: String, // Serialized List<LocationPoint>
    val createdAt: Long = System.currentTimeMillis()
)
