package com.example.nagivator.domain.model

object GermanManeuverTranslator {

    fun translate(
        type: String,
        modifier: String?,
        rawName: String,
        roundaboutExit: Int? = null,
        destinations: String = "",
        exitRef: String = "",
        rotaryName: String = ""
    ): String {
        val streetName = rawName.trim()
        val streetStr = formatStreetTarget(streetName)
        val destStr = formatDestinations(destinations)
        val directionStr = if (destStr.isNotBlank()) " Richtung $destStr" else ""
        val exitStr = exitRef.trim().takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        val mod = modifier?.lowercase()?.trim() ?: ""

        return when (type.lowercase().trim()) {
            "depart" -> if (streetName.isNotBlank()) "Starten auf $streetName" else "Navigation gestartet"
            "arrive" -> "Sie haben Ihr Ziel erreicht"
            "turn" -> when {
                mod.contains("slight_right") || mod.contains("slight right") -> "Leicht rechts abbiegen$streetStr"
                mod.contains("sharp_right") || mod.contains("sharp right") -> "Scharf rechts abbiegen$streetStr"
                mod.contains("right") -> "Rechts abbiegen$streetStr"
                mod.contains("slight_left") || mod.contains("slight left") -> "Leicht links abbiegen$streetStr"
                mod.contains("sharp_left") || mod.contains("sharp left") -> "Scharf links abbiegen$streetStr"
                mod.contains("left") -> "Links abbiegen$streetStr"
                mod.contains("uturn") -> "Bitte wenden"
                else -> "Abbiegen$streetStr"
            }
            "end of road" -> when {
                mod.contains("right") -> "Am Straßenende rechts abbiegen$streetStr"
                mod.contains("left") -> "Am Straßenende links abbiegen$streetStr"
                else -> "Am Straßenende abbiegen$streetStr"
            }
            "roundabout", "rotary" -> {
                val ordinal = formatRoundaboutOrdinal(roundaboutExit)
                val rotaryPrefix = if (rotaryName.isNotBlank()) "Im Kreisverkehr $rotaryName" else "Im Kreisverkehr"
                if (ordinal != null) {
                    "$rotaryPrefix die $ordinal Ausfahrt nehmen$streetStr"
                } else {
                    "$rotaryPrefix Ausfahrt nehmen$streetStr"
                }
            }
            "exit roundabout", "exit rotary" -> "Kreisverkehr verlassen$streetStr"
            "merge" -> {
                val target = if (directionStr.isNotBlank()) "$streetStr$directionStr" else streetStr
                "Einordnen$target"
            }
            "fork" -> {
                val action = if (mod.contains("left")) "Links halten" else "Rechts halten"
                "$action$streetStr$directionStr"
            }
            "on ramp" -> {
                val target = if (directionStr.isNotBlank()) "$streetStr$directionStr" else streetStr
                "Auffahrt nehmen$target"
            }
            "off ramp" -> {
                val target = when {
                    directionStr.isNotBlank() -> directionStr
                    streetStr.isNotBlank() -> streetStr
                    else -> ""
                }
                "Ausfahrt$exitStr nehmen$target"
            }
            "continue" -> when {
                mod.contains("slight_right") || mod.contains("slight right") -> "Leicht rechts halten$streetStr"
                mod.contains("slight_left") || mod.contains("slight left") -> "Leicht links halten$streetStr"
                mod.contains("right") -> "Rechts abbiegen$streetStr"
                mod.contains("left") -> "Links abbiegen$streetStr"
                else -> "Dem Straßenverlauf folgen$streetStr"
            }
            "new name" -> if (streetName.isNotBlank()) "Weiter auf $streetName" else "Dem Straßenverlauf folgen"
            else -> if (streetName.isNotBlank()) "Dem Weg folgen auf $streetName" else "Dem Straßenverlauf folgen"
        }
    }

    /**
     * Formats a German street target with natural preposition ("auf Hauptstraße",
     * but "in die Straße Am Lindchen" / "in die Straße Im Bergwinkel" when the street
     * name itself already begins with a German preposition).
     */
    fun formatStreetTarget(streetName: String): String {
        val clean = streetName.trim()
        if (clean.isBlank()) return ""
        val startsWithPreposition = PREPOSITION_PREFIXES.any { prefix ->
            clean.startsWith(prefix, ignoreCase = true)
        }
        return if (startsWithPreposition) {
            " in die Straße $clean"
        } else {
            " auf $clean"
        }
    }

    private val PREPOSITION_PREFIXES = listOf(
        "Am ", "Im ", "An ", "Zur ", "Zum ", "In der ", "In den ", "Auf der ", "Auf dem ", "Auf den ", "Vor dem ", "Vor der "
    )

    /**
     * Extracts up to 2 concise destination names from OSRM `destinations` strings
     * (e.g. "Krefeld;Duisburg;Köln" -> "Krefeld, Duisburg") to avoid cognitive overload.
     */
    fun formatDestinations(raw: String, maxItems: Int = 2): String {
        if (raw.isBlank()) return ""
        val parts = raw
            .split(";", ",", "/", ":")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(maxItems)
        return parts.joinToString(", ")
    }

    fun formatRoundaboutOrdinal(exitNumber: Int?): String? = when (exitNumber) {
        null -> null
        1 -> "erste"
        2 -> "zweite"
        3 -> "dritte"
        4 -> "vierte"
        5 -> "fünfte"
        6 -> "sechste"
        in 7..20 -> "$exitNumber."
        else -> null
    }

