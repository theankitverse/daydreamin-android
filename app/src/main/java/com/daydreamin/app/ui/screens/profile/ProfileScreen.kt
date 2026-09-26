package com.daydreamin.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.AmbientGlow
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.EqualizerBars
import com.daydreamin.app.ui.components.SolidPillButton
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.screens.library.LibraryTab
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.BrandPink
import com.daydreamin.app.ui.theme.BrandPurple
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.Space
import com.daydreamin.app.ui.theme.glass
import com.daydreamin.app.ui.theme.rememberArtworkLight

private const val DISPLAY_NAME = "Ankit"
private val DefaultLight = ArtworkLight(key = BrandViolet, fill = Color(0xFF3D3A9E))
private val Muted = Color.White.copy(alpha = 0.55f)

/** The artist behind the most songs in recent history — "most played" in the only sense the data supports (history keeps each song once). */
private data class TopArtist(val name: String, val songs: List<Song>)

private fun topArtist(history: List<Song>): TopArtist? =
    history
        .filter { it.artist.isNotBlank() && !it.artist.equals("Unknown", ignoreCase = true) }
        .groupBy { it.artist.trim().lowercase() }
        .values
        .maxByOrNull { it.size }
        ?.takeIf { it.size >= 2 }
        ?.let { TopArtist(it.first().artist.trim(), it) }

/**
 * You, as a listener: your collection at a glance, the artist you've been playing most lately,
 * what you played last — lit by that artist's artwork — and the ways into Statistics, Theme and
 * Settings. Everything here comes from what the app keeps on this device.
 */
@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    onStatistics: () -> Unit,
    onSettings: () -> Unit,
    onThemeCustomize: () -> Unit,
    onOpenLibrary: (LibraryTab) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val prefs = DaydreaminApp.instance.prefs
    val liked by prefs.likedSongs.collectAsState(initial = emptyList())
    val history by prefs.history.collectAsState(initial = emptyList())
    val playlists by prefs.playlists.collectAsState(initial = emptyList())
    val meta = PlayerController.meta.collectAsState()
    val nowPlayingId by remember { derivedStateOf { meta.value.currentSong?.playId } }
    val nowIsPlaying by remember { derivedStateOf { meta.value.isPlaying } }

    val top = remember(history) { topArtist(history) }
    val lead = top?.songs?.firstOrNull() ?: history.firstOrNull() ?: liked.firstOrNull()
    val light = rememberArtworkLight(lead?.cover?.ifBlank { lead.artworkUrl }, DefaultLight)

    val listState = rememberLazyListState()
    val scrollY = { if (listState.firstVisibleItemIndex > 0) 2_000f else listState.firstVisibleItemScrollOffset.toFloat() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val play = { songs: List<Song>, index: Int ->
        val song = songs[index]
        if (song.playId == nowPlayingId) onOpenPlayer() else PlayerController.playFromList(songs, index)
    }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        AmbientGlow(light = light, scrollY = scrollY)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = statusTop + Space.l, bottom = contentPadding.calculateBottomPadding() + Space.l),
        ) {
            item { Header() }
            item {
                StatStrip(
                    liked = liked.size,
                    playlists = playlists.size,
                    played = history.size,
                    onOpen = onOpenLibrary,
                    modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.l),
                )
            }
            if (top != null) {
                item {
                    TopArtistCard(
                        top = top,
                        light = light,
                        onPlay = { play(top.songs, 0) },
                        modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.section),
                    )
                }
            }
            if (history.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.m, top = Space.section, bottom = Space.titleToContent),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text("Recently played", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                        Text(
                            "See all",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.pressable(onClick = { onOpenLibrary(LibraryTab.RECENT) }).padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                    val recent = history.take(12)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Space.s + 2.dp),
                    ) {
                        itemsIndexed(recent, key = { _, s -> s.playId }) { i, s ->
                            RecentCard(
                                song = s,
                                isCurrent = s.playId == nowPlayingId,
                                isPlaying = nowIsPlaying,
                                // Recently played isn't a playlist: play the song and let radio follow.
                                onClick = { if (s.playId == nowPlayingId) onOpenPlayer() else PlayerController.playSong(s) },
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(top = Space.section)) {
                    LinkRow(Icons.Rounded.BarChart, "Statistics", "Your listening, in numbers", onStatistics)
                    Hairline()
                    LinkRow(Icons.Rounded.Palette, "Theme", "Accent colour", onThemeCustomize)
                    Hairline()
                    LinkRow(Icons.Rounded.Settings, "Settings", "Sound, offline, backup and about", onSettings)
                }
            }
        }
        // Content scrolling up under the clock fades into black instead of colliding with it.
        val fadeIn = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusTop + 28.dp)
                .graphicsLayer { alpha = (scrollY() / fadeIn).coerceIn(0f, 1f) }
                .background(Brush.verticalGradient(0f to BgBase, 0.6f to BgBase.copy(alpha = 0.85f), 1f to Color.Transparent)),
        )
    }
}

