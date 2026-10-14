package com.example.nagivator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nagivator.data.repository.AppThemeMode
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.ui.favorites.FavoritesScreen
import com.example.nagivator.ui.map.MapScreen
import com.example.nagivator.ui.map.MapViewModel
import com.example.nagivator.ui.profile.ProfileScreen
import com.example.nagivator.ui.theme.NagivatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure osmdroid user agent & cache
        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = "Nagivator/1.0 (Android App; com.example.nagivator)"
        osmConfig.tileFileSystemCacheMaxBytes = 500L * 1024L * 1024L
        osmConfig.tileFileSystemCacheTrimBytes = 400L * 1024L * 1024L

        // Load preferences asynchronously on Dispatchers.IO to prevent main thread disk I/O blocking
        lifecycleScope.launch(Dispatchers.IO) {
            osmConfig.load(
                applicationContext,
                getSharedPreferences("osmdroid_pref", MODE_PRIVATE)
            )
        }

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val userSettingsRepo = remember { UserSettingsRepository.getInstance(context) }
            val userSettings by userSettingsRepo.settings.collectAsState()
            val systemInDark = isSystemInDarkTheme()

            val isDarkTheme = when (userSettings.themeMode) {
                AppThemeMode.SYSTEM -> systemInDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            NagivatorTheme(darkTheme = isDarkTheme) {
                NagivatorApp(isDarkTheme = isDarkTheme)
            }
        }
    }
}

@Composable
fun NagivatorApp(isDarkTheme: Boolean) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.MAP) }
    val mapViewModel: MapViewModel = viewModel()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.MAP -> {
                    MapScreen(
                        viewModel = mapViewModel,
                        isDarkTheme = isDarkTheme,
                        bottomPadding = innerPadding.calculateBottomPadding()
                    )
                }
                AppDestinations.FAVORITES -> {
                    FavoritesScreen(
                        onNavigateToMapWithPoi = { lat, lon ->
                            mapViewModel.selectPoi(
                                mapViewModel.pois.value.find { it.latitude == lat && it.longitude == lon }
                            )
                            currentDestination = AppDestinations.MAP
                        },
                        onNavigateToMapWithRoute = { routeEntity ->
                            mapViewModel.loadSavedRouteEntity(routeEntity)
                            currentDestination = AppDestinations.MAP
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                AppDestinations.PROFILE -> {
                    ProfileScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    MAP("Karte", Icons.Default.Map),
    FAVORITES("Favoriten", Icons.Default.Favorite),
    PROFILE("Profil", Icons.Default.Person),
}
