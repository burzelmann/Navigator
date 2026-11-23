package com.example.nagivator.domain

import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.domain.model.GermanManeuverTranslator
import com.example.nagivator.domain.model.LaneInfo
import com.example.nagivator.domain.model.RoadSignal
import com.example.nagivator.domain.model.RoadSignalType
import com.example.nagivator.domain.model.RouteStep
import com.example.nagivator.domain.model.TravelMode
import kotlin.math.roundToInt

/**
 * Human-factors-based Turn-by-Turn Voice Guidance State Machine.
 *
 * Implements:
 * - Speed-adaptive 3-stage Time-to-Maneuver cascade (Orient -> Prepare ~20s -> Execute ~5s)
 * - Natural German distance rounding (50m..2km)
 * - Compound double-maneuver chaining ("..., und dann sofort links abbiegen")
 * - Local OSM traffic-light landmark injection ("an der Ampel rechts abbiegen")
 * - Concise lane-choice prefix when unambiguous ("Links einordnen und ...")
 * - Anti-over-prompting (suppresses trivial `new name` / straight `continue` steps)
 */
class TurnGuidanceEngine {

    enum class AnnouncementStage {
        LONG_CONTINUE,
        HIGHWAY_EARLY,
        PREPARE,
        EXECUTE
    }

    private var trackedStepIndex: Int = -1
    private val announcedStages = mutableSetOf<AnnouncementStage>()

    fun reset() {
        trackedStepIndex = -1
        announcedStages.clear()
    }

    /**
     * Evaluates the current navigation state and returns a German TTS sentence to speak now,
     * or `null` if no announcement is due.
     */
    fun evaluate(
        stepIndex: Int,
        currentStep: RouteStep,
        nextStep: RouteStep?,
        distanceToManeuverMeters: Float,
        speedMps: Float,
        travelMode: TravelMode = TravelMode.CAR,
        roadSignals: List<RoadSignal> = emptyList()
    ): String? {
        if (stepIndex != trackedStepIndex) {
            trackedStepIndex = stepIndex
            announcedStages.clear()
        }

        if (isTrivialStep(currentStep)) {
            return null
        }

        val kmh = (speedMps * 3.6f).coerceAtLeast(0f)
        val isHighwaySpeed = travelMode == TravelMode.CAR && kmh >= 85f
        val isRuralSpeed = travelMode == TravelMode.CAR && kmh in 55f..84.99f

        val executeThreshold = when {
            travelMode == TravelMode.FOOT -> 25f
            isHighwaySpeed -> 260f
            isRuralSpeed -> 130f
            else -> 65f
        }
        val prepareThreshold = when {
            travelMode == TravelMode.FOOT -> 85f
            isHighwaySpeed -> 1050f
            isRuralSpeed -> 520f
            else -> 280f
        }
        val highwayEarlyThreshold = 2050f

        val atTrafficLight = travelMode == TravelMode.CAR &&
            hasNearbyTrafficLight(currentStep, roadSignals)

        // 1. EXECUTE stage (~4–6s before maneuver): short, immediate action
        if (distanceToManeuverMeters <= executeThreshold && AnnouncementStage.EXECUTE !in announcedStages) {
            announcedStages.add(AnnouncementStage.EXECUTE)
            announcedStages.add(AnnouncementStage.PREPARE)
            announcedStages.add(AnnouncementStage.HIGHWAY_EARLY)
            announcedStages.add(AnnouncementStage.LONG_CONTINUE)

            // Final arrival is announced once by the arrival detector (< 35m) to avoid double prompts.
            if (currentStep.maneuverType.equals("arrive", ignoreCase = true)) {
                return null
            }

            val phrase = GermanManeuverTranslator.buildSpokenPhrase(
                step = currentStep,
                includeTarget = false,
                atTrafficLight = atTrafficLight
            )
            val followUp = buildCompoundSuffix(currentStep, nextStep, isHighwaySpeed || isRuralSpeed)
            return "Jetzt $phrase$followUp."
        }

        // 2. PREPARE stage (~18–25s before maneuver): distance + optional lane + action + target + optional compound
        if (distanceToManeuverMeters <= prepareThreshold && AnnouncementStage.PREPARE !in announcedStages) {
            announcedStages.add(AnnouncementStage.PREPARE)
            announcedStages.add(AnnouncementStage.HIGHWAY_EARLY)
            announcedStages.add(AnnouncementStage.LONG_CONTINUE)

            val distText = formatSpokenDistance(distanceToManeuverMeters)
            if (currentStep.maneuverType.equals("arrive", ignoreCase = true)) {
                return "In $distText erreichen Sie Ihr Ziel."
            }

            val lanePrefix = if (travelMode == TravelMode.CAR) buildLaneHint(currentStep) else ""
            val phrase = GermanManeuverTranslator.buildSpokenPhrase(
                step = currentStep,
                includeTarget = true,
                atTrafficLight = atTrafficLight
            )
            val followUp = buildCompoundSuffix(currentStep, nextStep, isHighwaySpeed || isRuralSpeed)
            return if (lanePrefix.isNotBlank()) {
                "${lanePrefix}in $distText $phrase$followUp."
            } else {
                "In $distText $phrase$followUp."
            }
        }

        // 3. HIGHWAY EARLY warning (~2 km before Autobahn exit/fork/merge at >= 85 km/h)
        if (isHighwaySpeed &&
            isHighwayManeuver(currentStep) &&
            distanceToManeuverMeters <= highwayEarlyThreshold &&
            distanceToManeuverMeters > prepareThreshold + 200f &&
            AnnouncementStage.HIGHWAY_EARLY !in announcedStages
        ) {
            announcedStages.add(AnnouncementStage.HIGHWAY_EARLY)
            announcedStages.add(AnnouncementStage.LONG_CONTINUE)
            val distText = formatSpokenDistance(distanceToManeuverMeters)
            val phrase = GermanManeuverTranslator.buildSpokenPhrase(
                step = currentStep,
                includeTarget = true,
                atTrafficLight = false
            )
            return "In $distText $phrase."
        }

        // 4. ORIENTATION / LONG CONTINUE (> 1.5 km to next maneuver)
        if (travelMode == TravelMode.CAR &&
            distanceToManeuverMeters > 1500f &&
            AnnouncementStage.LONG_CONTINUE !in announcedStages
        ) {
            announcedStages.add(AnnouncementStage.LONG_CONTINUE)
            val distText = formatSpokenDistance(distanceToManeuverMeters, accusative = true)
            return "Dem Straßenverlauf $distText folgen."
        }

        return null
    }

