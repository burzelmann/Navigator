package com.example.nagivator.car.surface

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

class CarMapSurfaceRenderer(private val carContext: CarContext) : SurfaceCallback {

    private var surface: Surface? = null
    private var surfaceWidth = 800
    private var surfaceHeight = 480
    private var visibleArea: Rect? = null

    private var currentZoom = 16
    private var currentLocation: LocationPoint? = null
    private var activeRoute: RouteResult? = null
    private var isFollowLocation = true

    private val tileProvider = CarTileProvider(carContext) {
        requestRender()
    }

    private val renderThread = HandlerThread("CarSurfaceRenderer").apply { start() }
    private val renderHandler = Handler(renderThread.looper)
    private var isRenderPending = false

    // Paints
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val darkFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
                -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
                -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
                0f,        0f,        0f,       1f, 0f
            )
        )
    )

    private val routeHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#00363D")
    }

    private val routeCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#00B8D4")
    }

    private val vehicleHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4400B8D4")
    }

    private val vehicleBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00838F")
    }

    private val vehicleArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val destPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D32F2F")
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surface = surfaceContainer.surface
        surfaceWidth = surfaceContainer.width.coerceAtLeast(400)
        surfaceHeight = surfaceContainer.height.coerceAtLeast(300)
        requestRender()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
        requestRender()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        requestRender()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        surface = null
    }

    fun updateLocation(point: LocationPoint) {
        currentLocation = point
        if (isFollowLocation) {
            requestRender()
        }
    }

    fun updateRoute(route: RouteResult?) {
        activeRoute = route
        requestRender()
    }

    fun zoomIn() {
        if (currentZoom < 18) {
            currentZoom++
            requestRender()
        }
    }

    fun zoomOut() {
        if (currentZoom > 11) {
            currentZoom--
            requestRender()
        }
    }

    fun centerOnLocation() {
        isFollowLocation = true
        requestRender()
    }

    fun requestRender() {
        if (surface == null || isRenderPending) return
        isRenderPending = true
        renderHandler.post {
            isRenderPending = false
            render()
        }
    }

    private fun render() {
        val s = surface ?: return
        if (!s.isValid) return

        val canvas = try {
            s.lockHardwareCanvas()
        } catch (_: Exception) {
            try {
                s.lockCanvas(null)
            } catch (_: Exception) {
                null
            }
        } ?: return

        try {
            val isDark = carContext.isDarkMode
            canvas.drawColor(if (isDark) Color.parseColor("#12161A") else Color.parseColor("#EBF2F5"))

            val loc = currentLocation ?: LocationPoint(52.5200, 13.4050, "Berlin")
            val centerLat = loc.latitude
            val centerLon = loc.longitude
            val zoom = currentZoom

            val width = surfaceWidth.toFloat()
            val height = surfaceHeight.toFloat()
            val tileSize = 256f

            val centerTx = lonToTileX(centerLon, zoom)
            val centerTy = latToTileY(centerLat, zoom)

            // 1. Render Map Tiles
            tilePaint.colorFilter = if (isDark) darkFilter else null

            val halfTilesX = (width / (2f * tileSize)) + 1
            val halfTilesY = (height / (2f * tileSize)) + 1

            val minX = (centerTx - halfTilesX).toInt().coerceAtLeast(0)
            val maxX = (centerTx + halfTilesX).toInt().coerceAtMost((1 shl zoom) - 1)
            val minY = (centerTy - halfTilesY).toInt().coerceAtLeast(0)
            val maxY = (centerTy + halfTilesY).toInt().coerceAtMost((1 shl zoom) - 1)

            for (tx in minX..maxX) {
                for (ty in minY..maxY) {
                    val tileBitmap = tileProvider.getTile(zoom, tx, ty)
                    val px = (width / 2f) + (tx - centerTx).toFloat() * tileSize
                    val py = (height / 2f) + (ty - centerTy).toFloat() * tileSize

                    if (tileBitmap != null) {
                        canvas.drawBitmap(tileBitmap, px, py, tilePaint)
                    }
                }
            }

            // Screen projection helper
            fun project(lat: Double, lon: Double): PointF {
                val tx = lonToTileX(lon, zoom)
                val ty = latToTileY(lat, zoom)
                val px = (width / 2f) + (tx - centerTx).toFloat() * tileSize
                val py = (height / 2f) + (ty - centerTy).toFloat() * tileSize
                return PointF(px, py)
            }

            // 2. Render Route Polyline
            val route = activeRoute
            if (route != null && route.geometryPoints.size >= 2) {
                val path = Path()
                route.geometryPoints.forEachIndexed { i, pt ->
                    val p = project(pt.latitude, pt.longitude)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                canvas.drawPath(path, routeHaloPaint)
                canvas.drawPath(path, routeCorePaint)

                // Destination Pin
                val destPt = route.waypoints.lastOrNull() ?: route.geometryPoints.last()
                val destProj = project(destPt.latitude, destPt.longitude)
                canvas.drawCircle(destProj.x, destProj.y, 16f, destPinPaint)
                canvas.drawCircle(destProj.x, destProj.y, 7f, vehicleArrowPaint)
            }

            // 3. Render Vehicle Location & Heading
            val carPt = project(loc.latitude, loc.longitude)
            canvas.drawCircle(carPt.x, carPt.y, 36f, vehicleHaloPaint)
            canvas.drawCircle(carPt.x, carPt.y, 20f, vehicleBodyPaint)

            // Vehicle Heading Arrow
            canvas.save()
            canvas.translate(carPt.x, carPt.y)
            canvas.rotate(loc.bearing)
            val arrowPath = Path().apply {
                moveTo(0f, -14f)
                lineTo(10f, 10f)
                lineTo(0f, 5f)
                lineTo(-10f, 10f)
                close()
            }
            canvas.drawPath(arrowPath, vehicleArrowPaint)
            canvas.restore()

        } finally {
            try {
                s.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {}
        }
    }

    private fun lonToTileX(lon: Double, zoom: Int): Double =
        (lon + 180.0) / 360.0 * (1 shl zoom)

    private fun latToTileY(lat: Double, zoom: Int): Double {
        val rad = Math.toRadians(lat)
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * (1 shl zoom)
    }

    fun destroy() {
        surface = null
        tileProvider.clear()
        renderThread.quitSafely()
    }
}
