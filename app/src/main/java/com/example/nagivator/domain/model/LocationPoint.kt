package com.example.nagivator.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val name: String = "",
    val address: String = "",
    val bearing: Float = 0f,
    val speedMps: Float = 0f
)
