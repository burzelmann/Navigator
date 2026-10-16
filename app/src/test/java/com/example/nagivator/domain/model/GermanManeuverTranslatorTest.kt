package com.example.nagivator.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GermanManeuverTranslatorTest {

    @Test
    fun translate_depart_returnsCorrectGermanText() {
        val resultWithStreet = GermanManeuverTranslator.translate("depart", null, "Buchenweg")
        assertEquals("Starten auf Buchenweg", resultWithStreet)

        val resultWithoutStreet = GermanManeuverTranslator.translate("depart", null, "")
        assertEquals("Navigation gestartet", resultWithoutStreet)
    }

    @Test
    fun translate_arrive_returnsCorrectGermanText() {
        val result = GermanManeuverTranslator.translate("arrive", null, "Zielstraße")
        assertEquals("Sie haben Ihr Ziel erreicht", result)
    }

    @Test
    fun translate_turnModifiers_returnsCorrectGermanDirections() {
        val slightRight = GermanManeuverTranslator.translate("turn", "slight_right", "Hauptstraße")
        assertEquals("Leicht rechts abbiegen auf Hauptstraße", slightRight)

        val sharpLeft = GermanManeuverTranslator.translate("turn", "sharp_left", "Bahnhofstraße")
        assertEquals("Scharf links abbiegen auf Bahnhofstraße", sharpLeft)

        val uTurn = GermanManeuverTranslator.translate("turn", "uturn", "")
        assertEquals("Bitte wenden", uTurn)
    }

    @Test
    fun translate_roundabout_returnsCorrectGermanText() {
        val roundabout = GermanManeuverTranslator.translate("roundabout", "right", "Marktplatz")
        assertEquals("Im Kreisverkehr Ausfahrt nehmen auf Marktplatz", roundabout)
    }
}
