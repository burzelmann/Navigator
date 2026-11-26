package com.example.nagivator.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local offline search index entry (town, street, or key POI) downloaded alongside
 * a map region so destination search works completely offline in both Phone & Android Auto.
 */
@Entity(
    tableName = "offline_search_places",
    indices = [
        Index(value = ["normalizedName"]),
        Index(value = ["regionName"])
    ]
)
data class OfflineSearchPlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val regionName: String,
    val name: String,
    val normalizedName: String,
    val category: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val priority: Int // 3 = Ort, 2 = POI, 1 = Straße
)
