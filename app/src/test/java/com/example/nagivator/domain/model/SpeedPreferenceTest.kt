package com.example.nagivator.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedPreferenceTest {

    @Test
    fun speedPreferences_haveCorrectScientificMpsValues() {
        // Slow walking: ~3.3 km/h = 0.92 m/s
        assertEquals(0.92, SpeedPreference.SLOW.footSpeedMps, 0.01)

        // Normal walking: ~4.5 km/h = 1.25 m/s
        assertEquals(1.25, SpeedPreference.NORMAL.footSpeedMps, 0.01)

        // Fast walking: ~5.8 km/h = 1.62 m/s
        assertEquals(1.62, SpeedPreference.FAST.footSpeedMps, 0.01)
    }

    @Test
    fun footRouteDuration_calculatesCorrectlyForSpeedPreferences() {
        val distanceMeters = 1500.0 // 1.5 km

        val durationSlow = distanceMeters / SpeedPreference.SLOW.footSpeedMps
        val durationNormal = distanceMeters / SpeedPreference.NORMAL.footSpeedMps
        val durationFast = distanceMeters / SpeedPreference.FAST.footSpeedMps

        // Slow (~27.1 minutes)
        assertEquals(1630.4, durationSlow, 1.0)

        // Normal (20.0 minutes)
        assertEquals(1200.0, durationNormal, 1.0)

        // Fast (~15.4 minutes)
        assertEquals(925.9, durationFast, 1.0)
    }
}
