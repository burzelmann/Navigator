package com.example.nagivator.data.repository

import android.content.Context
import android.util.Log
import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.data.local.NagivatorDatabase
import com.example.nagivator.data.local.dao.LearnedRouteDao
import com.example.nagivator.data.local.entity.LearnedRouteEntity
import com.example.nagivator.domain.TripDeviationOutcome
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.TravelMode
import kotlinx.coroutines.flow.Flow

/**
 * Manages locally learned CAR route preferences (on-device Room DB, opt-in only).
 * A learned corridor becomes active for routing only after [CONFIRMATION_THRESHOLD] (3)
 * repeated intentional deviations on the exact same origin -> destination pair.
 */
class LearnedRouteRepository(private val dao: LearnedRouteDao) {

    val allLearnedRoutes: Flow<List<LearnedRouteEntity>> = dao.observeAll()

    /**
     * Finds the strongest active learned CAR preference (confirmationCount >= 3) matching
     * [origin] -> [destination] within [ENDPOINT_MATCH_RADIUS_M] (directional).
     */
    suspend fun findActivePreference(
        origin: LocationPoint,
        destination: LocationPoint,
        mode: TravelMode
    ): LearnedRouteEntity? {
        if (mode != TravelMode.CAR) return null
        val all = dao.getByTravelMode(TravelMode.CAR.name)
        return matchActivePreference(all, origin, destination)
    }

    /**
     * Persists a confirmed intentional deviation at trip arrival, incrementing an existing
     * corridor candidate or creating a new one with confirmationCount = 1.
     */
    suspend fun recordTripOutcome(outcome: TripDeviationOutcome) {
        if (outcome.demoteLearnedRouteId != null) {
            demotePreference(outcome.demoteLearnedRouteId)
        }
        val anchor1 = outcome.anchor1 ?: return
        val existing = dao.getByTravelMode(TravelMode.CAR.name)
        val now = System.currentTimeMillis()

        val match = existing.firstOrNull { candidate ->
            isSameOriginDest(candidate, outcome.origin, outcome.destination) &&
                CarNavigationHelper.haversineMeters(
                    candidate.anchorLatitude, candidate.anchorLongitude,
                    anchor1.latitude, anchor1.longitude
                ) <= ANCHOR_MATCH_RADIUS_M
        }

        if (match != null) {
            val newCount = (match.confirmationCount + 1).coerceAtMost(20)
            // Sliding weighted average keeps anchor centered in the user's preferred corridor
            val weight = 0.35
            val updated = match.copy(
                anchorLatitude = match.anchorLatitude * (1.0 - weight) + anchor1.latitude * weight,
                anchorLongitude = match.anchorLongitude * (1.0 - weight) + anchor1.longitude * weight,
                anchor2Latitude = outcome.anchor2?.latitude ?: match.anchor2Latitude,
                anchor2Longitude = outcome.anchor2?.longitude ?: match.anchor2Longitude,
                confirmationCount = newCount,
                lastUsedMs = now
            )
            dao.update(updated)
            Log.i(
                TAG,
                "Updated learned route '${updated.startName} -> ${updated.destinationName}' " +
                    "(confirmations=$newCount/$CONFIRMATION_THRESHOLD)"
            )
        } else {
            val entity = LearnedRouteEntity(
                startName = outcome.origin.name.ifBlank { "Start" },
                startLatitude = outcome.origin.latitude,
                startLongitude = outcome.origin.longitude,
                destinationName = outcome.destination.name.ifBlank { "Ziel" },
                destinationLatitude = outcome.destination.latitude,
                destinationLongitude = outcome.destination.longitude,
                travelMode = TravelMode.CAR.name,
                anchorLatitude = anchor1.latitude,
                anchorLongitude = anchor1.longitude,
                anchor2Latitude = outcome.anchor2?.latitude,
                anchor2Longitude = outcome.anchor2?.longitude,
                confirmationCount = 1,
                lastUsedMs = now
            )
            dao.insert(entity)
            Log.i(
                TAG,
                "Recorded new learned route candidate '${entity.startName} -> ${entity.destinationName}' (1/$CONFIRMATION_THRESHOLD)"
            )
        }
    }

    suspend fun demotePreference(id: Long) {
        val existing = dao.getById(id) ?: return
        val nextCount = existing.confirmationCount - 1
        if (nextCount <= 0) {
            dao.deleteById(id)
        } else {
            dao.update(existing.copy(confirmationCount = nextCount))
        }
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    companion object {
        private const val TAG = "LearnedRouteRepo"
        const val CONFIRMATION_THRESHOLD = 3
        const val ENDPOINT_MATCH_RADIUS_M = 500.0
        const val ANCHOR_MATCH_RADIUS_M = 600.0

        fun getInstance(context: Context): LearnedRouteRepository {
            val db = NagivatorDatabase.getDatabase(context.applicationContext)
            return LearnedRouteRepository(db.learnedRouteDao())
        }

        fun isSameOriginDest(
            entity: LearnedRouteEntity,
            origin: LocationPoint,
            destination: LocationPoint
        ): Boolean {
            val startDist = CarNavigationHelper.haversineMeters(
                entity.startLatitude, entity.startLongitude,
                origin.latitude, origin.longitude
            )
            val destDist = CarNavigationHelper.haversineMeters(
                entity.destinationLatitude, entity.destinationLongitude,
                destination.latitude, destination.longitude
            )
            return startDist <= ENDPOINT_MATCH_RADIUS_M && destDist <= ENDPOINT_MATCH_RADIUS_M
        }

        fun matchActivePreference(
            candidates: List<LearnedRouteEntity>,
            origin: LocationPoint,
            destination: LocationPoint
        ): LearnedRouteEntity? {
            return candidates
                .filter { entity ->
                    entity.travelMode == TravelMode.CAR.name &&
                        entity.confirmationCount >= CONFIRMATION_THRESHOLD &&
                        isSameOriginDest(entity, origin, destination)
                }
                .maxWithOrNull(
                    compareBy<LearnedRouteEntity>({ it.confirmationCount }, { it.lastUsedMs })
                )
        }
    }
}
