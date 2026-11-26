package com.example.nagivator.car.screens

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.DurationSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.RoutePreviewNavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.nagivator.car.surface.CarMapSurfaceRenderer
import com.example.nagivator.data.local.NagivatorDatabase
import com.example.nagivator.data.repository.TripHistoryRepository
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Android Auto RoutePreviewNavigationTemplate allowing drivers to compare up to 3
 * route alternatives on the live map surface before starting navigation.
 */
class CarRoutePreviewScreen(
    carContext: CarContext,
    private val surfaceRenderer: CarMapSurfaceRenderer,
    private val routes: List<RouteResult>,
    private val startPoint: LocationPoint,
    private val destination: LocationPoint
) : Screen(carContext), DefaultLifecycleObserver {

    private val db = NagivatorDatabase.getDatabase(carContext)
    private val tripHistoryRepository = TripHistoryRepository(db.tripHistoryDao())
    private var selectedIndex: Int = 0

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        routes.firstOrNull()?.let { surfaceRenderer.updateRoute(it) }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        if (!NavigationStateHolder.isNavigating.value) {
            surfaceRenderer.updateRoute(null)
        }
    }

    override fun onGetTemplate(): Template {
        val safeRoutes = routes.take(3)
        val fastestMinutes = safeRoutes.minOfOrNull { (it.totalDurationSeconds / 60.0).roundToInt().coerceAtLeast(1) } ?: 1

        val listBuilder = ItemList.Builder()
            .setSelectedIndex(selectedIndex.coerceIn(0, (safeRoutes.size - 1).coerceAtLeast(0)))
            .setOnSelectedListener { index ->
                if (index in safeRoutes.indices) {
                    selectedIndex = index
                    surfaceRenderer.updateRoute(safeRoutes[index])
                    invalidate()
                }
            }

        safeRoutes.forEachIndexed { idx, route ->
            val durationSec = route.totalDurationSeconds.roundToInt().coerceAtLeast(60)
            val titleSpan = SpannableString(" ").apply {
                setSpan(
                    DurationSpan.create(durationSec.toLong()),
                    0,
                    1,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )
            }

            val routeMinutes = (route.totalDurationSeconds / 60.0).roundToInt().coerceAtLeast(1)
            val deltaMin = routeMinutes - fastestMinutes
            val badge = when {
                route.isLearnedPreference -> if (deltaMin > 0) "⭐ Bevorzugte Route (+$deltaMin Min.)" else "⭐ Bevorzugte Route"
                deltaMin == 0 -> "Schnellste Route"
                deltaMin > 0 -> "+$deltaMin Min. • Alternative ${idx + 1}"
                else -> "Alternative ${idx + 1}"
            }

            val row = Row.Builder()
                .setTitle(titleSpan)
                .addText("${route.formattedDistance} • $badge")
                .build()

            listBuilder.addItem(row)
        }

        val navigateAction = Action.Builder()
            .setTitle("Starten")
            .setOnClickListener {
                val chosen = safeRoutes.getOrNull(selectedIndex) ?: return@setOnClickListener
                lifecycleScope.launch {
                    tripHistoryRepository.recordTrip(startPoint, destination, TravelMode.CAR)
                }
                NavigationStateHolder.startNavigation(chosen)
                surfaceRenderer.updateRoute(chosen)
            }
            .build()

        val destTitle = destination.name.ifBlank { "Routenauswahl" }

        return RoutePreviewNavigationTemplate.Builder()
            .setTitle(destTitle)
            .setHeaderAction(Action.BACK)
            .setItemList(listBuilder.build())
            .setNavigateAction(navigateAction)
            .build()
    }
}
