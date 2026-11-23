package com.example.nagivator.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineVoiceSelectorTest {

    @Test
    fun filtersOutNetworkNotInstalledAndNonGermanVoices() {
        val voices = listOf(
            RawTtsVoice("en-us-x-sfg-local", "en", "US", 500, 200, false, false),
            RawTtsVoice("de-de-x-deb-network", "de", "DE", 500, 200, true, false),
            RawTtsVoice("de-de-x-missing-local", "de", "DE", 500, 200, false, true),
            RawTtsVoice("de-de-x-deb-local", "de", "DE", 400, 200, false, false)
        )

        val ranked = OfflineVoiceSelector.filterAndRankGermanOfflineVoices(voices)
        assertEquals(1, ranked.size)
        assertEquals("de-de-x-deb-local", ranked.first().name)
        assertEquals("HD • Offline", ranked.first().qualityLabel)
        assertEquals("Stimme 1 (HD)", ranked.first().shortLabel)
    }

    @Test
    fun ranksDeDeAndHighestQualityLocalVoiceFirst() {
        val voices = listOf(
            RawTtsVoice("de-at-x-at1-local", "de", "AT", 500, 100, false, false),
            RawTtsVoice("de-de-x-std", "de", "DE", 300, 200, false, false),
            RawTtsVoice("de-de-x-hd-slow-local", "de", "DE", 400, 400, false, false),
            RawTtsVoice("de-de-x-hd-fast-local", "de", "DE", 400, 150, false, false),
            RawTtsVoice("de-de-x-veryhigh-local", "de", "DE", 500, 250, false, false)
        )

        val ranked = OfflineVoiceSelector.filterAndRankGermanOfflineVoices(voices)
        assertEquals(
            listOf(
                "de-de-x-veryhigh-local",
                "de-de-x-hd-fast-local",
                "de-de-x-hd-slow-local",
                "de-de-x-std",
                "de-at-x-at1-local"
            ),
            ranked.map { it.name }
        )
        assertEquals("Deutsch – Stimme 1", ranked[0].displayName)
        assertEquals("HD+ • Offline", ranked[0].qualityLabel)
        assertEquals("Deutsch (AT) – Stimme 1", ranked[4].displayName)
        assertEquals("Stimme 1 (AT)", ranked[4].shortLabel)
    }

    @Test
    fun resolvesPreferredVoiceOrFallsBackToBestOffline() {
        val ranked = OfflineVoiceSelector.filterAndRankGermanOfflineVoices(
            listOf(
                RawTtsVoice("de-de-x-best-local", "de", "DE", 500, 200, false, false),
                RawTtsVoice("de-de-x-alt-local", "de", "DE", 400, 200, false, false)
            )
        )

        assertEquals("de-de-x-best-local", OfflineVoiceSelector.resolveVoiceName(ranked, null))
        assertEquals("de-de-x-alt-local", OfflineVoiceSelector.resolveVoiceName(ranked, "de-de-x-alt-local"))
        assertEquals("de-de-x-best-local", OfflineVoiceSelector.resolveVoiceName(ranked, "deleted-voice"))
        assertNull(OfflineVoiceSelector.resolveVoiceName(emptyList(), "de-de-x-alt-local"))
    }
}
