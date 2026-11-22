package com.example.nagivator.car.surface

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.data.repository.AppThemeMode
import com.example.nagivator.data.repository.MapColorScheme
import com.example.nagivator.data.repository.RoadContextRepository
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RoadSignal
import com.example.nagivator.domain.model.RoadSignalType
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TrafficIncident
import com.example.nagivator.domain.model.TrafficIncidentType
import com.example.nagivator.ui.map.MapLibreStyleHelper
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

enum class MapPerspective {
    NORTH_UP,
    HEADING_UP
}

private const val CAR_SRC_ROUTE = "car-route-src"
private const val CAR_LYR_ROUTE_CASING = "car-route-casing"
private const val CAR_LYR_ROUTE_CORE = "car-route-core"
private const val CAR_SRC_DEST = "car-dest-src"
private const val CAR_LYR_DEST = "car-dest-lyr"
private const val CAR_SRC_SIGNALS = "car-signals-src"
private const val CAR_LYR_SIGNALS = "car-signals-lyr"
private const val CAR_SRC_VEHICLE = "car-vehicle-src"
private const val CAR_LYR_VEHICLE = "car-vehicle-lyr"

private const val CAR_IMG_VEHICLE = "car-img-vehicle"
private const val CAR_IMG_DEST = "car-img-dest"
private const val CAR_IMG_LIGHT = "car-img-light"
private const val CAR_IMG_STOP = "car-img-stop"
private const val CAR_IMG_GIVEWAY = "car-img-giveway"
private const val CAR_IMG_ROUNDABOUT = "car-img-roundabout"
private const val CAR_IMG_CLOSURE = "car-img-closure"
private const val CAR_IMG_WARN = "car-img-warn"

/**
 * Android Auto SurfaceCallback backed by MapLibre Native GPU vector rendering
 * inside a VirtualDisplay + Presentation (TextureView mode) with a crisp native HUD overlay.
 */
class CarMapSurfaceRenderer(private val carContext: CarContext) : SurfaceCallback {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val userSettingsRepository = UserSettingsRepository.getInstance(carContext)

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: MapView? = null
    private var mapLibreMap: MapLibreMap? = null
    private var loadedStyle: Style? = null
    private var hudOverlay: CarHudOverlayView? = null

    private var surfaceWidth = 800
    private var surfaceHeight = 480
    private var visibleArea: Rect? = null
    private var stableArea: Rect? = null

    private var currentZoom = 16
    @Volatile
    private var perspective: MapPerspective = userSettingsRepository.settings.value.carMapPerspective
    private var manualZoomPauseUntilMs = 0L

    private var currentLocation: LocationPoint? = null
    private var activeRoute: RouteResult? = null
    private var isFollowLocation = true

    private var appliedIsDark: Boolean? = null
    private var appliedScheme: MapColorScheme? = null

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        val width = surfaceContainer.width.coerceAtLeast(400)
        val height = surfaceContainer.height.coerceAtLeast(300)
        val dpi = surfaceContainer.dpi.coerceAtLeast(160)

