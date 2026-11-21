package com.example.nagivator.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Formats live ETA messages for sharing via Android's Share Sheet (Intent.ACTION_SEND).
 */
object EtaShareFormatter {

    fun formatShareMessage(
        destinationName: String,
        remainingDistanceMeters: Double,
        remainingSeconds: Double,
        etaMillis: Long = System.currentTimeMillis() + (remainingSeconds * 1000).toLong(),
        locale: Locale = Locale.GERMANY
    ): String {
        val cleanDest = destinationName.ifBlank { "meinem Ziel" }
        val etaStr = SimpleDateFormat("HH:mm", locale).format(Date(etaMillis))

        val distStr = if (remainingDistanceMeters >= 1000.0) {
            String.format(locale, "%.1f km", remainingDistanceMeters / 1000.0)
        } else {
            "${remainingDistanceMeters.roundToInt().coerceAtLeast(0)} m"
        }

        val totalMinutes = (remainingSeconds / 60.0).roundToInt().coerceAtLeast(1)
        val durationStr = if (totalMinutes >= 60) {
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            if (m == 0) "$h Std." else "$h Std. $m Min."
        } else {
            "$totalMinutes Min."
        }

        return "Ich bin unterwegs nach $cleanDest und komme voraussichtlich um $etaStr Uhr an (noch $distStr, ca. $durationStr)."
    }
}
