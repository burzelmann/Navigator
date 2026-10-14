package com.example.nagivator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nagivator.data.local.entity.OfflineRegionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineRegionDao {
    @Query("SELECT * FROM offline_regions ORDER BY downloadedAt DESC")
    fun getAllOfflineRegions(): Flow<List<OfflineRegionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegion(region: OfflineRegionEntity): Long

    @Delete
    suspend fun deleteRegion(region: OfflineRegionEntity)
}
