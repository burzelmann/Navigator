package com.example.nagivator.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.nagivator.data.local.entity.PoiEntity
import com.example.nagivator.data.repository.ParkedCarLocation
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RoadSignal
import com.example.nagivator.domain.model.RoadSignalType
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TrafficIncident
import com.example.nagivator.domain.model.TrafficIncidentType
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Controller exposed to MapScreen for compass reset and offline region bounds queries.
 */
class MapLibreController(val map: MapLibreMap) {
    fun resetNorth() {
        val current = map.cameraPosition
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(current).bearing(0.0).build()
            ),
            300
        )
    }

    fun visibleBounds(): LatLngBounds = map.projection.visibleRegion.latLngBounds

    fun currentZoom(): Double = map.cameraPosition.zoom
}

private const val SRC_ALT_ROUTES = "nagivator-alt-routes-src"
private const val LYR_ALT_ROUTES = "nagivator-alt-routes-lyr"

private const val SRC_ACTIVE_ROUTE = "nagivator-active-route-src"
private const val LYR_ACTIVE_ROUTE_CASING = "nagivator-active-route-casing"
private const val LYR_ACTIVE_ROUTE = "nagivator-active-route-lyr"

private const val SRC_ROAD_SIGNALS = "nagivator-road-signals-src"
private const val LYR_ROAD_SIGNALS = "nagivator-road-signals-lyr"

private const val SRC_POIS = "nagivator-pois-src"
private const val LYR_POIS = "nagivator-pois-lyr"

private const val SRC_WAYPOINTS = "nagivator-waypoints-src"
private const val LYR_WAYPOINTS = "nagivator-waypoints-lyr"

private const val SRC_PARKED_CAR = "nagivator-parked-car-src"
private const val LYR_PARKED_CAR = "nagivator-parked-car-lyr"

private const val SRC_USER_LOC = "nagivator-user-loc-src"
private const val LYR_USER_CONE = "nagivator-user-cone-lyr"
private const val LYR_USER_HALO = "nagivator-user-halo-lyr"
private const val LYR_USER_DOT = "nagivator-user-dot-lyr"

private const val IMG_POI_DEFAULT = "nagivator-poi-default"
private const val IMG_POI_FAV = "nagivator-poi-fav"
private const val IMG_PARKED_CAR = "nagivator-parked-car"
private const val IMG_USER_CONE = "nagivator-user-cone"
private const val IMG_SIG_TRAFFIC_LIGHT = "nagivator-sig-light"
private const val IMG_SIG_STOP = "nagivator-sig-stop"
private const val IMG_SIG_GIVE_WAY = "nagivator-sig-giveway"
private const val IMG_SIG_ROUNDABOUT = "nagivator-sig-roundabout"
private const val IMG_INC_CLOSURE = "nagivator-inc-closure"
private const val IMG_INC_WARN = "nagivator-inc-warn"

