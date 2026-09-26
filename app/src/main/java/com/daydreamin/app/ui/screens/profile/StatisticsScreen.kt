package com.daydreamin.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.EmptyState
import com.daydreamin.app.ui.components.GlassCard
import com.daydreamin.app.ui.components.SongRow
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary

@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val prefs = DaydreaminApp.instance.prefs
    val history by prefs.history.collectAsState(initial = emptyList())
    val liked by prefs.likedSongs.collectAsState(initial = emptyList())

    val topArtist = history.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
            Text("Statistics", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(modifier = Modifier.weight(1f)) {
                Column {
                    Text("${history.size}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Songs played", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            GlassCard(modifier = Modifier.weight(1f)) {
                Column {
                    Text("${liked.size}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Liked songs", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (topArtist != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
            Text("Most played: $topArtist", color = TextSecondary, modifier = Modifier.padding(vertical = 16.dp))
        }

        Text("Recently Played", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))

        if (history.isEmpty()) {
            EmptyState("Play something and it'll show up here.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history, key = { it.playId }) { song ->
                    SongRow(song = song, onClick = { PlayerController.playSong(song) })
                }
            }
        }
    }
}
