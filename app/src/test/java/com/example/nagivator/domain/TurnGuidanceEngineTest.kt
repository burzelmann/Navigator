package com.example.nagivator.domain

import com.example.nagivator.car.CarManeuverConverter
import com.example.nagivator.domain.model.GermanManeuverTranslator
import com.example.nagivator.domain.model.LaneInfo
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RoadSignal
import com.example.nagivator.domain.model.RoadSignalType
import com.example.nagivator.domain.model.RouteStep
import com.example.nagivator.domain.model.TravelMode
import androidx.car.app.navigation.model.LaneDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnGuidanceEngineTest {

    @Test
    fun highwaySpeedCascadeTriggersEarlyPrepareAndExecuteWithoutSkipping() {
        val engine = TurnGuidanceEngine()
        val exitStep = RouteStep(
            instruction = "Ausfahrt 8 nehmen Richtung Krefeld",
            distanceMeters = 4000.0,
            durationSeconds = 120.0,
            maneuverType = "off ramp",
            maneuverModifier = "slight right",
            destinations = "Krefeld;Duisburg;Köln",
            exitRef = "8"
        )
        val speed120Kmh = 33.3f

        // 1. Long orientation > 1.5 km (beyond 2 km early exit threshold)
        val orient = engine.evaluate(0, exitStep, null, 3500f, speed120Kmh, TravelMode.CAR)
        assertNotNull(orient)
        assertTrue(orient!!.contains("4 Kilometer folgen"))

        // 2. Highway early warning (~2 km)
        val early = engine.evaluate(0, exitStep, null, 1980f, speed120Kmh, TravelMode.CAR)
        assertNotNull(early)
        assertTrue(early!!.contains("2 Kilometern"))
        assertTrue(early.contains("Richtung Krefeld"))

        // 3. Prepare (~1 km)
        val prepare = engine.evaluate(0, exitStep, null, 980f, speed120Kmh, TravelMode.CAR)
        assertNotNull(prepare)
        assertTrue(prepare!!.contains("einem Kilometer"))

        // 4. Execute (~250 m at 120 km/h — fires even with 1 Hz 35m jump!)
        val exec = engine.evaluate(0, exitStep, null, 230f, speed120Kmh, TravelMode.CAR)
        assertNotNull(exec)
        assertTrue(exec!!.startsWith("Jetzt "))

        // 5. No duplicate execution on subsequent tick
        assertNull(engine.evaluate(0, exitStep, null, 190f, speed120Kmh, TravelMode.CAR))
    }

    @Test
    fun cityTurnInjectsTrafficLightLandmarkLaneHintAndCompoundFollowUp() {
        val engine = TurnGuidanceEngine()
        val turnLoc = LocationPoint(latitude = 51.7300, longitude = 6.3000)
        val nextLoc = LocationPoint(latitude = 51.7308, longitude = 6.3000) // ~89 m away

        val currentStep = RouteStep(
            instruction = "Links abbiegen auf Hauptstr. (L 474)",
            distanceMeters = 90.0,
            durationSeconds = 12.0,
            maneuverType = "turn",
            maneuverModifier = "left",
            maneuverLocation = turnLoc,
            roadName = "Hauptstr.",
            roadRef = "L 474",
            lanes = listOf(
                LaneInfo(listOf("left"), isValid = true),
                LaneInfo(listOf("straight"), isValid = false),
                LaneInfo(listOf("right"), isValid = false)
            )
        )
        val nextStep = RouteStep(
            instruction = "Rechts abbiegen auf Marktplatz",
            distanceMeters = 200.0,
            durationSeconds = 20.0,
            maneuverType = "turn",
            maneuverModifier = "right",
            maneuverLocation = nextLoc,
            roadName = "Marktplatz"
        )
        val signals = listOf(
            RoadSignal(
                id = 1L,
                latitude = 51.73005,
                longitude = 6.30005,
                type = RoadSignalType.TRAFFIC_SIGNALS
            )
        )

        val prepare = engine.evaluate(
            stepIndex = 1,
            currentStep = currentStep,
            nextStep = nextStep,
            distanceToManeuverMeters = 220f,
            speedMps = 12f,
            travelMode = TravelMode.CAR,
            roadSignals = signals
        )
        assertNotNull(prepare)
        assertTrue("Expected lane hint: $prepare", prepare!!.startsWith("Links einordnen und in 200 Metern"))
        assertTrue("Expected traffic light landmark: $prepare", prepare.contains("an der Ampel links abbiegen"))
        assertTrue("Expected expanded street abbreviation: $prepare", prepare.contains("Hauptstraße"))
        assertTrue("Expected compound follow-up: $prepare", prepare.contains("und dann sofort rechts abbiegen"))
    }

    @Test
    fun suppressesTrivialNewNameAndStraightContinueSteps() {
        val engine = TurnGuidanceEngine()
        val newName = RouteStep("Weiter auf Bahnhofstraße", 300.0, 20.0, maneuverType = "new name")
        val straightContinue = RouteStep("Dem Straßenverlauf folgen", 300.0, 20.0, maneuverType = "continue", maneuverModifier = "straight")
        val exitRoundabout = RouteStep("Kreisverkehr verlassen", 50.0, 5.0, maneuverType = "exit roundabout", maneuverModifier = "right")

        assertNull(engine.evaluate(0, newName, null, 200f, 13f, TravelMode.CAR))
        assertNull(engine.evaluate(1, straightContinue, null, 50f, 13f, TravelMode.CAR))
        assertNull(engine.evaluate(2, exitRoundabout, null, 30f, 10f, TravelMode.CAR))
    }

    @Test
    fun laneHintNeverContradictsManeuverDirectionAndPrepositionHandlesAmIm() {
        val engine = TurnGuidanceEngine()
        // Rightmost lane valid, but maneuver is LEFT -> plausibility guard must suppress "Rechts einordnen"
        val mismatchedLeftTurn = RouteStep(
            instruction = "Links abbiegen in die Straße Im Bergwinkel",
            distanceMeters = 300.0,
            durationSeconds = 25.0,
            maneuverType = "turn",
            maneuverModifier = "left",
            roadName = "Im Bergwinkel",
            lanes = listOf(
                LaneInfo(listOf("straight"), isValid = false),
                LaneInfo(listOf("right"), isValid = true)
            )
        )
        val prompt = engine.evaluate(0, mismatchedLeftTurn, null, 220f, 12f, TravelMode.CAR)
        assertNotNull(prompt)
        assertTrue("Must not say Rechts einordnen for left turn: $prompt", !prompt!!.contains("Rechts einordnen"))
        assertTrue("Expected natural preposition: $prompt", prompt.contains("in die Straße Im Bergwinkel"))
    }

    @Test
    fun translatorFormatsRoundaboutExitDestinationsAndTtsHygiene() {
        val roundabout = GermanManeuverTranslator.translate(
            type = "roundabout",
            modifier = "right",
            rawName = "Bahnhofstr.",
            roundaboutExit = 2
        )
        assertEquals("Im Kreisverkehr die zweite Ausfahrt nehmen auf Bahnhofstr.", roundabout)
        assertEquals(
            "Im Kreisverkehr die zweite Ausfahrt nehmen auf Bahnhofstraße",
            GermanManeuverTranslator.normalizeForSpeech(roundabout)
        )

        val offRamp = GermanManeuverTranslator.translate(
            type = "off ramp",
            modifier = "slight right",
            rawName = "",
            destinations = "Krefeld;Duisburg;Köln",
            exitRef = "8"
        )
        assertEquals("Ausfahrt 8 nehmen Richtung Krefeld, Duisburg", offRamp)
        assertEquals("Dorfstraße", GermanManeuverTranslator.normalizeForSpeech("Dorfstraße (L 474)"))
    }

    @Test
    fun carManeuverConverterMapsOsrmLanesCorrectly() {
        val carLanes = CarManeuverConverter.toCarLanes(
            listOf(
                LaneInfo(listOf("left"), isValid = false),
                LaneInfo(listOf("straight", "right"), isValid = true)
            )
        )
        assertEquals(2, carLanes.size)
        assertEquals(LaneDirection.SHAPE_NORMAL_LEFT, carLanes[0].directions.first().shape)
        assertEquals(false, carLanes[0].directions.first().isRecommended)
        assertEquals(2, carLanes[1].directions.size)
        assertEquals(true, carLanes[1].directions.first().isRecommended)
    }

    @Test
    fun arrivalUsesNaturalGermanPreparationAndNoDuplicateExecutePrompt() {
        val engine = TurnGuidanceEngine()
        val arrive = RouteStep("Sie haben Ihr Ziel erreicht", 0.0, 0.0, maneuverType = "arrive")

        val prep = engine.evaluate(4, arrive, null, 210f, 12f, TravelMode.CAR)
        assertEquals("In 200 Metern erreichen Sie Ihr Ziel.", prep)

        // Arrival execute prompt is delegated to arrival detector (<35m) to avoid double announcements
        assertNull(engine.evaluate(4, arrive, null, 50f, 12f, TravelMode.CAR))
    }

    @Test
    fun distanceRoundingProducesNaturalGermanSteps() {
        assertEquals("50 Metern", TurnGuidanceEngine.formatSpokenDistance(48f))
        assertEquals("200 Metern", TurnGuidanceEngine.formatSpokenDistance(237f))
        assertEquals("300 Metern", TurnGuidanceEngine.formatSpokenDistance(287f))
        assertEquals("einem Kilometer", TurnGuidanceEngine.formatSpokenDistance(1040f))
        assertEquals("1,5 Kilometern", TurnGuidanceEngine.formatSpokenDistance(1480f))
        assertEquals("4 Kilometer", TurnGuidanceEngine.formatSpokenDistance(3900f, accusative = true))
    }
}
