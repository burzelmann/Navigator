package com.example.nagivator.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.model.Lane
import androidx.car.app.navigation.model.LaneDirection
import androidx.car.app.navigation.model.Maneuver
import androidx.core.graphics.drawable.IconCompat
import com.example.nagivator.R
import com.example.nagivator.domain.model.LaneInfo

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
                "left", "slight left", "slight_left" -> Maneuver.Builder(Maneuver.TYPE_FORK_LEFT) to R.drawable.ic_maneuver_slight_left
                "right", "slight right", "slight_right" -> Maneuver.Builder(Maneuver.TYPE_FORK_RIGHT) to R.drawable.ic_maneuver_slight_right
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
            "roundabout_exit", "exit roundabout", "exit rotary" ->
                Maneuver.Builder(Maneuver.TYPE_ROUNDABOUT_EXIT_CCW) to R.drawable.ic_maneuver_roundabout
            "turn", "end of road" -> when (modNorm) {
                "sharp_right", "sharp right" -> Maneuver.Builder(Maneuver.TYPE_TURN_SHARP_RIGHT) to R.drawable.ic_maneuver_sharp_right
                "right" -> Maneuver.Builder(Maneuver.TYPE_TURN_NORMAL_RIGHT) to R.drawable.ic_maneuver_turn_right
                "slight_right", "slight right" -> Maneuver.Builder(Maneuver.TYPE_TURN_SLIGHT_RIGHT) to R.drawable.ic_maneuver_slight_right
                "sharp_left", "sharp left" -> Maneuver.Builder(Maneuver.TYPE_TURN_SHARP_LEFT) to R.drawable.ic_maneuver_sharp_left
                "left" -> Maneuver.Builder(Maneuver.TYPE_TURN_NORMAL_LEFT) to R.drawable.ic_maneuver_turn_left
                "slight_left", "slight left" -> Maneuver.Builder(Maneuver.TYPE_TURN_SLIGHT_LEFT) to R.drawable.ic_maneuver_slight_left
                "uturn" -> Maneuver.Builder(Maneuver.TYPE_U_TURN_LEFT) to R.drawable.ic_maneuver_uturn_left
                else -> Maneuver.Builder(Maneuver.TYPE_STRAIGHT) to R.drawable.ic_maneuver_straight
            }
            "off ramp" -> when (modNorm) {
                "slight_right", "slight right", "right" -> Maneuver.Builder(Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT) to R.drawable.ic_maneuver_slight_right
                "slight_left", "slight left", "left" -> Maneuver.Builder(Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT) to R.drawable.ic_maneuver_slight_left
                else -> Maneuver.Builder(Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT) to R.drawable.ic_maneuver_slight_right
            }
            "on ramp" -> when (modNorm) {
                "slight_right", "slight right", "right" -> Maneuver.Builder(Maneuver.TYPE_ON_RAMP_NORMAL_RIGHT) to R.drawable.ic_maneuver_slight_right
                "slight_left", "slight left", "left" -> Maneuver.Builder(Maneuver.TYPE_ON_RAMP_NORMAL_LEFT) to R.drawable.ic_maneuver_slight_left
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

    fun mapIndicationToLaneShape(indication: String): Int = when (indication.lowercase().trim()) {
        "left" -> LaneDirection.SHAPE_NORMAL_LEFT
        "slight left", "slight_left" -> LaneDirection.SHAPE_SLIGHT_LEFT
        "sharp left", "sharp_left" -> LaneDirection.SHAPE_SHARP_LEFT
        "right" -> LaneDirection.SHAPE_NORMAL_RIGHT
        "slight right", "slight_right" -> LaneDirection.SHAPE_SLIGHT_RIGHT
        "sharp right", "sharp_right" -> LaneDirection.SHAPE_SHARP_RIGHT
        "uturn" -> LaneDirection.SHAPE_U_TURN_LEFT
        "straight", "none" -> LaneDirection.SHAPE_STRAIGHT
        else -> LaneDirection.SHAPE_UNKNOWN
    }

    /**
     * Converts OSRM [LaneInfo] entries into Android Auto [Lane] objects for [Step.Builder.addLane].
     */
    fun toCarLanes(lanes: List<LaneInfo>): List<Lane> {
        return lanes.take(6).mapNotNull { laneInfo ->
            val shapes = laneInfo.indications
                .map { mapIndicationToLaneShape(it) }
                .distinct()
                .ifEmpty { listOf(LaneDirection.SHAPE_STRAIGHT) }

            val builder = Lane.Builder()
            shapes.forEach { shape ->
                builder.addDirection(LaneDirection.create(shape, laneInfo.isValid))
            }
            builder.build()
        }
    }

    /**
     * Renders a crisp horizontal lane-guidance strip bitmap required by Android Auto's
     * [Step.Builder.setLanesImage] whenever lanes are attached to a [Step].
     */
    fun renderLanesIcon(lanes: List<LaneInfo>): CarIcon? {
        val visibleLanes = lanes.take(6)
        if (visibleLanes.isEmpty()) return null

        val laneWidth = 64
        val height = 72
        val width = (visibleLanes.size * laneWidth).coerceAtLeast(laneWidth)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(115, 220, 225, 232)
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 255, 255)
            strokeWidth = 2f
        }

        visibleLanes.forEachIndexed { idx, lane ->
            val cx = idx * laneWidth + laneWidth / 2f
            if (idx > 0) {
                val divX = idx * laneWidth.toFloat()
                canvas.drawLine(divX, 14f, divX, height - 14f, dividerPaint)
            }
            val paint = if (lane.isValid) activePaint else inactivePaint
            val shapes = lane.indications
                .map { mapIndicationToLaneShape(it) }
                .distinct()
                .ifEmpty { listOf(LaneDirection.SHAPE_STRAIGHT) }

            shapes.forEach { shape ->
                drawLaneArrow(canvas, cx, height.toFloat(), shape, paint)
            }
        }

        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }

    private fun drawLaneArrow(
        canvas: Canvas,
        cx: Float,
        height: Float,
        shape: Int,
        paint: Paint
    ) {
        val bottomY = height - 12f
        val midY = height * 0.48f
        val topY = 14f
        val path = Path()

        when (shape) {
            LaneDirection.SHAPE_NORMAL_LEFT, LaneDirection.SHAPE_SHARP_LEFT -> {
                path.moveTo(cx, bottomY)
                path.lineTo(cx, midY)
                path.lineTo(cx - 16f, midY)
                // Arrow head left
                path.moveTo(cx - 9f, midY - 7f)
                path.lineTo(cx - 16f, midY)
                path.lineTo(cx - 9f, midY + 7f)
            }
            LaneDirection.SHAPE_SLIGHT_LEFT -> {
                path.moveTo(cx, bottomY)
                path.lineTo(cx, midY + 6f)
                path.lineTo(cx - 12f, topY + 6f)
                path.moveTo(cx - 12f, topY + 15f)
                path.lineTo(cx - 12f, topY + 6f)
                path.lineTo(cx - 3f, topY + 8f)
            }
            LaneDirection.SHAPE_NORMAL_RIGHT, LaneDirection.SHAPE_SHARP_RIGHT -> {
                path.moveTo(cx, bottomY)
                path.lineTo(cx, midY)
                path.lineTo(cx + 16f, midY)
                // Arrow head right
                path.moveTo(cx + 9f, midY - 7f)
                path.lineTo(cx + 16f, midY)
                path.lineTo(cx + 9f, midY + 7f)
            }
            LaneDirection.SHAPE_SLIGHT_RIGHT -> {
                path.moveTo(cx, bottomY)
                path.lineTo(cx, midY + 6f)
                path.lineTo(cx + 12f, topY + 6f)
                path.moveTo(cx + 12f, topY + 15f)
                path.lineTo(cx + 12f, topY + 6f)
                path.lineTo(cx + 3f, topY + 8f)
            }
            LaneDirection.SHAPE_U_TURN_LEFT, LaneDirection.SHAPE_U_TURN_RIGHT -> {
                path.moveTo(cx + 8f, bottomY)
                path.lineTo(cx + 8f, topY + 14f)
                path.quadTo(cx + 8f, topY, cx - 2f, topY)
                path.quadTo(cx - 12f, topY, cx - 12f, topY + 14f)
                path.lineTo(cx - 12f, midY + 4f)
                path.moveTo(cx - 18f, midY - 2f)
                path.lineTo(cx - 12f, midY + 4f)
                path.lineTo(cx - 6f, midY - 2f)
            }
            else -> {
                // Straight arrow
                path.moveTo(cx, bottomY)
                path.lineTo(cx, topY)
                path.moveTo(cx - 8f, topY + 9f)
                path.lineTo(cx, topY)
                path.lineTo(cx + 8f, topY + 9f)
            }
        }
        canvas.drawPath(path, paint)
    }
}