        mainHandler.post {
            teardownPresentation()
            surfaceWidth = width
            surfaceHeight = height

            try {
                MapLibre.getInstance(carContext.applicationContext)
                val dm = carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val vd = dm.createVirtualDisplay(
                    "NagivatorCarMap",
                    width,
                    height,
                    dpi,
                    surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                )
                virtualDisplay = vd

                val container = FrameLayout(carContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                // TextureView mode is essential for smooth GPU compositing inside Android Auto VirtualDisplay
                val options = MapLibreMapOptions.createFromAttributes(carContext)
                    .textureMode(true)
                    .compassEnabled(false)
                    .logoEnabled(false)
                    .attributionEnabled(false)

                val mv = MapView(carContext, options).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                val hud = CarHudOverlayView(carContext).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                container.addView(mv)
                container.addView(hud)

                val pres = object : Presentation(carContext, vd.display) {}
                pres.setContentView(container)
                pres.show()

                mv.onCreate(null)
                mv.onStart()
                mv.onResume()

                presentation = pres
                mapView = mv
                hudOverlay = hud

                mv.getMapAsync { map ->
                    map.uiSettings.apply {
                        isCompassEnabled = false
                        isLogoEnabled = false
                        isAttributionEnabled = false
                    }
                    mapLibreMap = map
                    applyThemeAndStyle(forceReload = true)
                    syncCamera(animate = false)
                }
            } catch (_: Exception) {
                // Prevent host crash if surface becomes invalid mid-setup
            }
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
        mainHandler.post {
            hudOverlay?.updateSafeAreas(visibleArea, stableArea)
            syncCamera(animate = false)
        }
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        this.stableArea = stableArea
        mainHandler.post {
            hudOverlay?.updateSafeAreas(visibleArea, stableArea)
            syncCamera(animate = false)
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        mainHandler.post {
            teardownPresentation()
        }
    }

    private fun teardownPresentation() {
        loadedStyle = null
        mapLibreMap = null
        appliedIsDark = null
        appliedScheme = null
        try {
            mapView?.onPause()
            mapView?.onStop()
            mapView?.onDestroy()
        } catch (_: Exception) {}
        mapView = null
        hudOverlay = null
        try {
            presentation?.dismiss()
        } catch (_: Exception) {}
        presentation = null
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null
    }

    fun getPerspective(): MapPerspective = perspective

    fun togglePerspective(): MapPerspective {
        perspective = if (perspective == MapPerspective.NORTH_UP) {
            MapPerspective.HEADING_UP
        } else {
            MapPerspective.NORTH_UP
        }
        userSettingsRepository.setCarMapPerspective(perspective)
        requestRender()
        return perspective
    }

    fun updateLocation(point: LocationPoint) {
        mainHandler.post {
            val stabilized = CarNavigationHelper.preserveBearingIfStationary(currentLocation, point)
            val settings = userSettingsRepository.settings.value
            if (settings.dynamicZoomEnabled && SystemClock.uptimeMillis() > manualZoomPauseUntilMs) {
                val route = activeRoute
                val stepIdx = NavigationStateHolder.currentStepIndex.value
                val step = if (route != null && stepIdx in route.steps.indices) route.steps[stepIdx] else null
                val maneuverLoc = step?.maneuverLocation
                val distMeters = if (maneuverLoc != null) {
                    CarNavigationHelper.haversineMeters(
                        stabilized.latitude, stabilized.longitude,
                        maneuverLoc.latitude, maneuverLoc.longitude
                    )
                } else {
                    null
                }
                currentZoom = CarNavigationHelper.targetZoomForNavigation(
                    speedMps = stabilized.speedMps,
                    currentZoom = currentZoom,
                    maneuverType = step?.maneuverType,
                    distanceToManeuverMeters = distMeters
                )
            }
            currentLocation = stabilized
            updateVehicleLayer()
            syncCamera(animate = true)
            hudOverlay?.updateSpeed(stabilized.speedMps, NavigationStateHolder.currentSpeedLimitKmh.value)
        }
    }

    fun updateRoute(route: RouteResult?) {
        activeRoute = route
        mainHandler.post {
            updateRouteLayer()
            syncCamera(animate = true)
        }
    }

    fun zoomIn() {
        if (currentZoom < 18) {
            currentZoom++
            manualZoomPauseUntilMs = SystemClock.uptimeMillis() + 15_000L
            requestRender()
        }
    }

    fun zoomOut() {
        if (currentZoom > 11) {
            currentZoom--
            manualZoomPauseUntilMs = SystemClock.uptimeMillis() + 15_000L
            requestRender()
        }
    }

    fun centerOnLocation() {
        isFollowLocation = true
        manualZoomPauseUntilMs = 0L
        requestRender()
    }

    fun requestRender() {
        mainHandler.post {
            perspective = userSettingsRepository.settings.value.carMapPerspective
            applyThemeAndStyle(forceReload = false)
            updateSignalsLayer()
            updateRouteLayer()
            updateVehicleLayer()
            syncCamera(animate = true)
            hudOverlay?.updateSpeed(
                currentLocation?.speedMps ?: 0f,
                NavigationStateHolder.currentSpeedLimitKmh.value
            )
        }
    }

    fun destroy() {
        mainHandler.post {
            teardownPresentation()
        }
    }

    private fun resolveIsDark(): Boolean {
        val themeMode = userSettingsRepository.settings.value.themeMode
        return when (themeMode) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM -> carContext.isDarkMode
        }
    }

    private fun applyThemeAndStyle(forceReload: Boolean) {
        val map = mapLibreMap ?: return
        val isDark = resolveIsDark()
        val scheme = userSettingsRepository.settings.value.mapColorScheme

        if (!forceReload && appliedIsDark == isDark && loadedStyle?.isFullyLoaded == true) {
            if (isDark && appliedScheme != scheme) {
                MapLibreStyleHelper.applyNightColorScheme(loadedStyle!!, scheme)
                appliedScheme = scheme
            }
            return
        }

        appliedIsDark = isDark
        appliedScheme = scheme
        val styleUrl = MapLibreStyleHelper.styleUrlFor(isDark)
        map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            if (isDark) {
                MapLibreStyleHelper.applyNightColorScheme(style, scheme)
            }
            installCarLayers(style)
            loadedStyle = style
            updateRouteLayer()
            updateSignalsLayer()
            updateVehicleLayer()
        }
    }