@Composable
fun MapLibreMapView(
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
    onMapClick: (Double, Double) -> Unit = { _, _ -> },
    onMapLongClick: (Double, Double) -> Unit = { _, _ -> },
    onBearingChanged: (Float) -> Unit = {},
    onMapReady: (MapLibreController) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember {
        MapLibre.getInstance(context.applicationContext)
    }

    val userSettings by remember {
        UserSettingsRepository.getInstance(context).settings
    }.collectAsState()
    val roadSignals by NavigationStateHolder.roadSignals.collectAsState()
    val trafficIncidents by NavigationStateHolder.activeTrafficIncidents.collectAsState()

    val latestPois by rememberUpdatedState(pois)
    val latestAltRoutes by rememberUpdatedState(alternativeRoutes)
    val latestActiveRoute by rememberUpdatedState(activeRoute)
    val latestIsNavigating by rememberUpdatedState(isNavigating)
    val latestOnPoiClick by rememberUpdatedState(onPoiClick)
    val latestOnSelectAlt by rememberUpdatedState(onSelectAlternativeRoute)
    val latestOnMapClick by rememberUpdatedState(onMapClick)
    val latestOnMapLongClick by rememberUpdatedState(onMapLongClick)
    val latestOnBearingChanged by rememberUpdatedState(onBearingChanged)

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var loadedIsDark by remember { mutableStateOf<Boolean?>(null) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    // Bind MapView lifecycle cleanly to Compose LifecycleOwner
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onStart()
        mapView.onResume()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Initialize MapLibreMap instance once
    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.uiSettings.apply {
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
            }

            val initialCenter = currentLocation?.let { LatLng(it.latitude, it.longitude) }
                ?: LatLng(51.7732, 6.2924)
            map.cameraPosition = CameraPosition.Builder()
                .target(initialCenter)
                .zoom(15.0)
                .build()

            map.addOnCameraMoveListener {
                latestOnBearingChanged(map.cameraPosition.bearing.toFloat())
            }

            map.addOnMapClickListener { latLng ->
                val screenPt: PointF = map.projection.toScreenLocation(latLng)

                // 1. Check POI hit
                val poiHits = map.queryRenderedFeatures(screenPt, LYR_POIS)
                val hitPoiId = poiHits.firstOrNull()?.getStringProperty("poi_key")
                if (hitPoiId != null) {
                    val matched = latestPois.firstOrNull { poiKey(it) == hitPoiId }
                    if (matched != null) {
                        latestOnPoiClick(matched)
                        return@addOnMapClickListener true
                    }
                }

                // 2. Check Alternative Route hit
                if (!latestIsNavigating && latestAltRoutes.size > 1) {
                    val routeHits = map.queryRenderedFeatures(screenPt, LYR_ALT_ROUTES)
                    val altIdx = routeHits.firstOrNull()?.getNumberProperty("alt_index")?.toInt()
                    if (altIdx != null && altIdx in latestAltRoutes.indices) {
                        val chosen = latestAltRoutes[altIdx]
                        if (chosen != latestActiveRoute) {
                            latestOnSelectAlt(chosen)
                            return@addOnMapClickListener true
                        }
                    }
                }

                latestOnMapClick(latLng.latitude, latLng.longitude)
                true
            }

            map.addOnMapLongClickListener { latLng ->
                latestOnMapLongClick(latLng.latitude, latLng.longitude)
                true
            }

            mapLibreMap = map
            onMapReady(MapLibreController(map))
        }
    }

    // Load vector style only when switching between Day and Night (switching between the 4 night schemes is instant in-place!)
    LaunchedEffect(mapLibreMap, isDarkTheme) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (loadedIsDark == isDarkTheme && loadedStyle?.isFullyLoaded == true) return@LaunchedEffect

        val styleUrl = MapLibreStyleHelper.styleUrlFor(isDarkTheme)
        map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            if (isDarkTheme) {
                MapLibreStyleHelper.applyNightColorScheme(style, userSettings.mapColorScheme)
            }
            installOverlayLayers(style)
            loadedIsDark = isDarkTheme
            loadedStyle = style

            // Hydrate immediately with current state
            updateRouteSources(style, activeRoute, alternativeRoutes, isNavigating)
            updatePoiSources(style, pois, selectedPoi, activeRoute)
            updateWaypointSources(style, activeRoute)
            updateParkedCarSource(style, userSettings.parkedCar)
            updateSignalsSource(style, if (userSettings.showRoadSignals) roadSignals else emptyList(), trafficIncidents)
            updateUserLocationSource(style, currentLocation, azimuthDegrees)
        }
    }

    // Instant GPU restyling when switching between the 4 night color schemes (zero tile reload, zero flicker!)
    LaunchedEffect(loadedStyle, isDarkTheme, userSettings.mapColorScheme) {
        val style = loadedStyle ?: return@LaunchedEffect
        if (isDarkTheme && style.isFullyLoaded) {
            MapLibreStyleHelper.applyNightColorScheme(style, userSettings.mapColorScheme)
        }
    }

    // Update routes
    LaunchedEffect(loadedStyle, activeRoute, alternativeRoutes, isNavigating) {
        val style = loadedStyle ?: return@LaunchedEffect
        if (style.isFullyLoaded) {
            updateRouteSources(style, activeRoute, alternativeRoutes, isNavigating)
            updateWaypointSources(style, activeRoute)
        }
    }

    // Update POIs
    LaunchedEffect(loadedStyle, pois, selectedPoi, activeRoute) {
        val style = loadedStyle ?: return@LaunchedEffect
        if (style.isFullyLoaded) {
            updatePoiSources(style, pois, selectedPoi, activeRoute)
        }
    }

    // Update Parked Car pin
    LaunchedEffect(loadedStyle, userSettings.parkedCar) {
        val style = loadedStyle ?: return@LaunchedEffect
        if (style.isFullyLoaded) {
            updateParkedCarSource(style, userSettings.parkedCar)
        }
    }

    // Update Road Signals & Traffic Incidents
    LaunchedEffect(loadedStyle, roadSignals, trafficIncidents, userSettings.showRoadSignals) {
        val style = loadedStyle ?: return@LaunchedEffect
        if (style.isFullyLoaded) {
            updateSignalsSource(
                style,
                if (userSettings.showRoadSignals) roadSignals else emptyList(),
                trafficIncidents
            )
        }
    }

    // Update User Location & Compass Cone (60 FPS GPU update, zero map invalidation flicker!)
    LaunchedEffect(loadedStyle, currentLocation, azimuthDegrees) {
        val style = loadedStyle ?: return@LaunchedEffect
        if (style.isFullyLoaded) {
            updateUserLocationSource(style, currentLocation, azimuthDegrees)
        }
    }

    // Camera follow tracking in Navigation mode
    LaunchedEffect(mapLibreMap, isNavigating, isOverviewMode, currentLocation) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (isNavigating && activeRoute != null) {
            if (isOverviewMode && activeRoute.geometryPoints.size >= 2) {
                try {
                    val builder = LatLngBounds.Builder()
                    activeRoute.geometryPoints.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120), 600)
                } catch (_: Exception) {}
            } else if (!isOverviewMode && currentLocation != null) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(currentLocation.latitude, currentLocation.longitude),
                        17.2
                    ),
                    600
                )
            }
        }
    }

    // Center on location trigger
    LaunchedEffect(mapLibreMap, centerOnLocationTrigger) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (centerOnLocationTrigger > 0L && currentLocation != null) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(currentLocation.latitude, currentLocation.longitude),
                    16.0
                ),
                700
            )
        }
    }

    // Animate to selected POI
    LaunchedEffect(mapLibreMap, selectedPoi) {
        val map = mapLibreMap ?: return@LaunchedEffect
        selectedPoi?.let { poi ->
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(poi.latitude, poi.longitude), 15.8),
                700
            )
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView }
    )
}

