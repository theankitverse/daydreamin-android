package com.daydreamin.app.ui.screens.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.EmptyState
import com.daydreamin.app.ui.theme.SurfaceVariant
import com.daydreamin.app.ui.theme.TextMuted
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary

@Composable
fun QueueScreen(onBack: () -> Unit) {
    val playerMeta by PlayerController.meta.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close", tint = TextPrimary)
            }
            Text("Queue", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer_(Modifier.size(48.dp))
        }

        playerMeta.currentSong?.let { current ->
            Text("Playing Now", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(url = current.artworkUrl, modifier = Modifier.size(52.dp))
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(current.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(current.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Up Next (${playerMeta.queue.size})", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            if (playerMeta.queue.isNotEmpty()) {
                Text("Clear", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { PlayerController.clearQueue() })
            }
        }

        if (playerMeta.queue.isEmpty()) {
            EmptyState("Nothing queued — Up Next fills in automatically once a song is playing.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playerMeta.queue, key = { it.playId }) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { PlayerController.playQueueItemAt(playerMeta.queue.indexOf(song)) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.DragHandle, contentDescription = null, tint = TextMuted, modifier = Modifier.padding(end = 8.dp))
                        Artwork(url = song.artworkUrl, modifier = Modifier.size(48.dp))
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(song.title, color = TextPrimary, maxLines = 1)
                            Text(song.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        IconButton(onClick = { PlayerController.removeFromQueue(song) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer_(modifier: Modifier) = Box(modifier)