    private fun installCarLayers(style: Style) {
        style.addImage(CAR_IMG_VEHICLE, createVehicleArrowBitmap())
        style.addImage(CAR_IMG_DEST, createDestPinBitmap())
        style.addImage(CAR_IMG_LIGHT, createCarTrafficLightBitmap())
        style.addImage(CAR_IMG_STOP, createCarStopBitmap())
        style.addImage(CAR_IMG_GIVEWAY, createCarGiveWayBitmap())
        style.addImage(CAR_IMG_ROUNDABOUT, createCarRoundaboutBitmap())
        style.addImage(CAR_IMG_CLOSURE, createCarIncidentBitmap(Color.parseColor("#D32F2F")))
        style.addImage(CAR_IMG_WARN, createCarIncidentBitmap(Color.parseColor("#F57C00")))

        style.addSource(GeoJsonSource(CAR_SRC_ROUTE))
        style.addLayer(
            LineLayer(CAR_LYR_ROUTE_CASING, CAR_SRC_ROUTE).withProperties(
                PropertyFactory.lineColor("#00363D"),
                PropertyFactory.lineWidth(12f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        style.addLayer(
            LineLayer(CAR_LYR_ROUTE_CORE, CAR_SRC_ROUTE).withProperties(
                PropertyFactory.lineColor("#00B8D4"),
                PropertyFactory.lineWidth(7.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )

        style.addSource(GeoJsonSource(CAR_SRC_SIGNALS))
        style.addLayer(
            SymbolLayer(CAR_LYR_SIGNALS, CAR_SRC_SIGNALS).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(0.95f)
            ).apply {
                minZoom = 14.0f
            }
        )

        style.addSource(GeoJsonSource(CAR_SRC_DEST))
        style.addLayer(
            SymbolLayer(CAR_LYR_DEST, CAR_SRC_DEST).withProperties(
                PropertyFactory.iconImage(CAR_IMG_DEST),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(0.9f)
            )
        )

        style.addSource(GeoJsonSource(CAR_SRC_VEHICLE))
        style.addLayer(
            SymbolLayer(CAR_LYR_VEHICLE, CAR_SRC_VEHICLE).withProperties(
                PropertyFactory.iconImage(CAR_IMG_VEHICLE),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(1.0f)
            )
        )
    }

    private fun updateRouteLayer() {
        val style = loadedStyle ?: return
        if (!style.isFullyLoaded) return
        val route = activeRoute
        val routeSrc = style.getSourceAs<GeoJsonSource>(CAR_SRC_ROUTE)
        val destSrc = style.getSourceAs<GeoJsonSource>(CAR_SRC_DEST)

        if (route != null && route.geometryPoints.size >= 2) {
            val pts = route.geometryPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
            routeSrc?.setGeoJson(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(LineString.fromLngLats(pts)))))
            val dest = route.geometryPoints.last()
            destSrc?.setGeoJson(
                FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(dest.longitude, dest.latitude))))
            )
        } else {
            routeSrc?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            destSrc?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    private fun updateSignalsLayer() {
        val style = loadedStyle ?: return
        if (!style.isFullyLoaded) return
        val src = style.getSourceAs<GeoJsonSource>(CAR_SRC_SIGNALS) ?: return
        val features = mutableListOf<Feature>()

        if (userSettingsRepository.settings.value.showRoadSignals) {
            val signals: List<RoadSignal> = NavigationStateHolder.roadSignals.value
            signals.forEach { sig ->
                val icon = when (sig.type) {
                    RoadSignalType.TRAFFIC_SIGNALS -> CAR_IMG_LIGHT
                    RoadSignalType.STOP -> CAR_IMG_STOP
                    RoadSignalType.GIVE_WAY -> CAR_IMG_GIVEWAY
                    RoadSignalType.ROUNDABOUT -> CAR_IMG_ROUNDABOUT
                }
                features.add(
                    Feature.fromGeometry(Point.fromLngLat(sig.longitude, sig.latitude)).apply {
                        addStringProperty("icon", icon)
                    }
                )
            }
        }

        val incidents: List<TrafficIncident> = NavigationStateHolder.activeTrafficIncidents.value
        incidents.forEach { inc ->
            val icon = if (inc.type == TrafficIncidentType.CLOSURE) CAR_IMG_CLOSURE else CAR_IMG_WARN
            features.add(
                Feature.fromGeometry(Point.fromLngLat(inc.longitude, inc.latitude)).apply {
                    addStringProperty("icon", icon)
                }
            )
        }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun updateVehicleLayer() {
        val style = loadedStyle ?: return
        if (!style.isFullyLoaded) return
        val src = style.getSourceAs<GeoJsonSource>(CAR_SRC_VEHICLE) ?: return
        val loc = currentLocation ?: NavigationStateHolder.currentLocation.value
        if (loc == null) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val feat = Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude)).apply {
            addNumberProperty("bearing", loc.bearing.toDouble())
        }
        src.setGeoJson(FeatureCollection.fromFeatures(listOf(feat)))
    }

