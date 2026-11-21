package com.example.nagivator.data.repository

import com.example.nagivator.data.local.dao.TripHistoryDao
import com.example.nagivator.data.local.entity.TripHistoryEntity
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class TripHistoryRepository(private val dao: TripHistoryDao) {

    fun recentTrips(limit: Int = 50): Flow<List<TripHistoryEntity>> = dao.getRecentTrips(limit)

    suspend fun recentTripsOnce(limit: Int = 100): List<TripHistoryEntity> = dao.getRecentTripsOnce(limit)

    suspend fun recordTrip(
        start: LocationPoint,
        destination: LocationPoint,
        mode: TravelMode,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val entity = TripHistoryEntity(
            startName = start.name.ifBlank { "Startpunkt" },
            startLatitude = start.latitude,
            startLongitude = start.longitude,
            destinationName = destination.name.ifBlank { destination.address.ifBlank { "Ziel" } },
            destinationLatitude = destination.latitude,
            destinationLongitude = destination.longitude,
            travelMode = mode.name,
            timestampMs = nowMs,
            dayOfWeek = dayOfWeek,
            minuteOfDay = minuteOfDay
        )
        dao.insertTrip(entity)
    }

    suspend fun deleteTrip(id: Long) = dao.deleteTrip(id)

    suspend fun clearAll() = dao.clearAll()
}
