package com.example.nagivator.data.repository

import com.example.nagivator.data.local.dao.OfflineRegionDao
import com.example.nagivator.data.local.dao.PoiDao
import com.example.nagivator.data.local.dao.SavedRouteDao
import com.example.nagivator.data.local.entity.CategoryEntity
import com.example.nagivator.data.local.entity.OfflineRegionEntity
import com.example.nagivator.data.local.entity.PoiEntity
import com.example.nagivator.data.local.entity.SavedRouteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class PoiRepository(
    private val poiDao: PoiDao,
    private val savedRouteDao: SavedRouteDao? = null,
    private val offlineRegionDao: OfflineRegionDao? = null
) {
    val allPois: Flow<List<PoiEntity>> = poiDao.getAllPois()
    val favoritePois: Flow<List<PoiEntity>> = poiDao.getFavoritePois()
    val allCategories: Flow<List<CategoryEntity>> = poiDao.getAllCategories()
    val allSavedRoutes: Flow<List<SavedRouteEntity>> = savedRouteDao?.getAllSavedRoutes() ?: flowOf(emptyList())
    val allOfflineRegions: Flow<List<OfflineRegionEntity>> = offlineRegionDao?.getAllOfflineRegions() ?: flowOf(emptyList())

    suspend fun getPoiById(id: Long): PoiEntity? = poiDao.getPoiById(id)

    suspend fun savePoi(poi: PoiEntity): Long {
        val sanitizedPoi = if (poi.title.isBlank()) {
            poi.copy(title = poi.displayTitle)
        } else poi
        return if (sanitizedPoi.id == 0L) {
            poiDao.insertPoi(sanitizedPoi)
        } else {
            poiDao.updatePoi(sanitizedPoi)
            sanitizedPoi.id
        }
    }

    suspend fun deletePoi(poi: PoiEntity) = poiDao.deletePoi(poi)

    suspend fun toggleFavorite(poi: PoiEntity) {
        poiDao.updatePoi(poi.copy(isFavorite = !poi.isFavorite))
    }

    // Categories
    suspend fun addCategory(category: CategoryEntity): Long = poiDao.insertCategory(category)

    // Saved Routes
    suspend fun saveRoute(route: SavedRouteEntity): Long {
        return savedRouteDao?.insertRoute(route) ?: -1L
    }

    suspend fun deleteRoute(route: SavedRouteEntity) {
        savedRouteDao?.deleteRoute(route)
    }

    // Offline Regions
    suspend fun saveOfflineRegion(region: OfflineRegionEntity): Long {
        return offlineRegionDao?.insertRegion(region) ?: -1L
    }

    suspend fun deleteOfflineRegion(region: OfflineRegionEntity) {
        offlineRegionDao?.deleteRegion(region)
    }
}
