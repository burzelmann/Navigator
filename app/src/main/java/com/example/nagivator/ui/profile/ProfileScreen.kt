package com.example.nagivator.ui.profile

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nagivator.car.CarVoiceGuidance
import com.example.nagivator.car.surface.MapPerspective
import com.example.nagivator.data.repository.AppThemeMode
import com.example.nagivator.data.repository.MapColorScheme
import com.example.nagivator.data.repository.VoiceSpeechRate
import com.example.nagivator.domain.model.SpeedPreference

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val voiceGuidance = remember { CarVoiceGuidance.getInstance(context) }
    val availableVoices by voiceGuidance.availableVoices.collectAsState()
    var reloadOnResume by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (reloadOnResume) {
                    reloadOnResume = false
                    voiceGuidance.reloadEngine()
                } else {
                    voiceGuidance.refreshAvailableVoices()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = "Profil & Einstellungen",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Persönliche Routen- & Theme-Präferenzen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Appearance / Theme Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Erscheinungsbild (Theme)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (settings.themeMode) {
                        AppThemeMode.SYSTEM -> "Folgt den Android-Systemeinstellungen"
                        AppThemeMode.LIGHT -> "Karte & App immer im hellen Design"
                        AppThemeMode.DARK -> "Karte & App immer im dunklen Graphit-Design"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AppThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = AppThemeMode.entries.size)
                        ) {
                            Text(mode.label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Nacht-Farbschema (Karte)",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = settings.mapColorScheme.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MapColorScheme.entries.forEach { scheme ->
                        FilterChip(
                            selected = settings.mapColorScheme == scheme,
                            onClick = { viewModel.setMapColorScheme(scheme) },
                            label = { Text(scheme.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Dynamischer Auto-Zoom",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Zoomt bei höherem Tempo automatisch heraus",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.dynamicZoomEnabled,
                        onCheckedChange = { viewModel.setDynamicZoomEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Ampeln & Kreuzungen hervorheben",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Zeigt Ampeln, Stopp-, Vorfahrt- und Kreisverkehr-Symbole",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.showRoadSignals,
                        onCheckedChange = { viewModel.setShowRoadSignals(it) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Sprachausgabe (Turn-by-Turn)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (settings.voiceMuted) "Stummgeschaltet (Synchron mit Android Auto)" else "Aktiv (Synchron mit Android Auto)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = !settings.voiceMuted,
                        onCheckedChange = { enabled -> viewModel.setVoiceMuted(!enabled) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { voiceGuidance.speakTestPrompt() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Testansage abspielen (Lautstärke testen)")
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Stimme (Offline-TTS)",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (availableVoices.isEmpty()) {
                        "System-Standardstimme aktiv. Über Sprachpakete lassen sich lokale HD-Stimmen nachladen."
                    } else {
                        "${availableVoices.size} lokale Offline-Stimme(n) erkannt • Tippen zum Probehören"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = settings.voiceName == null,
                        onClick = {
                            viewModel.setVoiceName(null)
                            voiceGuidance.speakVoicePreview(null, settings.voiceSpeechRate)
                        },
                        label = { Text("Auto (Beste HD)") }
                    )
                    availableVoices.forEach { voice ->
                        FilterChip(
                            selected = settings.voiceName == voice.name,
                            onClick = {
                                viewModel.setVoiceName(voice.name)
                                voiceGuidance.speakVoicePreview(voice.name, settings.voiceSpeechRate)
                            },
                            label = { Text(voice.shortLabel) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Sprechtempo",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    VoiceSpeechRate.entries.forEachIndexed { index, rate ->
                        SegmentedButton(
                            selected = settings.voiceSpeechRate == rate,
                            onClick = {
                                viewModel.setVoiceSpeechRate(rate)
                                voiceGuidance.speakVoicePreview(settings.voiceName, rate)
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = VoiceSpeechRate.entries.size
                            )
                        ) {
                            Text(rate.label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        reloadOnResume = true
                        val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                        val settingsIntent = Intent("com.android.settings.TTS_SETTINGS")
                        try {
                            context.startActivity(installIntent)
                        } catch (_: Exception) {
                            try {
                                context.startActivity(settingsIntent)
                            } catch (_: Exception) {
                                reloadOnResume = false
                                Toast.makeText(
                                    context,
                                    "Bitte Android-Einstellungen → Sprachausgabe öffnen",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Offline-Sprachpakete laden / verwalten")
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Kartenausrichtung (Android Auto)",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Wird für künftige Fahrten gespeichert",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = settings.carMapPerspective == MapPerspective.NORTH_UP,
                        onClick = { viewModel.setCarMapPerspective(MapPerspective.NORTH_UP) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Norden oben")
                    }
                    SegmentedButton(
                        selected = settings.carMapPerspective == MapPerspective.HEADING_UP,
                        onClick = { viewModel.setCarMapPerspective(MapPerspective.HEADING_UP) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Fahrtrichtung")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Foot Speed Preferences Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Gehgeschwindigkeit (Fußgänger)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = settings.footSpeed.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SpeedPreference.entries.forEachIndexed { index, pref ->
                        SegmentedButton(
                            selected = settings.footSpeed == pref,
                            onClick = { viewModel.setFootSpeed(pref) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = SpeedPreference.entries.size)
                        ) {
                            Text(pref.label)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Car Speed Preferences Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Fahrgeschwindigkeit (Auto)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (settings.carSpeed) {
                        SpeedPreference.SLOW -> "Gemütlich / Vorsichtig (+25% Zeit)"
                        SpeedPreference.NORMAL -> "Standard-Tempo (Verkehrsfluss)"
                        SpeedPreference.FAST -> "Zügig / Freie Fahrt (-15% Zeit)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SpeedPreference.entries.forEachIndexed { index, pref ->
                        SegmentedButton(
                            selected = settings.carSpeed == pref,
                            onClick = { viewModel.setCarSpeed(pref) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = SpeedPreference.entries.size)
                        ) {
                            Text(pref.label)
                        }
                    }
                }
            }
        }
    }
}
