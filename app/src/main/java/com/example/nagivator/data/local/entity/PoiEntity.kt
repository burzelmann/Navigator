package com.example.nagivator.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pois",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId")]
)
data class PoiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val latitude: Double,
    val longitude: Double,
    val categoryId: Long? = null,
    val rating: Float = 0f,
    val notes: String = "",
    val photoUri: String? = null,
    val isFavorite: Boolean = false,
    val iconName: String = "place",
    val createdAt: Long = System.currentTimeMillis()
) {
    val displayTitle: String
        get() = title.ifBlank {
            if (description.isNotBlank()) description
            else "Markierter Ort (%.3f, %.3f)".format(latitude, longitude)
        }
}