    /**
     * Cleans text specifically for German TTS pronunciation:
     * expands common street abbreviations, removes noisy parenthetical road codes
     * like "(L 474)" when a real street name precedes it, and removes slashes.
     */
    fun normalizeForSpeech(text: String): String {
        var result = text.trim()
        if (result.isBlank()) return ""

        // Remove parenthetical road ref codes like "Dorfstraße (L 474)" -> "Dorfstraße"
        result = result.replace(Regex("""\s*\([A-ZÄÖÜ]{1,2}\s?\d+[a-z]?\)"""), "")
        // Replace slashes with natural spacing/conjunction
        result = result.replace(" / ", " oder ")
        // Expand common German road abbreviations for natural TTS
        result = result.replace(Regex("""\bStr\."""), "Straße")
        result = result.replace(Regex("""(?<=\p{L})str\."""), "straße")
        result = result.replace(Regex("""\bPl\."""), "Platz")
        result = result.replace(Regex("""(?<=\p{L})pl\."""), "platz")
        result = result.replace(Regex("""\bSt\.\s"""), "Sankt ")
        result = result.replace(Regex("""\bDr\.\s"""), "Doktor ")
        result = result.replace(Regex("""\s{2,}"""), " ")
        return result.trim()
    }

    /**
     * Builds a concise German spoken phrase for a step, optionally injecting a
     * traffic-light landmark ("an der Ampel ...") or omitting the street name for
     * ultra-short immediate execution prompts.
     */
    fun buildSpokenPhrase(
        step: RouteStep,
        includeTarget: Boolean = true,
        atTrafficLight: Boolean = false
    ): String {
        val type = step.maneuverType.lowercase().trim()
        val mod = step.maneuverModifier.lowercase().trim()
        val targetRoad = if (includeTarget) {
            val clean = when {
                step.roadName.isNotBlank() -> step.roadName
                step.roadRef.isNotBlank() -> step.roadRef
                else -> ""
            }
            formatStreetTarget(clean)
        } else ""

        val destStr = if (includeTarget) {
            val d = formatDestinations(step.destinations, maxItems = 1)
            if (d.isNotBlank()) " Richtung $d" else ""
        } else ""

        val landmark = if (atTrafficLight && (type == "turn" || type == "end of road")) {
            "an der Ampel "
        } else ""

        val raw = when (type) {
            "turn" -> when {
                mod.contains("slight_right") || mod.contains("slight right") -> "${landmark}leicht rechts abbiegen$targetRoad"
                mod.contains("sharp_right") || mod.contains("sharp right") -> "${landmark}scharf rechts abbiegen$targetRoad"
                mod.contains("right") -> "${landmark}rechts abbiegen$targetRoad"
                mod.contains("slight_left") || mod.contains("slight left") -> "${landmark}leicht links abbiegen$targetRoad"
                mod.contains("sharp_left") || mod.contains("sharp left") -> "${landmark}scharf links abbiegen$targetRoad"
                mod.contains("left") -> "${landmark}links abbiegen$targetRoad"
                mod.contains("uturn") -> "bitte wenden"
                else -> "${landmark}abbiegen$targetRoad"
            }
            "end of road" -> when {
                mod.contains("right") -> "${landmark}am Straßenende rechts abbiegen$targetRoad"
                mod.contains("left") -> "${landmark}am Straßenende links abbiegen$targetRoad"
                else -> "${landmark}am Straßenende abbiegen$targetRoad"
            }
            "roundabout", "rotary" -> {
                val ordinal = formatRoundaboutOrdinal(step.roundaboutExit)
                if (ordinal != null) {
                    "im Kreisverkehr die $ordinal Ausfahrt nehmen$targetRoad"
                } else {
                    "im Kreisverkehr die Ausfahrt nehmen$targetRoad"
                }
            }
            "off ramp" -> {
                val exitPart = step.exitRef.trim().takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                val target = if (destStr.isNotBlank()) destStr else targetRoad
                "die Ausfahrt$exitPart nehmen$target"
            }
            "on ramp" -> "die Auffahrt nehmen$targetRoad$destStr"
            "fork" -> {
                val action = if (mod.contains("left")) "links halten" else "rechts halten"
                "$action$targetRoad$destStr"
            }
            "merge" -> "einordnen$targetRoad$destStr"
            "arrive" -> "Sie haben Ihr Ziel erreicht"
            else -> step.instruction.replaceFirstChar { it.lowercase() }
        }
        return normalizeForSpeech(raw)
    }

    /**
     * Short follow-up phrase for compound instructions ("..., und dann sofort links abbiegen").
     */
    fun shortFollowUpAction(step: RouteStep): String? {
        val type = step.maneuverType.lowercase().trim()
        val mod = step.maneuverModifier.lowercase().trim()
        return when (type) {
            "turn", "end of road" -> when {
                mod.contains("slight_right") || mod.contains("slight right") -> "sofort leicht rechts abbiegen"
                mod.contains("sharp_right") || mod.contains("sharp right") -> "sofort scharf rechts abbiegen"
                mod.contains("right") -> "sofort rechts abbiegen"
                mod.contains("slight_left") || mod.contains("slight left") -> "sofort leicht links abbiegen"
                mod.contains("sharp_left") || mod.contains("sharp left") -> "sofort scharf links abbiegen"
                mod.contains("left") -> "sofort links abbiegen"
                mod.contains("uturn") -> "sofort wenden"
                else -> null
            }
            "roundabout", "rotary" -> {
                val ord = formatRoundaboutOrdinal(step.roundaboutExit)
                if (ord != null) "im Kreisverkehr die $ord Ausfahrt nehmen" else "in den Kreisverkehr fahren"
            }
            "fork" -> if (mod.contains("left")) "sofort links halten" else "sofort rechts halten"
            "off ramp" -> "sofort die Ausfahrt nehmen"
            "arrive" -> "haben Sie Ihr Ziel erreicht"
            else -> null
        }
    }
}
