package com.example.nagivator.domain

import com.example.nagivator.data.local.entity.TripHistoryEntity
import com.example.nagivator.domain.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class Release3HistoryAndPredictionTest {

    private val home = LocationPoint(latitude = 51.7890, longitude = 6.1380, name = "Zuhause")
    private val office = LocationPoint(latitude = 51.8250, longitude = 6.2100, name = "Büro Kleve")
    private val gym = LocationPoint(latitude = 51.7700, longitude = 6.1500, name = "Fitnessstudio")

    private fun trip(
        id: Long,
        start: LocationPoint,
        dest: LocationPoint,
        dayOfWeek: Int,
        hour: Int,
        minute: Int = 0,
        timestampMs: Long = 1_760_000_000_000L
    ) = TripHistoryEntity(
        id = id,
        startName = start.name,
        startLatitude = start.latitude,
        startLongitude = start.longitude,
        destinationName = dest.name,
        destinationLatitude = dest.latitude,
        destinationLongitude = dest.longitude,
        travelMode = "CAR",
        timestampMs = timestampMs,
        dayOfWeek = dayOfWeek,
        minuteOfDay = hour * 60 + minute
    )

    @Test
    fun `predictAt ranks morning office commute first on weekday morning from home`() {
        val nowMs = 1_760_100_000_000L
        val history = listOf(
            trip(1, home, office, Calendar.MONDAY, 8, 0, nowMs - 86_400_000L),
            trip(2, home, office, Calendar.TUESDAY, 8, 15, nowMs - 172_800_000L),
            trip(3, home, gym, Calendar.SATURDAY, 18, 0, nowMs - 86_400_000L)
        )

        val predictions = RoutePredictionUseCase.predictAt(
            history = history,
            currentLocation = home,
            nowDayOfWeek = Calendar.WEDNESDAY,
            nowMinuteOfDay = 8 * 60 + 10,
            nowMs = nowMs
        )

        assertTrue(predictions.isNotEmpty())
        assertEquals("Büro Kleve", predictions.first().name)
        assertEquals(2, predictions.first().tripCount)
    }

    @Test
    fun `predictAt skips destination when user is already standing right there`() {
        val nowMs = 1_760_100_000_000L
        val history = listOf(
            trip(1, home, office, Calendar.MONDAY, 8, 0, nowMs - 3_600_000L),
            trip(2, office, home, Calendar.MONDAY, 17, 30, nowMs - 3_600_000L)
        )

        // User is currently at the office at 17:20 -> should predict "Zuhause", NOT "Büro Kleve"
        val predictions = RoutePredictionUseCase.predictAt(
            history = history,
            currentLocation = office,
            nowDayOfWeek = Calendar.TUESDAY,
            nowMinuteOfDay = 17 * 60 + 20,
            nowMs = nowMs
        )

        assertEquals(1, predictions.size)
        assertEquals("Zuhause", predictions.first().name)
    }

    @Test
    fun `predictAt clusters destinations within 150m`() {
        val nowMs = 1_760_100_000_000L
        val slightlyShiftedOffice = office.copy(
            latitude = office.latitude + 0.0005, // ~55 m north
            longitude = office.longitude + 0.0004
        )
        val history = listOf(
            trip(1, home, office, Calendar.MONDAY, 8, 0, nowMs - 86_400_000L),
            trip(2, home, slightlyShiftedOffice, Calendar.TUESDAY, 8, 5, nowMs - 10_000L)
        )

        val predictions = RoutePredictionUseCase.predictAt(
            history = history,
            currentLocation = home,
            nowDayOfWeek = Calendar.WEDNESDAY,
            nowMinuteOfDay = 8 * 60,
            nowMs = nowMs
        )

        assertEquals(1, predictions.size)
        assertEquals(2, predictions.first().tripCount)
    }
}
