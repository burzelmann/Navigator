package com.example.nagivator.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.nagivator.data.local.dao.OfflineRegionDao
import com.example.nagivator.data.local.dao.PoiDao
import com.example.nagivator.data.local.dao.SavedRouteDao
import com.example.nagivator.data.local.entity.CategoryEntity
import com.example.nagivator.data.local.entity.OfflineRegionEntity
import com.example.nagivator.data.local.entity.PoiEntity
import com.example.nagivator.data.local.entity.SavedRouteEntity

@Database(
    entities = [
        PoiEntity::class,
        CategoryEntity::class,
        SavedRouteEntity::class,
        OfflineRegionEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class NagivatorDatabase : RoomDatabase() {
    abstract fun poiDao(): PoiDao
    abstract fun savedRouteDao(): SavedRouteDao
    abstract fun offlineRegionDao(): OfflineRegionDao

    companion object {
        @Volatile
        private var INSTANCE: NagivatorDatabase? = null

        fun getDatabase(context: Context): NagivatorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NagivatorDatabase::class.java,
                    "nagivator_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
