package com.example.nagivator.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nagivator.data.local.entity.LearnedRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LearnedRouteDao {
    @Query("SELECT * FROM learned_routes ORDER BY confirmationCount DESC, lastUsedMs DESC")
    fun observeAll(): Flow<List<LearnedRouteEntity>>

    @Query("SELECT * FROM learned_routes WHERE travelMode = :travelMode ORDER BY confirmationCount DESC, lastUsedMs DESC")
    suspend fun getByTravelMode(travelMode: String): List<LearnedRouteEntity>

    @Query("SELECT * FROM learned_routes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LearnedRouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LearnedRouteEntity): Long

    @Update
    suspend fun update(entity: LearnedRouteEntity)

    @Query("DELETE FROM learned_routes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM learned_routes")
    suspend fun deleteAll()
}
