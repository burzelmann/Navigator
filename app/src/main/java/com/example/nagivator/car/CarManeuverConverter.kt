package com.example.nagivator.car

import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.model.Maneuver
import androidx.core.graphics.drawable.IconCompat
import com.example.nagivator.R

object CarManeuverConverter {
    fun toCarManeuver(
        type: String,
        modifier: String,
        exitNumber: Int? = null,
        carContext: CarContext? = null
    ): Maneuver {
        val typeNorm = type.lowercase().trim()
        val modNorm = modifier.lowercase().trim()

        val (builder, iconRes) = when (typeNorm) {
            "depart" -> Maneuver.Builder(Maneuver.TYPE_DEPART) to R.drawable.ic_maneuver_straight
            "arrive" -> Maneuver.Builder(Maneuver.TYPE_DESTINATION) to R.drawable.ic_maneuver_destination
            "merge" -> when (modNorm) {
                "left" -> Maneuver.Builder(Maneuver.TYPE_MERGE_LEFT) to R.drawable.ic_maneuver_merge
                "right" -> Maneuver.Builder(Maneuver.TYPE_MERGE_RIGHT) to R.drawable.ic_maneuver_merge
                else -> Maneuver.Builder(Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED) to R.drawable.ic_maneuver_merge
            }
            "fork" -> when (modNorm) {
                "left" -> Maneuver.Builder(Maneuver.TYPE_FORK_LEFT) to R.drawable.ic_maneuver_slight_left
                "right" -> Maneuver.Builder(Maneuver.TYPE_FORK_RIGHT) to R.drawable.ic_maneuver_slight_right
                else -> Maneuver.Builder(Maneuver.TYPE_STRAIGHT) to R.drawable.ic_maneuver_straight
            }
            "roundabout", "rotary" -> {
                if (exitNumber != null && exitNumber > 0) {
                    Maneuver.Builder(Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW)
                        .setRoundaboutExitNumber(exitNumber) to R.drawable.ic_maneuver_roundabout
                } else {
                    Maneuver.Builder(Maneuver.TYPE_ROUNDABOUT_ENTER_CCW) to R.drawable.ic_maneuver_roundabout
                }
            }
            "roundabout_exit" -> Maneuver.Builder(Maneuver.TYPE_ROUNDABOUT_EXIT_CCW) to R.drawable.ic_maneuver_roundabout
            "turn", "end of road" -> when (modNorm) {
                "sharp_right" -> Maneuver.Builder(Maneuver.TYPE_TURN_SHARP_RIGHT) to R.drawable.ic_maneuver_sharp_right
                "right" -> Maneuver.Builder(Maneuver.TYPE_TURN_NORMAL_RIGHT) to R.drawable.ic_maneuver_turn_right
                "slight_right" -> Maneuver.Builder(Maneuver.TYPE_TURN_SLIGHT_RIGHT) to R.drawable.ic_maneuver_slight_right
                "sharp_left" -> Maneuver.Builder(Maneuver.TYPE_TURN_SHARP_LEFT) to R.drawable.ic_maneuver_sharp_left
                "left" -> Maneuver.Builder(Maneuver.TYPE_TURN_NORMAL_LEFT) to R.drawable.ic_maneuver_turn_left
                "slight_left" -> Maneuver.Builder(Maneuver.TYPE_TURN_SLIGHT_LEFT) to R.drawable.ic_maneuver_slight_left
                "uturn" -> Maneuver.Builder(Maneuver.TYPE_U_TURN_LEFT) to R.drawable.ic_maneuver_uturn_left
                else -> Maneuver.Builder(Maneuver.TYPE_STRAIGHT) to R.drawable.ic_maneuver_straight
            }
            "off ramp", "on ramp" -> when (modNorm) {
                "slight_right", "right" -> Maneuver.Builder(Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT) to R.drawable.ic_maneuver_slight_right
                "slight_left", "left" -> Maneuver.Builder(Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT) to R.drawable.ic_maneuver_slight_left
                else -> Maneuver.Builder(Maneuver.TYPE_STRAIGHT) to R.drawable.ic_maneuver_straight
            }
            "straight", "continue", "new name" -> Maneuver.Builder(Maneuver.TYPE_STRAIGHT) to R.drawable.ic_maneuver_straight
            else -> Maneuver.Builder(Maneuver.TYPE_STRAIGHT) to R.drawable.ic_maneuver_straight
        }
        if (carContext != null) {
            builder.setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build())
        }
        return builder.build()
    }
}
