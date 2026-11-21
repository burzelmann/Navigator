package com.example.nagivator.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.nagivator.data.local.entity.TripHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripHistoryDao {
    @Insert
    suspend fun insertTrip(trip: TripHistoryEntity): Long

    @Query("SELECT * FROM trip_history ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentTrips(limit: Int = 50): Flow<List<TripHistoryEntity>>

    @Query("SELECT * FROM trip_history ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentTripsOnce(limit: Int = 100): List<TripHistoryEntity>

    @Query("DELETE FROM trip_history WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    @Query("DELETE FROM trip_history")
    suspend fun clearAll()
}
