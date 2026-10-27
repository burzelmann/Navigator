package com.example.nagivator.car

import androidx.car.app.navigation.model.Maneuver

object CarManeuverConverter {
    fun toCarManeuver(type: String, modifier: String, exitNumber: Int? = null): Maneuver {
        val typeNorm = type.lowercase().trim()
        val modNorm = modifier.lowercase().trim()

        val builder = when (typeNorm) {
            "depart" -> Maneuver.Builder(Maneuver.TYPE_DEPART)
            "arrive" -> Maneuver.Builder(Maneuver.TYPE_DESTINATION)
            "merge" -> when (modNorm) {
                "left" -> Maneuver.Builder(Maneuver.TYPE_MERGE_LEFT)
                "right" -> Maneuver.Builder(Maneuver.TYPE_MERGE_RIGHT)
                else -> Maneuver.Builder(Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED)
            }
            "fork" -> when (modNorm) {
                "left" -> Maneuver.Builder(Maneuver.TYPE_FORK_LEFT)
                "right" -> Maneuver.Builder(Maneuver.TYPE_FORK_RIGHT)
                else -> Maneuver.Builder(Maneuver.TYPE_STRAIGHT)
            }
            "roundabout", "rotary" -> {
                if (exitNumber != null && exitNumber > 0) {
                    Maneuver.Builder(Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW)
                        .setRoundaboutExitNumber(exitNumber)
                } else {
                    Maneuver.Builder(Maneuver.TYPE_ROUNDABOUT_ENTER_CCW)
                }
            }
            "roundabout_exit" -> Maneuver.Builder(Maneuver.TYPE_ROUNDABOUT_EXIT_CCW)
            "turn", "end of road" -> when (modNorm) {
                "sharp_right" -> Maneuver.Builder(Maneuver.TYPE_TURN_SHARP_RIGHT)
                "right" -> Maneuver.Builder(Maneuver.TYPE_TURN_NORMAL_RIGHT)
                "slight_right" -> Maneuver.Builder(Maneuver.TYPE_TURN_SLIGHT_RIGHT)
                "sharp_left" -> Maneuver.Builder(Maneuver.TYPE_TURN_SHARP_LEFT)
                "left" -> Maneuver.Builder(Maneuver.TYPE_TURN_NORMAL_LEFT)
                "slight_left" -> Maneuver.Builder(Maneuver.TYPE_TURN_SLIGHT_LEFT)
                "uturn" -> Maneuver.Builder(Maneuver.TYPE_U_TURN_LEFT)
                else -> Maneuver.Builder(Maneuver.TYPE_STRAIGHT)
            }
            "off ramp", "on ramp" -> when (modNorm) {
                "slight_right", "right" -> Maneuver.Builder(Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT)
                "slight_left", "left" -> Maneuver.Builder(Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT)
                else -> Maneuver.Builder(Maneuver.TYPE_STRAIGHT)
            }
            "straight", "continue", "new name" -> Maneuver.Builder(Maneuver.TYPE_STRAIGHT)
            else -> Maneuver.Builder(Maneuver.TYPE_STRAIGHT)
        }
        return builder.build()
    }
}
