package com.example.nagivator.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.nagivator.data.local.dao.LearnedRouteDao
import com.example.nagivator.data.local.dao.OfflineRegionDao
import com.example.nagivator.data.local.dao.PoiDao
import com.example.nagivator.data.local.dao.SavedRouteDao
import com.example.nagivator.data.local.dao.TripHistoryDao
import com.example.nagivator.data.local.entity.CategoryEntity
import com.example.nagivator.data.local.entity.LearnedRouteEntity
import com.example.nagivator.data.local.entity.OfflineRegionEntity
import com.example.nagivator.data.local.entity.PoiEntity
import com.example.nagivator.data.local.entity.SavedRouteEntity
import com.example.nagivator.data.local.entity.TripHistoryEntity

@Database(
    entities = [
        PoiEntity::class,
        CategoryEntity::class,
        SavedRouteEntity::class,
        OfflineRegionEntity::class,
        TripHistoryEntity::class,
        LearnedRouteEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class NagivatorDatabase : RoomDatabase() {
    abstract fun poiDao(): PoiDao
    abstract fun savedRouteDao(): SavedRouteDao
    abstract fun offlineRegionDao(): OfflineRegionDao
    abstract fun tripHistoryDao(): TripHistoryDao
    abstract fun learnedRouteDao(): LearnedRouteDao

    companion object {
        @Volatile
        private var INSTANCE: NagivatorDatabase? = null

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trip_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `startName` TEXT NOT NULL,
                        `startLatitude` REAL NOT NULL,
                        `startLongitude` REAL NOT NULL,
                        `destinationName` TEXT NOT NULL,
                        `destinationLatitude` REAL NOT NULL,
                        `destinationLongitude` REAL NOT NULL,
                        `travelMode` TEXT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `minuteOfDay` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `learned_routes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `startName` TEXT NOT NULL,
                        `startLatitude` REAL NOT NULL,
                        `startLongitude` REAL NOT NULL,
                        `destinationName` TEXT NOT NULL,
                        `destinationLatitude` REAL NOT NULL,
                        `destinationLongitude` REAL NOT NULL,
                        `travelMode` TEXT NOT NULL,
                        `anchorLatitude` REAL NOT NULL,
                        `anchorLongitude` REAL NOT NULL,
                        `anchor2Latitude` REAL,
                        `anchor2Longitude` REAL,
                        `confirmationCount` INTEGER NOT NULL,
                        `lastUsedMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): NagivatorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NagivatorDatabase::class.java,
                    "nagivator_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