    private fun isTrivialStep(step: RouteStep): Boolean {
        val type = step.maneuverType.lowercase().trim()
        val mod = step.maneuverModifier.lowercase().trim()
        if (type == "new name") return true
        if (type == "exit roundabout" || type == "exit rotary") return true
        if (type == "continue" && (mod.isBlank() || mod == "straight")) return true
        return false
    }

    private fun isHighwayManeuver(step: RouteStep): Boolean {
        val type = step.maneuverType.lowercase().trim()
        return type == "off ramp" || type == "fork" || type == "merge" || type == "on ramp"
    }

    private fun hasNearbyTrafficLight(step: RouteStep, roadSignals: List<RoadSignal>): Boolean {
        val loc = step.maneuverLocation ?: return false
        if (roadSignals.isEmpty()) return false
        return roadSignals.any { sig ->
            sig.type == RoadSignalType.TRAFFIC_SIGNALS &&
                CarNavigationHelper.haversineMeters(
                    loc.latitude, loc.longitude,
                    sig.latitude, sig.longitude
                ) <= TRAFFIC_LIGHT_LANDMARK_RADIUS_M
        }
    }

    private fun buildCompoundSuffix(
        currentStep: RouteStep,
        nextStep: RouteStep?,
        isFastRoad: Boolean
    ): String {
        if (nextStep == null || isTrivialStep(nextStep)) return ""
        val gapMeters = computeStepGapMeters(currentStep, nextStep) ?: return ""
        val maxGap = if (isFastRoad) 250.0 else 150.0
        if (gapMeters <= 0.0 || gapMeters > maxGap) return ""
        val shortAction = GermanManeuverTranslator.shortFollowUpAction(nextStep) ?: return ""
        return ", und dann $shortAction"
    }

    private fun computeStepGapMeters(currentStep: RouteStep, nextStep: RouteStep): Double? {
        val locA = currentStep.maneuverLocation
        val locB = nextStep.maneuverLocation
        if (locA != null && locB != null) {
            return CarNavigationHelper.haversineMeters(
                locA.latitude, locA.longitude,
                locB.latitude, locB.longitude
            )
        }
        return currentStep.distanceMeters.takeIf { it > 0.0 }
    }

    private fun buildLaneHint(step: RouteStep): String {
        val lanes = step.lanes
        if (lanes.size < 2) return ""
        val validIndices = lanes.mapIndexedNotNull { idx, lane -> if (lane.isValid) idx else null }
        if (validIndices.isEmpty() || validIndices.size == lanes.size) return ""
        val mod = step.maneuverModifier.lowercase().trim()
        val allLeftSide = validIndices.all { it == 0 }
        val allRightSide = validIndices.all { it == lanes.lastIndex }
        return when {
            // Plausibility guard: never say "Links einordnen" for a right turn or vice versa
            allLeftSide && !mod.contains("right") -> "Links einordnen und "
            allRightSide && !mod.contains("left") -> "Rechts einordnen und "
            else -> ""
        }
    }

    companion object {
        private const val TRAFFIC_LIGHT_LANDMARK_RADIUS_M = 30.0

        /**
         * Rounds raw meters to natural human-friendly German spoken distances
         * (dative: "In 300 Metern", "In 1 Kilometer"; accusative: "4 Kilometer folgen").
         */
        fun formatSpokenDistance(meters: Float, accusative: Boolean = false): String {
            val m = meters.coerceAtLeast(10f)
            return when {
                m < 65f -> if (accusative) "50 Meter" else "50 Metern"
                m < 90f -> if (accusative) "80 Meter" else "80 Metern"
                m < 125f -> if (accusative) "100 Meter" else "100 Metern"
                m < 175f -> if (accusative) "150 Meter" else "150 Metern"
                m < 250f -> if (accusative) "200 Meter" else "200 Metern"
                m < 350f -> if (accusative) "300 Meter" else "300 Metern"
                m < 450f -> if (accusative) "400 Meter" else "400 Metern"
                m < 650f -> if (accusative) "500 Meter" else "500 Metern"
                m < 900f -> if (accusative) "800 Meter" else "800 Metern"
                m < 1250f -> if (accusative) "einen Kilometer" else "einem Kilometer"
                m < 1750f -> if (accusative) "1,5 Kilometer" else "1,5 Kilometern"
                m < 2250f -> if (accusative) "2 Kilometer" else "2 Kilometern"
                else -> {
                    val km = (m / 1000f).roundToInt().coerceAtLeast(2)
                    if (accusative) "$km Kilometer" else "$km Kilometern"
                }
            }
        }
    }
}
