package com.example.nagivator.domain.model

object GermanManeuverTranslator {
    fun translate(type: String, modifier: String?, rawName: String): String {
        val streetName = rawName.trim()
        val streetStr = if (streetName.isNotBlank()) " auf $streetName" else ""
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
            "roundabout", "rotary" -> "Im Kreisverkehr Ausfahrt nehmen$streetStr"
            "merge" -> "Auffahren / Einordnen$streetStr"
            "fork" -> if (mod.contains("left")) "Links halten$streetStr" else "Rechts halten$streetStr"
            "on ramp" -> "Auffahrt nehmen$streetStr"
            "off ramp" -> "Ausfahrt nehmen$streetStr"
            "continue" -> "Dem Straßenverlauf folgen$streetStr"
            "new name" -> "Weiter auf $streetName"
            else -> if (streetName.isNotBlank()) "Dem Weg folgen auf $streetName" else "Dem Straßenverlauf folgen"
        }
    }
}
