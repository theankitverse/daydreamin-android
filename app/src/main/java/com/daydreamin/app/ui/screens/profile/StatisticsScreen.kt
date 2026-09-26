package com.daydreamin.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.AmbientGlow
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.SongListRow
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.Space
import com.daydreamin.app.ui.theme.glass
import com.daydreamin.app.ui.theme.rememberArtworkLight

private val Muted = Color.White.copy(alpha = 0.55f)

/**
 * Your listening in numbers — only numbers the app actually knows. History keeps each song once
 * (the most recent 200), so this talks about songs and artists in your recent listening, never
 * "play counts" it can't measure.
 */
@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val prefs = DaydreaminApp.instance.prefs
    val history by prefs.history.collectAsState(initial = emptyList())
    val liked by prefs.likedSongs.collectAsState(initial = emptyList())
    val playlists by prefs.playlists.collectAsState(initial = emptyList())
    val meta = PlayerController.meta.collectAsState()
    val nowId by remember { derivedStateOf { meta.value.currentSong?.playId } }
    val isPlaying by remember { derivedStateOf { meta.value.isPlaying } }

    val artists = remember(history) {
        history.filter { it.artist.isNotBlank() && !it.artist.equals("Unknown", true) }
            .groupBy { it.artist.trim().lowercase() }.values
            .sortedByDescending { it.size }
            .take(5)
    }
    val likedMinutes = remember(liked) { liked.sumOf { it.duration.coerceAtLeast(0) } / 60 }
    val lead = artists.firstOrNull()?.firstOrNull() ?: history.firstOrNull()
    val light = rememberArtworkLight(lead?.cover?.ifBlank { lead.artworkUrl }, ArtworkLight(BrandViolet, Color(0xFF3D3A9E)))

    Box(Modifier.fillMaxSize().background(BgBase)) {
        AmbientGlow(light = light, scrollY = { 0f })
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item {
                Box(
                    Modifier.padding(start = 12.dp, top = 8.dp).size(40.dp).pressable(onClick = onBack).glass(Radius.pill, Glass.Clear),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(26.dp)) }
                Text("Statistics", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.padding(start = Space.gutter, top = 16.dp, bottom = Space.l))
            }
            item {
                Column(Modifier.padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(history.size.toString(), "songs in your\nrecent listening", Modifier.weight(1f))
                        StatTile(history.map { it.artist.trim().lowercase() }.distinct().size.toString(), "different artists\nin that time", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(liked.size.toString(), if (likedMinutes > 0) "liked songs ·\n$likedMinutes min of music" else "liked\nsongs", Modifier.weight(1f))
                        StatTile(playlists.size.toString(), "${if (playlists.size == 1) "playlist" else "playlists"} ·\n${playlists.sumOf { it.songs.size }} songs", Modifier.weight(1f))
                    }
                }
            }
            if (artists.isNotEmpty()) {
                item { Section("Top artists lately") }
                itemsIndexed(artists, key = { _, g -> "a-" + g.first().artist.lowercase() }) { i, group ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .pressable(onClick = { PlayerController.playFromList(group, 0) })
                            .padding(horizontal = Space.gutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = Muted, modifier = Modifier.width(24.dp))
                        Artwork(url = group.first().artworkUrl, shape = CircleShape, modifier = Modifier.size(50.dp))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(group.first().artist.trim(), style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${group.size} ${if (group.size == 1) "song" else "songs"} · tap to play", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                    }
                }
            }
            if (history.isNotEmpty()) {
                item { Section("Recently played") }
                itemsIndexed(history.take(30), key = { _, s -> "h-" + s.playId }) { _, s ->
                    SongListRow(
                        s,
                        isCurrent = s.playId == nowId,
                        isPlaying = isPlaying,
                        onClick = { if (s.playId != nowId) PlayerController.playSong(s) },
                    )
                }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).glass(CircleShape, Glass.Regular), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Insights, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Your numbers start with your first song", style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
                        Text("Play anything and come back.", style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(start = Space.gutter, top = Space.section, bottom = Space.xs))
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    Column(modifier.glass(Radius.panelShape, Glass.Clear).padding(16.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, fontFeatureSettings = "tnum"), color = Color.White)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted, minLines = 2)
    }
}
