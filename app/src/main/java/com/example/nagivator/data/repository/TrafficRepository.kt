package com.example.nagivator.data.repository

import com.example.nagivator.car.CarNavigationHelper
import com.example.nagivator.data.remote.AutobahnEventDto
import com.example.nagivator.data.remote.AutobahnTrafficApi
import com.example.nagivator.domain.model.LocationPoint
import com.example.nagivator.domain.model.RouteResult
import com.example.nagivator.domain.model.TrafficIncident
import com.example.nagivator.domain.model.TrafficIncidentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Fetches keyless German Autobahn live traffic data (closures, warnings, roadworks)
 * from the official Autobahn GmbH Open Data API (`verkehr.autobahn.de`).
 */
class TrafficRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val api: AutobahnTrafficApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://verkehr.autobahn.de/o/autobahn/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(MediaType.get("application/json")))
            .build()
            .create(AutobahnTrafficApi::class.java)
    }

    /**
     * Queries only the Autobahns touched by [route] and returns incidents matching the route geometry.
     */
    suspend fun fetchIncidentsOnRoute(route: RouteResult): List<TrafficIncident> =
        withContext(Dispatchers.IO) {
            val roadIds = extractAutobahnIds(route)
            if (roadIds.isEmpty()) return@withContext emptyList()

            val allIncidents = mutableListOf<TrafficIncident>()
            for (roadId in roadIds) {
                runCatching {
                    api.getClosures(roadId).closure.mapNotNull {
                        it.toIncident(roadId, TrafficIncidentType.CLOSURE, defaultBlocked = true)
                    }
                }.onSuccess { allIncidents.addAll(it) }

                runCatching {
                    api.getWarnings(roadId).warning.mapNotNull {
                        it.toIncident(roadId, TrafficIncidentType.WARNING, defaultBlocked = false)
                    }
                }.onSuccess { allIncidents.addAll(it) }

                runCatching {
                    api.getRoadworks(roadId).roadworks.mapNotNull {
                        it.toIncident(roadId, TrafficIncidentType.ROADWORK, defaultBlocked = false)
                    }
                }.onSuccess { allIncidents.addAll(it) }
            }

            filterIncidentsOnRoute(allIncidents, route)
        }

    private fun AutobahnEventDto.toIncident(
        roadId: String,
        type: TrafficIncidentType,
        defaultBlocked: Boolean
    ): TrafficIncident? {
        if (isFutureBool()) return null
        val lat = coordinate?.latDouble() ?: return null
        val lon = coordinate?.lonDouble() ?: return null
        val id = identifier.ifBlank { "${roadId}_${type.name}_${lat}_$lon" }
        return TrafficIncident(
            id = id,
            roadId = roadId,
            type = type,
            title = title.ifBlank { "$roadId Meldung" },
            subtitle = subtitle,
            description = description.joinToString(" ").trim(),
            latitude = lat,
            longitude = lon,
            isBlocked = defaultBlocked || isBlockedBool()
        )
    }

    companion object {
        private val AUTOBAHN_REGEX = Regex("""\bA\s?(\d{1,3})\b""")

        /**
         * Extracts normalized German Autobahn IDs (e.g. "A57", "A3") from a route's step instructions.
         */
        fun extractAutobahnIds(route: RouteResult): List<String> {
            val found = LinkedHashSet<String>()
            for (step in route.steps) {
                AUTOBAHN_REGEX.findAll(step.instruction).forEach { match ->
                    val num = match.groupValues[1]
                    if (num.isNotEmpty()) {
                        found.add("A$num")
                    }
                }
            }
            return found.toList()
        }

        /**
         * Filters incidents to those lying within [maxDistanceMeters] of the active route polyline,
         * ordered by appearance along the route.
         */
        fun filterIncidentsOnRoute(
            incidents: List<TrafficIncident>,
            route: RouteResult,
            maxDistanceMeters: Float = 200f
        ): List<TrafficIncident> {
            if (route.geometryPoints.size < 2 || incidents.isEmpty()) return emptyList()
            return incidents.mapNotNull { incident ->
                val point = LocationPoint(incident.latitude, incident.longitude)
                val (dist, segIdx) = CarNavigationHelper.distanceToPolyline(point, route.geometryPoints)
                if (dist <= maxDistanceMeters) segIdx to incident else null
            }
                .sortedBy { it.first }
                .map { it.second }
                .distinctBy { it.id }
        }
    }
}
