package com.example.nagivator.car

import androidx.car.app.navigation.model.Maneuver
import org.junit.Assert.assertEquals
import org.junit.Test

class CarManeuverConverterTest {

    @Test
    fun toCarManeuver_departAndArrive() {
        val depart = CarManeuverConverter.toCarManeuver("depart", "")
        assertEquals(Maneuver.TYPE_DEPART, depart.type)

        val arrive = CarManeuverConverter.toCarManeuver("arrive", "")
        assertEquals(Maneuver.TYPE_DESTINATION, arrive.type)
    }

    @Test
    fun toCarManeuver_turns() {
        val turnLeft = CarManeuverConverter.toCarManeuver("turn", "left")
        assertEquals(Maneuver.TYPE_TURN_NORMAL_LEFT, turnLeft.type)

        val sharpRight = CarManeuverConverter.toCarManeuver("turn", "sharp_right")
        assertEquals(Maneuver.TYPE_TURN_SHARP_RIGHT, sharpRight.type)

        val slightLeft = CarManeuverConverter.toCarManeuver("turn", "slight_left")
        assertEquals(Maneuver.TYPE_TURN_SLIGHT_LEFT, slightLeft.type)

        val uTurn = CarManeuverConverter.toCarManeuver("turn", "uturn")
        assertEquals(Maneuver.TYPE_U_TURN_LEFT, uTurn.type)
    }

    @Test
    fun toCarManeuver_roundabouts() {
        val roundabout = CarManeuverConverter.toCarManeuver("roundabout", "right")
        assertEquals(Maneuver.TYPE_ROUNDABOUT_ENTER_CCW, roundabout.type)

        val roundaboutWithExit = CarManeuverConverter.toCarManeuver("roundabout", "right", exitNumber = 2)
        assertEquals(Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW, roundaboutWithExit.type)
        assertEquals(2, roundaboutWithExit.roundaboutExitNumber)
    }

    @Test
    fun toCarManeuver_mergesAndForks() {
        val mergeLeft = CarManeuverConverter.toCarManeuver("merge", "left")
        assertEquals(Maneuver.TYPE_MERGE_LEFT, mergeLeft.type)

        val forkRight = CarManeuverConverter.toCarManeuver("fork", "right")
        assertEquals(Maneuver.TYPE_FORK_RIGHT, forkRight.type)
    }
}
