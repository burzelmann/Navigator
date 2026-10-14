package com.example.nagivator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nagivator.data.local.entity.CategoryEntity
import com.example.nagivator.data.local.entity.PoiEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiDao {
    @Query("SELECT * FROM pois ORDER BY title ASC")
    fun getAllPois(): Flow<List<PoiEntity>>

    @Query("SELECT * FROM pois WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoritePois(): Flow<List<PoiEntity>>

    @Query("SELECT * FROM pois WHERE id = :id")
    suspend fun getPoiById(id: Long): PoiEntity?

    @Query("SELECT * FROM pois WHERE categoryId = :categoryId")
    fun getPoisByCategory(categoryId: Long): Flow<List<PoiEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoi(poi: PoiEntity): Long

    @Update
    suspend fun updatePoi(poi: PoiEntity)

    @Delete
    suspend fun deletePoi(poi: PoiEntity)

    // Categories
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long
}
