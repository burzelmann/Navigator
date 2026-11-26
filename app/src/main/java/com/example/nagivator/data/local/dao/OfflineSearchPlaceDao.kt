package com.example.nagivator.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nagivator.data.local.entity.OfflineSearchPlaceEntity

@Dao
interface OfflineSearchPlaceDao {
    @Query(
        """
        SELECT * FROM offline_search_places
        WHERE normalizedName LIKE :containsPattern
        ORDER BY
            CASE WHEN normalizedName LIKE :prefixPattern THEN 0 ELSE 1 END ASC,
            priority DESC,
            LENGTH(name) ASC,
            name ASC
        LIMIT :limit
        """
    )
    suspend fun search(
        containsPattern: String,
        prefixPattern: String,
        limit: Int = 20
    ): List<OfflineSearchPlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(places: List<OfflineSearchPlaceEntity>)

    @Query("DELETE FROM offline_search_places WHERE regionName = :regionName")
    suspend fun deleteByRegion(regionName: String)

    @Query("SELECT COUNT(*) FROM offline_search_places WHERE regionName = :regionName")
    suspend fun countByRegion(regionName: String): Int

    @Query("SELECT COUNT(*) FROM offline_search_places")
    suspend fun totalCount(): Int
}
