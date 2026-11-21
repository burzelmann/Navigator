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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.nagivator.data.local.entity.PoiEntity
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RoadSignal
import com.example.nagivator.domain.model.RoadSignalType
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TrafficIncident
import com.example.nagivator.domain.model.TrafficIncidentType
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

import android.graphics.Path
import android.graphics.drawable.Drawable
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.nagivator.data.repository.MapColorScheme
import java.io.InputStream

// OpenStreetMap Deutschland tile source - completely free, open & no API key required
val OsmDeTileSource = XYTileSource(
    "OSM_DE",
    0, 19, 256, ".png",
    arrayOf("https://tile.openstreetmap.de/"),
    "© OpenStreetMap-Mitwirkende"
)

private class SemanticNightOsmTileSource(
    private val scheme: MapColorScheme
) : XYTileSource(
    "OSM_DE",
    0, 19, 256, ".png",
    arrayOf("https://tile.openstreetmap.de/"),
    "© OpenStreetMap-Mitwirkende"
) {
    override fun getDrawable(aFilePath: String?): Drawable? {
        val raw = super.getDrawable(aFilePath) as? BitmapDrawable ?: return null
        val bmp = raw.bitmap ?: return raw
        val styled = MapStyleHelper.transformNightBitmap(bmp, scheme)
        return BitmapDrawable(null, styled)
    }

    override fun getDrawable(aFileInputStream: InputStream?): Drawable? {
        val raw = super.getDrawable(aFileInputStream) as? BitmapDrawable ?: return null
        val bmp = raw.bitmap ?: return raw
        val styled = MapStyleHelper.transformNightBitmap(bmp, scheme)
        return BitmapDrawable(null, styled)
    }
}

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
    alternativeRoutes: List<RouteResult> = emptyList(),
    selectedPoi: PoiEntity? = null,
    onSelectAlternativeRoute: (RouteResult) -> Unit = {},
    onPoiClick: (PoiEntity) -> Unit = {},
    onMapClick: (GeoPoint) -> Unit = {},
    onMapLongClick: (GeoPoint) -> Unit = {},
    onMapReady: (MapView) -> Unit = {}
) {
    val context = LocalContext.current

    // Set proper User-Agent and load osmdroid configuration
    Configuration.getInstance().userAgentValue = "NagivatorApp/1.0 (Android App; com.verhuelsdonk.navigator)"
    Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid_pref", Context.MODE_PRIVATE))

    // Location Pulse Overlay - drawn natively on Canvas
    val locationPulseOverlay = remember { LocationPulseOverlay() }
    val roadSignalsOverlay = remember { RoadSignalsOverlay() }
    val roadSignals by NavigationStateHolder.roadSignals.collectAsState()
    val trafficIncidents by NavigationStateHolder.activeTrafficIncidents.collectAsState()

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

    val userSettings by remember {
        UserSettingsRepository.getInstance(context).settings
    }.collectAsState()

    LaunchedEffect(isDarkTheme, userSettings.mapColorScheme) {
        mapView.overlayManager.tilesOverlay.setColorFilter(null)
        mapView.overlayManager.tilesOverlay.setLoadingBackgroundColor(
            MapStyleHelper.canvasBackgroundColor(isDarkTheme, userSettings.mapColorScheme)
        )
        mapView.tileProvider.clearTileCache()
        mapView.setTileSource(
            if (isDarkTheme) SemanticNightOsmTileSource(userSettings.mapColorScheme) else OsmDeTileSource
        )
        mapView.invalidate()
    }

    locationPulseOverlay.currentLocation = currentLocation
    roadSignalsOverlay.signals = if (userSettings.showRoadSignals) roadSignals else emptyList()
    roadSignalsOverlay.incidents = trafficIncidents
    if (locationPulseOverlay.azimuthDegrees != azimuthDegrees) {
        locationPulseOverlay.azimuthDegrees = azimuthDegrees
        mapView.postInvalidate()
    }
    LaunchedEffect(roadSignals, trafficIncidents, userSettings.showRoadSignals) {
        if (!mapView.overlays.contains(roadSignalsOverlay)) {
            mapView.overlays.add(roadSignalsOverlay)
        }
        mapView.invalidate()
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

    // Update map overlays ONLY when POIs, activeRoute, alternativeRoutes, parkedCar or currentLocation actually change
    LaunchedEffect(pois, activeRoute, alternativeRoutes, isNavigating, currentLocation != null, selectedPoi, userSettings.parkedCar) {
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

        // 3a. Alternative Route Polylines (rendered underneath the active route; clickable to switch)
        if (!isNavigating && alternativeRoutes.size > 1) {
            alternativeRoutes.forEach { altRoute ->
                if (altRoute != activeRoute && altRoute.geometryPoints.isNotEmpty()) {
                    val altLine = Polyline(mapView).apply {
                        outlinePaint.color = Color.parseColor("#99607D8B")
                        outlinePaint.strokeWidth = 11f
                        setPoints(altRoute.geometryPoints.map { GeoPoint(it.latitude, it.longitude) })
                        setOnClickListener { _, _, _ ->
                            onSelectAlternativeRoute(altRoute)
                            true
                        }
                    }
                    mapView.overlays.add(altLine)
                }
            }
        }

        // 3b. Active Route Polyline
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

        // 6. Parked Car Marker ("P" pin)
        userSettings.parkedCar?.let { parked ->
            val parkedBitmap = getParkedCarBitmap()
            val marker = Marker(mapView).apply {
                position = GeoPoint(parked.latitude, parked.longitude)
                title = "Parkplatz: ${parked.label}"
                icon = BitmapDrawable(context.resources, parkedBitmap)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(marker)
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
 * Draws prominent OSM road signals (traffic lights, stop signs, give-way, roundabouts)
 * and Autobahn traffic incidents natively on the osmdroid Canvas.
 */
private class RoadSignalsOverlay : Overlay() {
    var signals: List<RoadSignal> = emptyList()
    var incidents: List<TrafficIncident> = emptyList()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val screenPt = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (mapView.zoomLevelDouble >= 14.5) {
            for (sig in signals) {
                mapView.projection.toPixels(GeoPoint(sig.latitude, sig.longitude), screenPt)
                val x = screenPt.x.toFloat()
                val y = screenPt.y.toFloat()
                when (sig.type) {
                    RoadSignalType.TRAFFIC_SIGNALS -> {
                        fillPaint.color = Color.parseColor("#1E2228")
                        canvas.drawRoundRect(x - 7f, y - 14f, x + 7f, y + 14f, 4f, 4f, fillPaint)
                        fillPaint.color = Color.parseColor("#FF3B30")
                        canvas.drawCircle(x, y - 8f, 3f, fillPaint)
                        fillPaint.color = Color.parseColor("#FFCC00")
                        canvas.drawCircle(x, y, 3f, fillPaint)
                        fillPaint.color = Color.parseColor("#34C759")
                        canvas.drawCircle(x, y + 8f, 3f, fillPaint)
                    }
                    RoadSignalType.STOP -> {
                        fillPaint.color = Color.parseColor("#D32F2F")
                        canvas.drawCircle(x, y, 11f, fillPaint)
                        strokePaint.color = Color.WHITE
                        strokePaint.strokeWidth = 2f
                        canvas.drawCircle(x, y, 11f, strokePaint)
                    }
                    RoadSignalType.GIVE_WAY -> {
                        val tri = Path().apply {
                            moveTo(x - 11f, y - 8f)
                            lineTo(x + 11f, y - 8f)
                            lineTo(x, y + 11f)
                            close()
                        }
                        fillPaint.color = Color.WHITE
                        canvas.drawPath(tri, fillPaint)
                        strokePaint.color = Color.parseColor("#D32F2F")
                        strokePaint.strokeWidth = 3f
                        canvas.drawPath(tri, strokePaint)
                    }
                    RoadSignalType.ROUNDABOUT -> {
                        fillPaint.color = Color.parseColor("#1565C0")
                        canvas.drawCircle(x, y, 11f, fillPaint)
                        strokePaint.color = Color.WHITE
                        strokePaint.strokeWidth = 2.4f
                        canvas.drawCircle(x, y, 5.5f, strokePaint)
                    }
                }
            }
        }
        for (inc in incidents) {
            mapView.projection.toPixels(GeoPoint(inc.latitude, inc.longitude), screenPt)
            val x = screenPt.x.toFloat()
            val y = screenPt.y.toFloat()
            fillPaint.color = if (inc.type == TrafficIncidentType.CLOSURE || inc.isBlocked) {
                Color.parseColor("#D32F2F")
            } else {
                Color.parseColor("#FFC107")
            }
            canvas.drawCircle(x, y, 13f, fillPaint)
            strokePaint.color = Color.WHITE
            strokePaint.strokeWidth = 2.5f
            canvas.drawCircle(x, y, 13f, strokePaint)
        }
    }
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

private fun getParkedCarBitmap(): Bitmap {
    return poiBitmapCache.getOrPut("parked-car") {
        val size = 104
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44000000")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center + 4f, 36f, shadowPaint)

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1565C0")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 34f, pinPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawCircle(center, center, 34f, borderPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val textY = center - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText("P", center, textY, textPaint)

        bitmap
    }
}
