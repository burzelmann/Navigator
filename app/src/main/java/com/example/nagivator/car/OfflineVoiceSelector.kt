package com.example.nagivator.car

/**
 * Pure JVM-testable representation of an Android TTS [android.speech.tts.Voice].
 */
data class RawTtsVoice(
    val name: String,
    val language: String,
    val country: String,
    val quality: Int,
    val latency: Int,
    val isNetworkRequired: Boolean,
    val isNotInstalled: Boolean
)

/**
 * User-facing offline voice entry exposed to Smartphone & Android Auto settings UIs.
 */
data class TtsVoiceOption(
    val name: String,
    val displayName: String,
    val shortLabel: String,
    val qualityLabel: String,
    val qualityScore: Int
)

/**
 * Filters and ranks available Android TTS voices according to Android Best Practice:
 * strictly offline German voices that are actually installed on-device, ranked by
 * de-DE region preference, voice quality (VERY_HIGH > HIGH > NORMAL), local neural
 * preference, and low latency.
 */
object OfflineVoiceSelector {

    const val QUALITY_HIGH = 400
    const val QUALITY_VERY_HIGH = 500

    fun filterAndRankGermanOfflineVoices(voices: Collection<RawTtsVoice>): List<TtsVoiceOption> {
        val eligible = voices
            .filter { voice ->
                voice.language.equals("de", ignoreCase = true) &&
                    !voice.isNetworkRequired &&
                    !voice.isNotInstalled &&
                    voice.name.isNotBlank()
            }
            .distinctBy { it.name }
            .sortedWith(
                compareBy<RawTtsVoice>(
                    { if (it.country.equals("DE", ignoreCase = true)) 0 else 1 },
                    { -it.quality },
                    { if (it.name.contains("local", ignoreCase = true)) 0 else 1 },
                    { it.latency },
                    { it.name.lowercase() }
                )
            )

        val countersByRegion = mutableMapOf<String, Int>()
        return eligible.map { voice ->
            val region = voice.country.uppercase().ifBlank { "DE" }
            val index = (countersByRegion[region] ?: 0) + 1
            countersByRegion[region] = index

            val isHd = voice.quality >= QUALITY_HIGH
            val qualityLabel = when {
                voice.quality >= QUALITY_VERY_HIGH -> "HD+ • Offline"
                isHd -> "HD • Offline"
                else -> "Standard • Offline"
            }

            val regionSuffix = if (region == "DE") "" else " ($region)"
            val displayName = "Deutsch$regionSuffix – Stimme $index"
            val shortLabel = buildString {
                append("Stimme ")
                append(index)
                if (region != "DE") {
                    append(" (")
                    append(region)
                    append(")")
                } else if (isHd) {
                    append(" (HD)")
                }
            }

            TtsVoiceOption(
                name = voice.name,
                displayName = displayName,
                shortLabel = shortLabel,
                qualityLabel = qualityLabel,
                qualityScore = voice.quality
            )
        }
    }

    /**
     * Picks the preferred voice name if installed, otherwise falls back to the highest-ranked
     * offline German voice.
     */
    fun resolveVoiceName(
        rankedVoices: List<TtsVoiceOption>,
        preferredVoiceName: String?
    ): String? {
        if (rankedVoices.isEmpty()) return null
        if (!preferredVoiceName.isNullOrBlank()) {
            val match = rankedVoices.firstOrNull { it.name == preferredVoiceName }
            if (match != null) return match.name
        }
        return rankedVoices.first().name
    }
}