    private fun syncCamera(animate: Boolean) {
        val map = mapLibreMap ?: return
        val loc = currentLocation ?: NavigationStateHolder.currentLocation.value
            ?: LocationPoint(latitude = 51.7732, longitude = 6.2924, name = "Start")

        val safe = stableArea ?: visibleArea ?: Rect(0, 0, surfaceWidth, surfaceHeight)
        val extraTopPad = if (perspective == MapPerspective.HEADING_UP) (safe.height() * 0.28f).toInt() else 0
        val padLeft = safe.left.coerceAtLeast(0).toDouble()
        val padTop = (safe.top + extraTopPad).coerceAtLeast(0).toDouble()
        val padRight = (surfaceWidth - safe.right).coerceAtLeast(0).toDouble()
        val padBottom = (surfaceHeight - safe.bottom).coerceAtLeast(0).toDouble()

        val targetBearing = if (perspective == MapPerspective.HEADING_UP) loc.bearing.toDouble() else 0.0
        val targetTilt = if (perspective == MapPerspective.HEADING_UP && activeRoute != null) 30.0 else 0.0
        // Vector zoom level matches OSM visual scale closely at (currentZoom - 0.6)
        val vectorZoom = (currentZoom - 0.5).coerceIn(10.0, 18.0)

        val pos = CameraPosition.Builder()
            .target(LatLng(loc.latitude, loc.longitude))
            .zoom(vectorZoom)
            .bearing(targetBearing)
            .tilt(targetTilt)
            .padding(padLeft, padTop, padRight, padBottom)
            .build()

        val update = CameraUpdateFactory.newCameraPosition(pos)
        if (animate) {
            map.easeCamera(update, 650)
        } else {
            map.moveCamera(update)
        }
    }

    private fun createVehicleArrowBitmap(): Bitmap {
        val size = 84
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c = size / 2f

        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4400B8D4") }
        canvas.drawCircle(c, c, 38f, halo)

        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00838F") }
        canvas.drawCircle(c, c, 24f, body)

        val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val path = Path().apply {
            moveTo(c, c - 15f)
            lineTo(c + 11f, c + 10f)
            lineTo(c, c + 5f)
            lineTo(c - 11f, c + 10f)
            close()
        }
        canvas.drawPath(path, arrow)
        return bmp
    }

