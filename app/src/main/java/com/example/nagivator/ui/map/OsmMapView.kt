package com.example.nagivator.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.nagivator.data.local.entity.PoiEntity
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Path
import androidx.compose.foundation.isSystemInDarkTheme

// OpenStreetMap Deutschland tile source - completely free, open & no API key required
val OsmDeTileSource = XYTileSource(
    "OSM_DE",
    0, 19, 256, ".png",
    arrayOf("https://tile.openstreetmap.de/"),
    "© OpenStreetMap-Mitwirkende"
)

// Global bitmap cache to avoid memory allocations and GC thrashing
private val poiBitmapCache = HashMap<String, Bitmap>()

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    currentLocation: LocationPoint?,
    azimuthDegrees: Float? = null,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isNavigating: Boolean = false,
    isOverviewMode: Boolean = false,
    centerOnLocationTrigger: Long = 0L,
    pois: List<PoiEntity> = emptyList(),
    activeRoute: RouteResult? = null,
    selectedPoi: PoiEntity? = null,
    onPoiClick: (PoiEntity) -> Unit = {},
    onMapClick: (GeoPoint) -> Unit = {},
    onMapLongClick: (GeoPoint) -> Unit = {},
    onMapReady: (MapView) -> Unit = {}
) {
    val context = LocalContext.current

    // Set proper User-Agent and load osmdroid configuration
    Configuration.getInstance().userAgentValue = "NagivatorApp/1.0 (Android App; com.example.nagivator)"
    Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid_pref", Context.MODE_PRIVATE))

    // Location Pulse Overlay - drawn natively on Canvas
    val locationPulseOverlay = remember { LocationPulseOverlay() }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(OsmDeTileSource)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

            clipToPadding = false
            clipChildren = false

            overlayManager.tilesOverlay.setLoadingBackgroundColor(Color.parseColor("#F4FAFC"))
            overlayManager.tilesOverlay.setLoadingLineColor(Color.TRANSPARENT)

            isVerticalMapRepetitionEnabled = false
            isHorizontalMapRepetitionEnabled = true

            controller.setZoom(15.0)
            val initialCenter = currentLocation?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(52.5200, 13.4050)
            controller.setCenter(initialCenter)
        }
    }

    val rotationGestureOverlay = remember {
        RotationGestureOverlay(mapView).apply {
            isEnabled = true
        }
    }

    LaunchedEffect(isDarkTheme) {
        if (isDarkTheme) {
            // High-contrast, clean dark slate / charcoal color matrix (NO purple/pink!)
            val matrix = ColorMatrix(
                floatArrayOf(
                    -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
                    -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
                    -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
                    0f,       0f,        0f,       1f, 0f
                )
            )
            mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
        } else {
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
        }
        mapView.invalidate()
    }

    locationPulseOverlay.currentLocation = currentLocation
    if (locationPulseOverlay.azimuthDegrees != azimuthDegrees) {
        locationPulseOverlay.azimuthDegrees = azimuthDegrees
        mapView.postInvalidate()
    }

    LaunchedEffect(mapView) {
        onMapReady(mapView)
    }

    // Camera follow tracking in Navigation mode
    LaunchedEffect(isNavigating, isOverviewMode, currentLocation) {
        if (isNavigating && activeRoute != null) {
            if (isOverviewMode && activeRoute.geometryPoints.size >= 2) {
                try {
                    val box = BoundingBox.fromGeoPoints(
                        activeRoute.geometryPoints.map { GeoPoint(it.latitude, it.longitude) }
                    )
                    mapView.zoomToBoundingBox(box, true, 100)
                } catch (_: Exception) {}
            } else if (!isOverviewMode && currentLocation != null) {
                mapView.controller.animateTo(GeoPoint(currentLocation.latitude, currentLocation.longitude), 17.5, 600L)
            }
        }
    }

    // Camera animation when user taps "Mein Standort"
    LaunchedEffect(centerOnLocationTrigger) {
        if (centerOnLocationTrigger > 0L && currentLocation != null) {
            mapView.controller.animateTo(GeoPoint(currentLocation.latitude, currentLocation.longitude), 16.5, 800L)
        }
    }

    // Camera animation when a POI or search result is selected
    LaunchedEffect(selectedPoi) {
        selectedPoi?.let { poi ->
            mapView.controller.animateTo(GeoPoint(poi.latitude, poi.longitude), 16.0, 800L)
        }
    }

    // Update map overlays ONLY when POIs, activeRoute or currentLocation actually change
    LaunchedEffect(pois, activeRoute, currentLocation != null, selectedPoi) {
        mapView.overlays.retainAll(listOf(mapView.overlayManager.tilesOverlay, rotationGestureOverlay))
        if (!mapView.overlays.contains(rotationGestureOverlay)) {
            mapView.overlays.add(rotationGestureOverlay)
        }

        // 1. Map Click overlay (single tap & long press)
        val eventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let { onMapClick(it) }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { onMapLongClick(it) }
                return true
            }
        }
        mapView.overlays.add(MapEventsOverlay(eventsReceiver))

        // 2. Google Maps Style Pulsing Blue Location Overlay
        if (currentLocation != null) {
            mapView.overlays.add(locationPulseOverlay)
        }

        // 3. Active Route Polyline
        activeRoute?.let { route ->
            if (route.geometryPoints.isNotEmpty()) {
                val line = Polyline(mapView).apply {
                    outlinePaint.color = Color.parseColor("#006874")
                    outlinePaint.strokeWidth = 14f
                    val points = route.geometryPoints.map { GeoPoint(it.latitude, it.longitude) }
                    setPoints(points)
                }
                mapView.overlays.add(line)
            }
        }

        // 4. POI Markers with Cached Bitmaps
        val displayPois = pois.toMutableList()
        selectedPoi?.let { selected ->
            if (displayPois.none { it.latitude == selected.latitude && it.longitude == selected.longitude }) {
                displayPois.add(selected)
            }
        }

        displayPois.forEach { poi ->
            if (activeRoute != null && activeRoute.waypoints.size >= 2 && activeRoute.waypoints.any { it.latitude == poi.latitude && it.longitude == poi.longitude }) {
                return@forEach
            }
            val cachedBitmap = getPoiBitmap(poi.iconName, poi.isFavorite)
            val marker = Marker(mapView).apply {
                position = GeoPoint(poi.latitude, poi.longitude)
                title = poi.displayTitle
                snippet = poi.description
                icon = BitmapDrawable(context.resources, cachedBitmap)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    onPoiClick(poi)
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        
        // 5. Waypoint Markers (if active route has multiple points)
        if (activeRoute != null && activeRoute.waypoints.size >= 2) {
            activeRoute.waypoints.forEachIndexed { index, point ->
                val cachedBitmap = getWaypointBitmap(index)
                val marker = Marker(mapView).apply {
                    position = GeoPoint(point.latitude, point.longitude)
                    title = point.name
                    icon = BitmapDrawable(context.resources, cachedBitmap)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(marker)
            }
        }

        mapView.invalidate()
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView }
    )
}

