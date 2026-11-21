package com.example.nagivator.domain

import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.data.local.entity.TripHistoryEntity
import com.example.nagivator.domain.model.LocationPoint
import java.util.Calendar
import kotlin.math.abs

data class PredictedDestination(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val score: Double,
    val reason: String,
    val tripCount: Int
)

/**
 * On-device route & destination prediction based on local trip history.
 * Scores recurring destinations by start-location proximity, time-of-day window,
 * day-of-week pattern, frequency, and recency — completely offline and privacy-preserving.
 */
object RoutePredictionUseCase {

    fun predict(
        history: List<TripHistoryEntity>,
        currentLocation: LocationPoint?,
        nowMs: Long = System.currentTimeMillis(),
        maxResults: Int = 5
    ): List<PredictedDestination> {
        if (history.isEmpty()) return emptyList()

        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val nowDay = cal.get(Calendar.DAY_OF_WEEK)
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return predictAt(history, currentLocation, nowDay, nowMinute, nowMs, maxResults)
    }

    fun predictAt(
        history: List<TripHistoryEntity>,
        currentLocation: LocationPoint?,
        nowDayOfWeek: Int,
        nowMinuteOfDay: Int,
        nowMs: Long = System.currentTimeMillis(),
        maxResults: Int = 5
    ): List<PredictedDestination> {
        if (history.isEmpty()) return emptyList()

        // Cluster destinations within ~150 m so slight coordinate jitter groups together
        val clusters = mutableListOf<MutableList<TripHistoryEntity>>()
        for (trip in history) {
            val match = clusters.firstOrNull { group ->
                val rep = group.first()
                CarNavigationHelper.haversineMeters(
                    trip.destinationLatitude, trip.destinationLongitude,
                    rep.destinationLatitude, rep.destinationLongitude
                ) <= 150.0
            }
            if (match != null) match.add(trip) else clusters.add(mutableListOf(trip))
        }

        val nowIsWeekend = nowDayOfWeek == Calendar.SATURDAY || nowDayOfWeek == Calendar.SUNDAY
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000

        return clusters.mapNotNull { group ->
            val latest = group.maxByOrNull { it.timestampMs } ?: return@mapNotNull null

            // Skip destinations we are already standing right at (< 150 m)
            if (currentLocation != null) {
                val distToDest = CarNavigationHelper.haversineMeters(
                    currentLocation.latitude, currentLocation.longitude,
                    latest.destinationLatitude, latest.destinationLongitude
                )
                if (distToDest < 150.0) return@mapNotNull null
            }

            var score = 0.0
            var matchedTime = false
            var matchedStart = false

            for (trip in group) {
                score += 1.0 // Base frequency weight

                // Recency boost
                if (nowMs - trip.timestampMs <= sevenDaysMs) {
                    score += 0.8
                }

                // Start-location proximity boost (commute from same origin, e.g. Home -> Work)
                if (currentLocation != null) {
                    val startDist = CarNavigationHelper.haversineMeters(
                        currentLocation.latitude, currentLocation.longitude,
                        trip.startLatitude, trip.startLongitude
                    )
                    if (startDist <= 600.0) {
                        score += 4.0
                        matchedStart = true
                    }
                }

                // Time-of-day circular window (±90 min strong, ±180 min mild)
                val rawDiff = abs(trip.minuteOfDay - nowMinuteOfDay)
                val circDiff = minOf(rawDiff, 1440 - rawDiff)
                if (circDiff <= 90) {
                    score += 3.5
                    matchedTime = true
                } else if (circDiff <= 180) {
                    score += 1.5
                }

                // Day-of-week similarity
                val tripIsWeekend = trip.dayOfWeek == Calendar.SATURDAY || trip.dayOfWeek == Calendar.SUNDAY
                if (trip.dayOfWeek == nowDayOfWeek) {
                    score += 2.0
                } else if (tripIsWeekend == nowIsWeekend) {
                    score += 1.0
                }
            }

            val reason = when {
                matchedStart && matchedTime -> "Übliche Strecke um diese Uhrzeit"
                matchedTime -> "Häufig um diese Uhrzeit"
                group.size >= 3 -> "Häufiges Ziel (${group.size}×)"
                else -> "Zuletzt gefahren"
            }

            PredictedDestination(
                name = latest.destinationName,
                latitude = latest.destinationLatitude,
                longitude = latest.destinationLongitude,
                score = score,
                reason = reason,
                tripCount = group.size
            )
        }
            .sortedByDescending { it.score }
            .take(maxResults)
    }
}
