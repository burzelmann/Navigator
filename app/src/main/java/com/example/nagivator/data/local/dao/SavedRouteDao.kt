package com.example.nagivator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nagivator.data.local.entity.SavedRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedRouteDao {
    @Query("SELECT * FROM saved_routes ORDER BY createdAt DESC")
    fun getAllSavedRoutes(): Flow<List<SavedRouteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: SavedRouteEntity): Long

    @Delete
    suspend fun deleteRoute(route: SavedRouteEntity)
}
