package com.example.nagivator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_history")
data class TripHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startName: String,
    val startLatitude: Double,
    val startLongitude: Double,
    val destinationName: String,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val travelMode: String,
    val timestampMs: Long,
    val dayOfWeek: Int,      // Calendar.MONDAY..SUNDAY (1..7)
    val minuteOfDay: Int     // 0..1439
)
