package com.example.nagivator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Locally learned preferred car route corridor for a specific origin -> destination pair.
 * Strictly on-device, opt-in only, and only active for routing once [confirmationCount] >= 3.
 */
@Entity(tableName = "learned_routes")
data class LearnedRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startName: String,
    val startLatitude: Double,
    val startLongitude: Double,
    val destinationName: String,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val travelMode: String,
    val anchorLatitude: Double,
    val anchorLongitude: Double,
    val anchor2Latitude: Double? = null,
    val anchor2Longitude: Double? = null,
    val confirmationCount: Int = 1,
    val lastUsedMs: Long
)
