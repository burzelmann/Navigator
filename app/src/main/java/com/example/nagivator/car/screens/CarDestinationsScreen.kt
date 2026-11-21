package com.example.nagivator.car.screens

import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.nagivator.car.surface.CarMapSurfaceRenderer
import com.example.nagivator.data.local.NagivatorDatabase
import com.example.nagivator.data.local.entity.TripHistoryEntity
import com.example.nagivator.data.repository.RoutingRepository
import com.example.nagivator.data.repository.TripHistoryRepository
import com.example.nagivator.domain.NavigationStateHolder
import com.example.nagivator.domain.PredictedDestination
import com.example.nagivator.domain.RoutePredictionUseCase
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android Auto "Ziele" quick-access screen showing:
 * 1. On-device predicted destinations based on recurring routes & time-of-day
 * 2. Recent trips with a 1-tap toggle between "Hinweg" (repeat) and "Rückweg" (reverse route)
 */
class CarDestinationsScreen(
    carContext: CarContext,
    private val surfaceRenderer: CarMapSurfaceRenderer
) : Screen(carContext), DefaultLifecycleObserver {

    private val db = NagivatorDatabase.getDatabase(carContext)
    private val tripHistoryRepository = TripHistoryRepository(db.tripHistoryDao())
    private val routingRepository = RoutingRepository()

    private var predictedDestinations: List<PredictedDestination> = emptyList()
    private var recentTrips: List<TripHistoryEntity> = emptyList()
    private var reverseMode = false
    private var isLoading = true
    private var isCalculating = false

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        loadHistoryAndPredictions()
    }

    private fun loadHistoryAndPredictions() {
        lifecycleScope.launch {
            isLoading = true
            invalidate()

            val history = tripHistoryRepository.recentTripsOnce(100)
            recentTrips = history.take(6)
            predictedDestinations = RoutePredictionUseCase.predict(
                history = history,
                currentLocation = NavigationStateHolder.currentLocation.value,
                maxResults = 3
            )

            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        if (isLoading || isCalculating) {
            return ListTemplate.Builder()
                .setTitle(if (isCalculating) "Route wird berechnet..." else "Ziele werden geladen...")
                .setHeaderAction(Action.BACK)
                .setLoading(true)
                .build()
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(if (reverseMode) "Rückweg: An" else "Rückweg: Aus")
                    .setOnClickListener {
                        reverseMode = !reverseMode
                        invalidate()
                    }
                    .build()
            )
            .build()

        val templateBuilder = ListTemplate.Builder()
            .setTitle(if (reverseMode) "Ziele (Rückweg-Modus)" else "Ziele & Verlauf")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)

        if (predictedDestinations.isEmpty() && recentTrips.isEmpty()) {
            val emptyList = ItemList.Builder()
                .setNoItemsMessage("Noch keine Fahrten gespeichert. Gestartete Routen erscheinen automatisch hier.")
                .build()
            return templateBuilder.setSingleList(emptyList).build()
        }

        val userLoc = NavigationStateHolder.currentLocation.value
        val distResults = FloatArray(1)

        // Cap total rows to 6 for Android Auto driving list limits.
        // In Rückweg-Modus, show only "Rückweg starten" right at the top for 1-tap access without scrolling.
        val predictionsToShow = if (reverseMode) emptyList() else predictedDestinations.take(3)
        val historyBudget = (6 - predictionsToShow.size).coerceAtLeast(1)
        val historyToShow = recentTrips.take(historyBudget)

        if (predictionsToShow.isNotEmpty()) {
            val predListBuilder = ItemList.Builder()
            predictionsToShow.forEach { pred ->
                var subtitle = pred.reason
                if (userLoc != null) {
                    Location.distanceBetween(
                        userLoc.latitude, userLoc.longitude,
                        pred.latitude, pred.longitude,
                        distResults
                    )
                    subtitle = "${"%.1f km".format(distResults[0] / 1000.0)} • $subtitle"
                }

                predListBuilder.addItem(
                    Row.Builder()
                        .setTitle(pred.name)
                        .addText(subtitle)
                        .setOnClickListener {
                            if (!isCalculating) {
                                startRouteTo(
                                    LocationPoint(
                                        latitude = pred.latitude,
                                        longitude = pred.longitude,
                                        name = pred.name
                                    )
                                )
                            }
                        }
                        .build()
                )
            }
            templateBuilder.addSectionedList(
                SectionedItemList.create(predListBuilder.build(), "Vorschläge für jetzt")
            )
        }

        if (historyToShow.isNotEmpty()) {
            val timeFormat = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
            val historyListBuilder = ItemList.Builder()
            historyToShow.forEach { trip ->
                val targetName = if (reverseMode) trip.startName else trip.destinationName
                val targetLat = if (reverseMode) trip.startLatitude else trip.destinationLatitude
                val targetLon = if (reverseMode) trip.startLongitude else trip.destinationLongitude
                val timeLabel = timeFormat.format(Date(trip.timestampMs))

                val title = if (reverseMode) "Rückweg: $targetName" else targetName
                val subtitle = if (reverseMode) {
                    "Von ${trip.destinationName} • $timeLabel"
                } else {
                    "Nochmal ab ${trip.startName} • $timeLabel"
                }

                historyListBuilder.addItem(
                    Row.Builder()
                        .setTitle(title)
                        .addText(subtitle)
                        .setOnClickListener {
                            if (!isCalculating) {
                                startRouteTo(
                                    LocationPoint(
                                        latitude = targetLat,
                                        longitude = targetLon,
                                        name = targetName
                                    )
                                )
                            }
                        }
                        .build()
                )
            }
            templateBuilder.addSectionedList(
                SectionedItemList.create(
                    historyListBuilder.build(),
                    if (reverseMode) "Rückweg starten" else "Letzte Fahrten"
                )
            )
        }

        return templateBuilder.build()
    }

    private fun startRouteTo(destination: LocationPoint) {
        val startLoc = NavigationStateHolder.currentLocation.value
        if (startLoc == null) {
            CarToast.makeText(
                carContext,
                "Warte auf GPS-Position...",
                CarToast.LENGTH_SHORT
            ).show()
            return
        }

        isCalculating = true
        invalidate()

        lifecycleScope.launch {
            val result = routingRepository.getRoute(
                waypoints = listOf(startLoc, destination),
                mode = TravelMode.CAR
            )

            result.onSuccess { route ->
                isCalculating = false
                tripHistoryRepository.recordTrip(startLoc, destination, TravelMode.CAR)
                NavigationStateHolder.startNavigation(route)
                surfaceRenderer.updateRoute(route)
            }.onFailure { error ->
                isCalculating = false
                invalidate()
                CarToast.makeText(
                    carContext,
                    error.localizedMessage ?: "Routenberechnung fehlgeschlagen",
                    CarToast.LENGTH_LONG
                ).show()
            }
        }
    }
}
