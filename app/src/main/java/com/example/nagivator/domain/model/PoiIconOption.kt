package com.example.nagivator.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Park
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

data class PoiIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

object PoiIconRegistry {
    val options = listOf(
        PoiIconOption("place", "Standard Pin", Icons.Default.Place),
        PoiIconOption("cafe", "Café / Bar", Icons.Default.LocalCafe),
        PoiIconOption("restaurant", "Restaurant", Icons.Default.Restaurant),
        PoiIconOption("hotel", "Hotel / Unterkunft", Icons.Default.Hotel),
        PoiIconOption("sight", "Kultur / Sight", Icons.Default.Museum),
        PoiIconOption("shopping", "Einkaufen", Icons.Default.ShoppingCart),
        PoiIconOption("nature", "Natur / Park", Icons.Default.Park),
        PoiIconOption("gas", "Tankstelle / EV", Icons.Default.LocalGasStation),
        PoiIconOption("beach", "Strand & Wasser", Icons.Default.BeachAccess),
        PoiIconOption("transit", "Transport / ÖPNV", Icons.Default.DirectionsBus)
    )

    fun getIcon(key: String): ImageVector {
        return options.find { it.key == key }?.icon ?: Icons.Default.Place
    }
}
