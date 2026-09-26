package com.daydreamin.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.imageLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import com.daydreamin.app.player.EqPreset
import com.daydreamin.app.ui.theme.TextMuted
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = composeViewModel()
    val context = LocalContext.current

    val darkMode by vm.darkMode.collectAsState()
    val dynamicTheming by vm.dynamicTheming.collectAsState()
    val quality by vm.streamingQuality.collectAsState()
    val crossfade by vm.crossfadeSeconds.collectAsState()
    val normalization by vm.audioNormalization.collectAsState()
    val wifiOnly by vm.downloadWifiOnly.collectAsState()
    val eqPreset by vm.equalizerPreset.collectAsState()
    val extractorState by vm.extractorState.collectAsState()
    val backupStatus by vm.backupStatus.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportLibrary(context.contentResolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importLibrary(context.contentResolver, uri)
    }

    var showAbout by remember { mutableStateOf(false) }
    var cacheStatus by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
                Text("Settings", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }

            SectionTitle("Playback source")
            Text(
                "Daydreamin streams directly from YouTube, resolved on this device — there's no server to configure, and nothing to keep running anywhere else.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (extractorState) {
                        ExtractorState.OK -> Icon(Icons.Filled.CheckCircle, contentDescription = "Working", tint = com.daydreamin.app.ui.theme.Success, modifier = Modifier.padding(end = 8.dp))
                        ExtractorState.FAILED -> Icon(Icons.Filled.Error, contentDescription = "Broken", tint = com.daydreamin.app.ui.theme.DangerRed, modifier = Modifier.padding(end = 8.dp))
                        else -> {}
                    }
                    Text(
                        when (extractorState) {
                            ExtractorState.OK -> "Extractor working"
                            ExtractorState.FAILED -> "Extractor broken — YouTube may have changed something"
                            ExtractorState.CHECKING -> "Checking..."
                            ExtractorState.UNKNOWN -> "Not tested yet"
                        },
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { vm.testExtractor() }) { Text("Test") }
            }

            SectionTitle("Appearance")
            SettingSwitchRow("Dark Mode", darkMode, vm::setDarkMode)
            SettingSwitchRow("Dynamic Theming", dynamicTheming, vm::setDynamicTheming)

            SectionTitle("Playback")
            Text("Streaming Quality: $quality", color = TextPrimary, modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Auto", "High", "Data Saver").forEach { q ->
                    TextButton(onClick = { vm.setStreamingQuality(q) }) {
                        Text(q, color = if (q == quality) MaterialTheme.colorScheme.primary else TextSecondary)
                    }
                }
            }
            Text("Fade out near track end: ${crossfade}s", color = TextPrimary, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = crossfade.toFloat(),
                onValueChange = { vm.setCrossfadeSeconds(it.toInt()) },
                valueRange = 0f..8f,
                steps = 7,
            )
            SettingSwitchRow("Loudness Boost", normalization, vm::setAudioNormalization)
            Text(
                "A real +6dB dynamics-aware boost, not fake — YouTube doesn't give us per-track loudness data, so this isn't true track-to-track normalization.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Equalizer: $eqPreset", color = TextPrimary, modifier = Modifier.padding(top = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EqPreset.entries.forEach { preset ->
                    TextButton(onClick = { vm.setEqualizerPreset(preset.label) }) {
                        Text(preset.label, color = if (preset.label == eqPreset) MaterialTheme.colorScheme.primary else TextSecondary)
                    }
                }
            }

            SectionTitle("Downloads")
            SettingSwitchRow("Cache over Wi-Fi only", wifiOnly, vm::setDownloadWifiOnly)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Clear image cache", color = TextPrimary)
                TextButton(onClick = {
                    context.imageLoader.diskCache?.clear()
                    context.imageLoader.memoryCache?.clear()
                    cacheStatus = "Cleared"
                }) { Text(cacheStatus.ifBlank { "Clear" }) }
            }

            SectionTitle("Backup")
            Text(
                "Save your liked songs, playlists and history to a file, or restore them from one. Importing only adds — it never removes anything already here.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    exportLauncher.launch("daydreamin-library-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json")
                }) { Text("Export library") }
                TextButton(onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text("Import library") }
            }
            backupStatus?.let { Text(it, color = TextMuted, style = MaterialTheme.typography.bodySmall) }

            SectionTitle("Other")
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).let { it },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("About", color = TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAbout = true }) { Text("View") }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
            title = { Text("Daydreamin") },
            text = { Text("More Than Music.\nStreams directly from YouTube, resolved on-device — no server required.") },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}
