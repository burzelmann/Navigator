package com.example.nagivator.domain.model

enum class SpeedPreference(
    val label: String,
    val description: String,
    val footSpeedMps: Double, // meters per second
    val carTimeFactor: Double // multiplier for driving duration
) {
    SLOW(
        label = "Langsam",
        description = "Flanieren / Gemütlich (~3.3 km/h)",
        footSpeedMps = 0.92,
        carTimeFactor = 1.25
    ),
    NORMAL(
        label = "Normal",
        description = "Standard-Schritt (~4.5 km/h)",
        footSpeedMps = 1.25,
        carTimeFactor = 1.00
    ),
    FAST(
        label = "Schnell",
        description = "Zügiges Gehen (~5.8 km/h)",
        footSpeedMps = 1.62,
        carTimeFactor = 0.85
    )
}
