package com.example.nagivator.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class Release5AlternativesAndEtaShareTest {

    @Test
    fun `formatShareMessage formats kilometers and minutes cleanly in German`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 35)
            set(Calendar.SECOND, 0)
        }

        val msg = EtaShareFormatter.formatShareMessage(
            destinationName = "Kleve Markt",
            remainingDistanceMeters = 12_400.0,
            remainingSeconds = 18 * 60.0,
            etaMillis = cal.timeInMillis,
            locale = Locale.GERMANY
        )

        assertTrue(msg.contains("Kleve Markt"))
        assertTrue(msg.contains("14:35 Uhr"))
        assertTrue(msg.contains("12,4 km"))
        assertTrue(msg.contains("18 Min."))
    }

    @Test
    fun `formatShareMessage handles sub-kilometer distance and multi-hour duration`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 10)
            set(Calendar.SECOND, 0)
        }

        val msg = EtaShareFormatter.formatShareMessage(
            destinationName = "",
            remainingDistanceMeters = 650.0,
            remainingSeconds = 95 * 60.0,
            etaMillis = cal.timeInMillis,
            locale = Locale.GERMANY
        )

        assertTrue(msg.contains("meinem Ziel"))
        assertTrue(msg.contains("16:10 Uhr"))
        assertTrue(msg.contains("650 m"))
        assertTrue(msg.contains("1 Std. 35 Min."))
    }
}
