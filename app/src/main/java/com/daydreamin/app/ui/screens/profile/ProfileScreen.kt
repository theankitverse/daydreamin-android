package com.daydreamin.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.ui.components.GlassCard
import com.daydreamin.app.ui.theme.BrandPurple
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.combine

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    onStatistics: () -> Unit,
    onSettings: () -> Unit,
    onThemeCustomize: () -> Unit,
) {
    val prefs = DaydreaminApp.instance.prefs
    val liked by prefs.likedSongs.collectAsState(initial = emptyList())
    val history by prefs.history.collectAsState(initial = emptyList())
    val playlists by prefs.playlists.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(BrandPurple, MaterialTheme.colorScheme.secondary))),
            )
            Column(Modifier.padding(start = 16.dp)) {
                Text("Ankit", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Music for a calmer you", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(label = "Liked", value = liked.size.toString(), modifier = Modifier.weight(1f))
            StatTile(label = "Playlists", value = playlists.size.toString(), modifier = Modifier.weight(1f))
            StatTile(label = "Played", value = history.size.toString(), modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))

        ProfileRow(icon = Icons.Filled.BarChart, label = "Statistics", onClick = onStatistics)
        ProfileRow(icon = Icons.Filled.Palette, label = "Theme / Customize", onClick = onThemeCustomize)
        ProfileRow(icon = Icons.Filled.Settings, label = "Settings", onClick = onSettings)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun ProfileRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary)
        Text(label, color = TextPrimary, modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}