// ---------------------------------------------------------------- pieces

/** Monogram in the brand gradient, ringed like the Home avatar. */
@Composable
private fun Header() {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(104.dp)
                .shadow(28.dp, CircleShape, ambientColor = BrandPurple, spotColor = BrandPurple)
                .border(2.dp, Brush.linearGradient(listOf(BrandPurple, BrandPink)), CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(BrandViolet.copy(alpha = 0.85f), BrandPink.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(DISPLAY_NAME.take(1), style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp), color = Color.White)
        }
        Spacer(Modifier.height(14.dp))
        Text(DISPLAY_NAME, style = MaterialTheme.typography.displaySmall, color = Color.White)
        Text("Music for a calmer you", style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

/** Your three collections as one glass strip; each number opens that collection in Library. */
@Composable
private fun StatStrip(liked: Int, playlists: Int, played: Int, onOpen: (LibraryTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(84.dp).glass(Radius.panelShape, Glass.Clear)) {
        Stat(liked, "Liked", Modifier.weight(1f)) { onOpen(LibraryTab.LIKED) }
        Divider()
        Stat(playlists, if (playlists == 1) "Playlist" else "Playlists", Modifier.weight(1f)) { onOpen(LibraryTab.PLAYLISTS) }
        Divider()
        Stat(played, "Played", Modifier.weight(1f)) { onOpen(LibraryTab.RECENT) }
    }
}

@Composable
private fun Stat(value: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().pressable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, fontFeatureSettings = "tnum"),
            color = Color.White,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(vertical = 20.dp).width(0.7.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.1f)))
}

/** The artist you've been playing most lately, lit by their artwork. */
@Composable
private fun TopArtistCard(top: TopArtist, light: ArtworkLight, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.panelShape)
            .background(Brush.linearGradient(listOf(light.key.copy(alpha = 0.26f), light.fill.copy(alpha = 0.08f))))
            .glass(Radius.panelShape, Glass.Clear)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = top.songs.first().artworkUrl, shape = CircleShape, modifier = Modifier.size(84.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text("TOP ARTIST LATELY", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(3.dp))
            Text(top.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${top.songs.size} songs in your recent listening", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
            Spacer(Modifier.height(10.dp))
            SolidPillButton(label = "Play", icon = Icons.Rounded.PlayArrow, onClick = onPlay)
        }
    }
}

@Composable
private fun RecentCard(song: Song, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(112.dp).pressable(onClick = onClick)) {
        Box {
            Artwork(url = song.artworkUrl, shape = Radius.cardShape, modifier = Modifier.size(112.dp))
            if (isCurrent) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(8.dp).glass(Radius.pill, Glass.Frosted, tint = Color.Black.copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 6.dp),
                ) { EqualizerBars(playing = isPlaying, color = Color.White) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(song.title, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).pressable(onClick = onClick).padding(horizontal = Space.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).glass(RoundedCornerShape(9.dp), Glass.Clear), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f))
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.padding(start = 64.dp, end = Space.gutter).fillMaxWidth().height(0.7.dp).background(Color.White.copy(alpha = 0.07f)))
}