    private fun createDestPinBitmap(): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D32F2F") }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(c, c, 18f, fill)
        canvas.drawCircle(c, c, 18f, stroke)
        return bmp
    }

    private fun createCarTrafficLightBitmap(): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c = size / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.parseColor("#1E2228")
        canvas.drawRoundRect(c - 11f, c - 22f, c + 11f, c + 22f, 6f, 6f, p)
        p.color = Color.parseColor("#FF3B30")
        canvas.drawCircle(c, c - 12f, 4.5f, p)
        p.color = Color.parseColor("#FFCC00")
        canvas.drawCircle(c, c, 4.5f, p)
        p.color = Color.parseColor("#34C759")
        canvas.drawCircle(c, c + 12f, 4.5f, p)
        return bmp
    }

    private fun createCarStopBitmap(): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D32F2F") }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(c, c, 18f, fill)
        canvas.drawCircle(c, c, 18f, stroke)
        return bmp
    }

    private fun createCarGiveWayBitmap(): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c = size / 2f
        val tri = Path().apply {
            moveTo(c - 18f, c - 13f)
            lineTo(c + 18f, c - 13f)
            lineTo(c, c + 18f)
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D32F2F")
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
        }
        canvas.drawPath(tri, fill)
        canvas.drawPath(tri, stroke)
        return bmp
    }

    private fun createCarRoundaboutBitmap(): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1976D2") }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(c, c, 18f, fill)
        canvas.drawCircle(c, c, 10f, stroke)
        return bmp
    }

    private fun createCarIncidentBitmap(color: Int): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawCircle(c, c, 18f, fill)
        val y = c - (text.descent() + text.ascent()) / 2f
        canvas.drawText("!", c, y, text)
        return bmp
    }
}

/**
 * Transparent overlay view drawn on top of the MapLibre TextureView inside the Android Auto
 * Presentation, rendering the compact speedometer and speed-limit sign in the safe area.
 */
private class CarHudOverlayView(context: Context) : View(context) {
    private var speedMps: Float = 0f
    private var speedLimitKmh: Int? = null
    private var visibleArea: Rect? = null
    private var stableArea: Rect? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun updateSpeed(speedMps: Float, speedLimitKmh: Int?) {
        this.speedMps = speedMps
        this.speedLimitKmh = speedLimitKmh
        invalidate()
    }

    fun updateSafeAreas(visible: Rect?, stable: Rect?) {
        this.visibleArea = visible
        this.stableArea = stable
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val safe = stableArea ?: visibleArea ?: Rect(0, 0, width, height)
        val currentKmh = (speedMps * 3.6f).roundToInt().coerceAtLeast(0)
        val limit = speedLimitKmh
        val isOverLimit = limit != null && limit > 0 && currentKmh > limit + 5

        val boxW = 76f
        val boxH = 54f
        val margin = 16f
        val rightEdge = (safe.right - margin).coerceAtMost(width - margin)
        val bottomEdge = (safe.bottom - margin).coerceAtMost(height - margin)

        val speedRect = RectF(rightEdge - boxW, bottomEdge - boxH, rightEdge, bottomEdge)
        bgPaint.color = if (isOverLimit) Color.parseColor("#D32F2F") else Color.parseColor("#DD1E242D")
        canvas.drawRoundRect(speedRect, 14f, 14f, bgPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 26f
        canvas.drawText(currentKmh.toString(), speedRect.centerX(), speedRect.top + 31f, textPaint)

        textPaint.color = if (isOverLimit) Color.parseColor("#FFCDD2") else Color.parseColor("#B0BEC5")
        textPaint.textSize = 11f
        canvas.drawText("km/h", speedRect.centerX(), speedRect.bottom - 8f, textPaint)

        if (limit != null) {
            val radius = 25f
            val cx = speedRect.left - 12f - radius
            val cy = speedRect.centerY()
            bgPaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, radius, bgPaint)

            strokePaint.color = if (limit == RoadContextRepository.SPEED_LIMIT_UNLIMITED) {
                Color.parseColor("#78909C")
            } else {
                Color.parseColor("#D32F2F")
            }
            strokePaint.strokeWidth = 5.5f
            canvas.drawCircle(cx, cy, radius - 2.5f, strokePaint)

            textPaint.color = Color.parseColor("#111111")
            textPaint.textSize = if (limit >= 100) 18f else 21f
            val label = if (limit == RoadContextRepository.SPEED_LIMIT_UNLIMITED) "∞" else limit.toString()
            val y = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, cx, y, textPaint)
        }
    }
}