private fun poiKey(poi: PoiEntity): String = "${poi.id}_${poi.latitude}_${poi.longitude}"

private fun installOverlayLayers(style: Style) {
    // Register crisp vector-style marker bitmaps
    style.addImage(IMG_POI_DEFAULT, createPinBitmap(Color.parseColor("#006874"), null))
    style.addImage(IMG_POI_FAV, createPinBitmap(Color.parseColor("#D93025"), null))
    style.addImage(IMG_PARKED_CAR, createPinBitmap(Color.parseColor("#1565C0"), "P"))
    style.addImage(IMG_USER_CONE, createCompassConeBitmap())
    style.addImage(IMG_SIG_TRAFFIC_LIGHT, createTrafficLightBitmap())
    style.addImage(IMG_SIG_STOP, createStopSignBitmap())
    style.addImage(IMG_SIG_GIVE_WAY, createGiveWayBitmap())
    style.addImage(IMG_SIG_ROUNDABOUT, createRoundaboutBitmap())
    style.addImage(IMG_INC_CLOSURE, createIncidentBitmap(Color.parseColor("#D32F2F"), "!"))
    style.addImage(IMG_INC_WARN, createIncidentBitmap(Color.parseColor("#F57C00"), "!"))

    // 1. Alternative Routes
    style.addSource(GeoJsonSource(SRC_ALT_ROUTES))
    style.addLayer(
        LineLayer(LYR_ALT_ROUTES, SRC_ALT_ROUTES).withProperties(
            PropertyFactory.lineColor("#88607D8B"),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )

    // 2. Active Route (casing + inner)
    style.addSource(GeoJsonSource(SRC_ACTIVE_ROUTE))
    style.addLayer(
        LineLayer(LYR_ACTIVE_ROUTE_CASING, SRC_ACTIVE_ROUTE).withProperties(
            PropertyFactory.lineColor("#003D47"),
            PropertyFactory.lineWidth(10f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer(LYR_ACTIVE_ROUTE, SRC_ACTIVE_ROUTE).withProperties(
            PropertyFactory.lineColor("#00ACC1"),
            PropertyFactory.lineWidth(6.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )

    // 3. Road Signals & Traffic Incidents
    style.addSource(GeoJsonSource(SRC_ROAD_SIGNALS))
    style.addLayer(
        SymbolLayer(LYR_ROAD_SIGNALS, SRC_ROAD_SIGNALS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(0.85f)
        ).apply {
            minZoom = 14.0f
        }
    )

    // 4. POIs
    style.addSource(GeoJsonSource(SRC_POIS))
    style.addLayer(
        SymbolLayer(LYR_POIS, SRC_POIS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(0.85f)
        )
    )

    // 5. Waypoints (A, B, C...)
    style.addSource(GeoJsonSource(SRC_WAYPOINTS))
    style.addLayer(
        SymbolLayer(LYR_WAYPOINTS, SRC_WAYPOINTS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(0.85f)
        )
    )

    // 6. Parked Car ("P")
    style.addSource(GeoJsonSource(SRC_PARKED_CAR))
    style.addLayer(
        SymbolLayer(LYR_PARKED_CAR, SRC_PARKED_CAR).withProperties(
            PropertyFactory.iconImage(IMG_PARKED_CAR),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(0.9f)
        )
    )

    // 7. User Location (Cone + Halo + Dot)
    style.addSource(GeoJsonSource(SRC_USER_LOC))
    style.addLayer(
        SymbolLayer(LYR_USER_CONE, SRC_USER_LOC).withProperties(
            PropertyFactory.iconImage(IMG_USER_CONE),
            PropertyFactory.iconRotate(Expression.get("bearing")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(1.0f)
        )
    )
    style.addLayer(
        CircleLayer(LYR_USER_HALO, SRC_USER_LOC).withProperties(
            PropertyFactory.circleRadius(18f),
            PropertyFactory.circleColor("#331A73E8"),
            PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP)
        )
    )
    style.addLayer(
        CircleLayer(LYR_USER_DOT, SRC_USER_LOC).withProperties(
            PropertyFactory.circleRadius(7.5f),
            PropertyFactory.circleColor("#1A73E8"),
            PropertyFactory.circleStrokeWidth(2.8f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP)
        )
    )
}

private fun updateRouteSources(
    style: Style,
    activeRoute: RouteResult?,
    alternativeRoutes: List<RouteResult>,
    isNavigating: Boolean
) {
    val activeSrc = style.getSourceAs<GeoJsonSource>(SRC_ACTIVE_ROUTE)
    val altSrc = style.getSourceAs<GeoJsonSource>(SRC_ALT_ROUTES)

    val activeFeatures = if (activeRoute != null && activeRoute.geometryPoints.size >= 2) {
        val pts = activeRoute.geometryPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        listOf(Feature.fromGeometry(LineString.fromLngLats(pts)))
    } else {
        emptyList()
    }
    activeSrc?.setGeoJson(FeatureCollection.fromFeatures(activeFeatures))

    val altFeatures = if (!isNavigating && alternativeRoutes.size > 1) {
        alternativeRoutes.mapIndexedNotNull { idx, alt ->
            if (alt == activeRoute || alt.geometryPoints.size < 2) null
            else {
                val pts = alt.geometryPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                Feature.fromGeometry(LineString.fromLngLats(pts)).apply {
                    addNumberProperty("alt_index", idx)
                }
            }
        }
    } else {
        emptyList()
    }
    altSrc?.setGeoJson(FeatureCollection.fromFeatures(altFeatures))
}

private fun updatePoiSources(
    style: Style,
    pois: List<PoiEntity>,
    selectedPoi: PoiEntity?,
    activeRoute: RouteResult?
) {
    val src = style.getSourceAs<GeoJsonSource>(SRC_POIS) ?: return
    val displayPois = pois.toMutableList()
    selectedPoi?.let { sel ->
        if (displayPois.none { it.latitude == sel.latitude && it.longitude == sel.longitude }) {
            displayPois.add(sel)
        }
    }
    val features = displayPois.mapNotNull { poi ->
        if (activeRoute != null && activeRoute.waypoints.size >= 2 &&
            activeRoute.waypoints.any { it.latitude == poi.latitude && it.longitude == poi.longitude }
        ) {
            return@mapNotNull null
        }
        Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude)).apply {
            addStringProperty("poi_key", poiKey(poi))
            addStringProperty("icon", if (poi.isFavorite) IMG_POI_FAV else IMG_POI_DEFAULT)
        }
    }
    src.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun updateWaypointSources(style: Style, activeRoute: RouteResult?) {
    val src = style.getSourceAs<GeoJsonSource>(SRC_WAYPOINTS) ?: return
    if (activeRoute == null || activeRoute.waypoints.size < 2) {
        src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val features = activeRoute.waypoints.mapIndexed { idx, pt ->
        val letter = ('A' + idx).toString()
        val imgId = "nagivator-wp-$letter"
        if (style.getImage(imgId) == null) {
            style.addImage(imgId, createPinBitmap(Color.parseColor("#E57373"), letter))
        }
        Feature.fromGeometry(Point.fromLngLat(pt.longitude, pt.latitude)).apply {
            addStringProperty("icon", imgId)
        }
    }
    src.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun updateParkedCarSource(style: Style, parkedCar: ParkedCarLocation?) {
    val src = style.getSourceAs<GeoJsonSource>(SRC_PARKED_CAR) ?: return
    val features = if (parkedCar != null) {
        listOf(Feature.fromGeometry(Point.fromLngLat(parkedCar.longitude, parkedCar.latitude)))
    } else {
        emptyList()
    }
    src.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun updateSignalsSource(
    style: Style,
    signals: List<RoadSignal>,
    incidents: List<TrafficIncident>
) {
    val src = style.getSourceAs<GeoJsonSource>(SRC_ROAD_SIGNALS) ?: return
    val features = mutableListOf<Feature>()
    signals.forEach { sig ->
        val icon = when (sig.type) {
            RoadSignalType.TRAFFIC_SIGNALS -> IMG_SIG_TRAFFIC_LIGHT
            RoadSignalType.STOP -> IMG_SIG_STOP
            RoadSignalType.GIVE_WAY -> IMG_SIG_GIVE_WAY
            RoadSignalType.ROUNDABOUT -> IMG_SIG_ROUNDABOUT
        }
        features.add(
            Feature.fromGeometry(Point.fromLngLat(sig.longitude, sig.latitude)).apply {
                addStringProperty("icon", icon)
            }
        )
    }
    incidents.forEach { inc ->
        val icon = if (inc.type == TrafficIncidentType.CLOSURE) IMG_INC_CLOSURE else IMG_INC_WARN
        features.add(
            Feature.fromGeometry(Point.fromLngLat(inc.longitude, inc.latitude)).apply {
                addStringProperty("icon", icon)
            }
        )
    }
    src.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun updateUserLocationSource(
    style: Style,
    location: LocationPoint?,
    azimuthDegrees: Float?
) {
    val src = style.getSourceAs<GeoJsonSource>(SRC_USER_LOC) ?: return
    if (location == null) {
        src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val feat = Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)).apply {
        addNumberProperty("bearing", (azimuthDegrees ?: location.bearing).toDouble())
    }
    src.setGeoJson(FeatureCollection.fromFeatures(listOf(feat)))
}

private fun createPinBitmap(fillColor: Int, label: String?): Bitmap {
    val size = 84
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f

    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#44000000") }
    canvas.drawCircle(c, c + 3f, 29f, shadow)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    canvas.drawCircle(c, c, 28f, fill)

    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    canvas.drawCircle(c, c, 28f, border)

    if (label != null) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val y = c - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, c, y, text)
    } else {
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(c, c, 10f, inner)
    }
    return bitmap
}

private fun createCompassConeBitmap(): Bitmap {
    val size = 140
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#551A73E8")
        style = Paint.Style.FILL
    }
    val path = Path().apply {
        moveTo(c, c)
        lineTo(c - 28f, 10f)
        quadTo(c, 0f, c + 28f, 10f)
        close()
    }
    canvas.drawPath(path, conePaint)
    return bitmap
}

private fun createTrafficLightBitmap(): Bitmap {
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

private fun createStopSignBitmap(): Bitmap {
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

private fun createGiveWayBitmap(): Bitmap {
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

private fun createRoundaboutBitmap(): Bitmap {
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

private fun createIncidentBitmap(color: Int, label: String): Bitmap {
    return createPinBitmap(color, label)
}