/**
 * Ultra performant native location overlay.
 * Animates the pulsing blue dot directly on the Android Canvas without triggering Compose recomposition.
 */
private class LocationPulseOverlay : Overlay() {
    var currentLocation: LocationPoint? = null
    var azimuthDegrees: Float? = null

    private val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4285F4")
        style = Paint.Style.FILL
        alpha = 85
    }
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4285F4")
        style = Paint.Style.FILL
    }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A73E8")
        style = Paint.Style.FILL
    }
    private val screenPoint = Point()
    private val conePath = Path()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val loc = currentLocation ?: return

        val geoPoint = GeoPoint(loc.latitude, loc.longitude)
        mapView.projection.toPixels(geoPoint, screenPoint)

        val cx = screenPoint.x.toFloat()
        val cy = screenPoint.y.toFloat()

        // 1. Draw orientation cone if azimuth is available
        azimuthDegrees?.let { azimuth ->
            val mapRotation = mapView.mapOrientation
            val totalRotation = azimuth - mapRotation

            canvas.save()
            canvas.rotate(totalRotation, cx, cy)

            conePath.reset()
            conePath.moveTo(cx, cy)
            val coneLength = 65f
            val coneAngleRad = Math.toRadians(25.0)

            val x1 = (cx + coneLength * Math.sin(-coneAngleRad)).toFloat()
            val y1 = (cy - coneLength * Math.cos(-coneAngleRad)).toFloat()
            val x2 = (cx + coneLength * Math.sin(coneAngleRad)).toFloat()
            val y2 = (cy - coneLength * Math.cos(coneAngleRad)).toFloat()

            conePath.lineTo(x1, y1)
            conePath.lineTo(x2, y2)
            conePath.close()

            canvas.drawPath(conePath, conePaint)
            canvas.restore()
        }

        val progress = (System.currentTimeMillis() % 1600L) / 1600f
        val auraRadius = 20f + (progress * 42f)
        val auraAlpha = ((1f - progress) * 110).toInt().coerceIn(0, 255)

        auraPaint.alpha = auraAlpha
        canvas.drawCircle(cx, cy, auraRadius, auraPaint)
        canvas.drawCircle(cx, cy, 18f, whitePaint)
        canvas.drawCircle(cx, cy, 13f, bluePaint)
    }
}

private fun getPoiBitmap(iconKey: String, isFavorite: Boolean): Bitmap {
    val cacheKey = "$iconKey-$isFavorite"
    return poiBitmapCache.getOrPut(cacheKey) {
        createPoiMarkerBitmap(iconKey, isFavorite)
    }
}

private fun createPoiMarkerBitmap(iconKey: String, isFavorite: Boolean): Bitmap {
    val size = 100
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center + 4f, 32f, shadowPaint)

    val pinColor = if (isFavorite) Color.parseColor("#D93025") else Color.parseColor("#006874")
    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, 32f, pinPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(center, center, 32f, borderPaint)

    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, 12f, innerPaint)

    return bitmap
}

private fun getWaypointBitmap(index: Int): Bitmap {
    val cacheKey = "waypoint-$index"
    return poiBitmapCache.getOrPut(cacheKey) {
        createWaypointMarkerBitmap(index)
    }
}

private fun createWaypointMarkerBitmap(index: Int): Bitmap {
    val size = 100
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center + 4f, 32f, shadowPaint)

    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E57373")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, 32f, pinPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(center, center, 32f, borderPaint)

    val letter = ('A' + index).toString()
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val textY = center - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(letter, center, textY, textPaint)

    return bitmap
}
