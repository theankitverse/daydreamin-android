package com.daydreamin.app.desktop.pages

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.recommend.Recommender
import com.daydreamin.app.desktop.components.CardTile
import com.daydreamin.app.desktop.components.DButton
import com.daydreamin.app.desktop.components.Shelf
import com.daydreamin.app.desktop.components.TrackTableState
import com.daydreamin.app.desktop.components.desktopClickable
import com.daydreamin.app.desktop.components.desktopGlass
import com.daydreamin.app.desktop.components.trackTable
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.theme.DSpace
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.UserAvatar
import com.daydreamin.app.ui.screens.profile.EditProfileDialog
import com.daydreamin.app.ui.theme.rememberArtworkLight

/** Artists you've played most lately — "Arijit Singh - Topic" and "Arijit Singh" counted as one, record labels left out. */
private fun topArtists(history: List<Song>, max: Int): List<Pair<String, List<Song>>> =
    history
        .filter { it.artist.isNotBlank() && !it.artist.equals("Unknown", ignoreCase = true) }
        .groupBy { Recommender.artistKey(it.artist) }
        .filterKeys { it.isNotBlank() && !Recommender.isLabel(it) }
        .values
        .sortedByDescending { it.size }
        .take(max)
        .map { Recommender.primaryArtist(it.first().artist) to it }

/**
 * You: your photo and name (click to change), your collection at a glance, the artists you've
 * been playing and what you played last. Everything comes from what's kept on this computer.
 */
@Composable
fun ProfilePage() {
    val prefs = AppGraph.prefs
    val nav = LocalNavigator.current
    val name by prefs.userName.collectAsState(initial = "")
    val liked by prefs.likedSongs.collectAsState(initial = emptyList())
    val history by prefs.history.collectAsState(initial = emptyList())
    val playlists by prefs.playlists.collectAsState(initial = emptyList())
    val totalPlays by prefs.totalPlays.collectAsState(initial = null)
    var editing by remember { mutableStateOf(false) }
    if (editing) EditProfileDialog(initialName = name, onDismiss = { editing = false })
    val artists = remember(history) { topArtists(history, 8) }
    val lead = artists.firstOrNull()?.second?.firstOrNull() ?: history.firstOrNull()
    val light = rememberArtworkLight(lead?.let { it.cover.ifBlank { it.artworkUrl } }, DefaultPageLight)
    val recent = remember(history) { history.distinctBy { it.playId } }
    val state = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        PageGlow(light, height = 420.dp)
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(DSpace.sectionGap)) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(start = DSpace.pagePad, end = DSpace.pagePad, top = 36.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.size(180.dp)) { UserAvatar(size = 180.dp, ring = false) }
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PROFILE", style = DType.label.copy(color = Color.White.copy(alpha = 0.8f)))
                        Spacer(Modifier.height(6.dp))
                        Text(name.ifBlank { "You" }, style = DType.hero.copy(fontSize = 64.sp, lineHeight = 70.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            listOf("${playlists.size} ${if (playlists.size == 1) "playlist" else "playlists"}", "${liked.size} liked", totalPlays?.let { "$it plays" }).filterNotNull().joinToString(" · "),
                            style = DType.body.copy(color = Color.White.copy(alpha = 0.8f)),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DButton("Edit profile", { editing = true }, icon = Icons.Rounded.Edit)
                            DButton("Your stats", { nav.go(Route.Stats) }, icon = Icons.Rounded.BarChart)
                        }
                    }
                }
            }
            if (artists.isNotEmpty()) item(key = "artists") {
                Shelf("Top artists lately", artists, subtitle = "From what you've been playing", onShowAll = { nav.go(Route.Stats) }) { (artist, songs), m ->
                    CardTile(artist, "${songs.size} ${if (songs.size == 1) "play" else "plays"}", onOpen = { nav.go(Route.Artist(artist)) }, onPlay = { PlayerController.playFromList(songs.distinctBy { it.playId }, 0) }, modifier = m, round = true) { am ->
                        Artwork(url = songs.first().let { it.cover.ifBlank { it.artworkUrl } }, shape = CircleShape, modifier = am)
                    }
                }
            }
            if (recent.isNotEmpty()) item(key = "recent") {
                Shelf("Recently played", recent, onShowAll = { nav.go(Route.History) }) { song, m -> SongCard(song, recent, null, m) }
            }
            if (playlists.isNotEmpty()) item(key = "playlists") {
                Shelf("Your playlists", playlists, onShowAll = { nav.go(Route.Library) }) { p, m ->
                    CardTile(p.name, "${p.songs.size} songs", onOpen = { nav.go(Route.Playlist(p.id)) }, onPlay = { PlayerController.playFromList(p.songs, 0) }.takeIf { p.songs.isNotEmpty() }, modifier = m) { am ->
                        Box(am.clip(RoundedCornerShape(6.dp))) { BoxWithConstraints { com.daydreamin.app.ui.components.PlaylistCover(p, maxWidth) } }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

/**
 * Your listening, in numbers that are actually measured: how much you've played lately and from
 * how many artists, your collection, your top artists, and the songs behind them.
 */
@Composable
fun StatsPage() {
    val prefs = AppGraph.prefs
    val nav = LocalNavigator.current
    val history by prefs.history.collectAsState(initial = emptyList())
    val liked by prefs.likedSongs.collectAsState(initial = emptyList())
    val playlists by prefs.playlists.collectAsState(initial = emptyList())
    val totalPlays by prefs.totalPlays.collectAsState(initial = null)
    val artists = remember(history) { topArtists(history, 10) }
    val likedMinutes = remember(liked) { liked.sumOf { it.duration.coerceAtLeast(0) } / 60 }
    val lead = artists.firstOrNull()?.second?.firstOrNull()
    val light = rememberArtworkLight(lead?.let { it.cover.ifBlank { it.artworkUrl } }, DefaultPageLight)
    val table = remember { TrackTableState() }
    val recent = remember(history) { history.distinctBy { it.playId }.take(50) }
    val state = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        PageGlow(light, height = 360.dp)
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp)) {
            item(key = "title") { Text("Your stats", style = DType.pageTitle, modifier = Modifier.padding(horizontal = DSpace.pagePad).padding(bottom = 20.dp)) }
            item(key = "tiles") {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = DSpace.pagePad)) {
                    val tiles = listOf(
                        (totalPlays ?: history.size).toString() to if (totalPlays != null) "songs played\nall time" else "songs in your\nrecent listening",
                        history.map { Recommender.artistKey(it.artist) }.distinct().size.toString() to "different artists\nlately",
                        liked.size.toString() to if (likedMinutes > 0) "liked songs ·\n$likedMinutes min of music" else "liked\nsongs",
                        playlists.size.toString() to "${if (playlists.size == 1) "playlist" else "playlists"} ·\n${playlists.sumOf { it.songs.size }} songs",
                    )
                    val perRow = if (maxWidth > 760.dp) 4 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tiles.chunked(perRow).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { (v, l) -> StatTile(v, l, Modifier.weight(1f)) } }
                        }
                    }
                }
            }
            if (artists.isNotEmpty()) {
                item(key = "artists-title") { Text("Top artists lately", style = DType.section, modifier = Modifier.padding(start = DSpace.pagePad, top = DSpace.sectionGap, bottom = 8.dp)) }
                artists.forEachIndexed { i, (artist, songs) ->
                    item(key = "artist-$i") { ArtistRank(i + 1, artist, songs, onOpen = { nav.go(Route.Artist(artist)) }) }
                }
            }
            if (recent.isNotEmpty()) {
                item(key = "recent-title") { Text("Recently played", style = DType.section, modifier = Modifier.padding(start = DSpace.pagePad, top = DSpace.sectionGap, bottom = 8.dp)) }
                trackTable(recent, table, showAlbum = false, onPlay = { i, list -> PlayerController.playFromList(list, i) })
            } else item(key = "none") {
                Text("Your numbers start with your first song. Play anything and come back.", style = DType.body, modifier = Modifier.padding(DSpace.pagePad))
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    Column(modifier.height(116.dp).desktopGlass(RoundedCornerShape(10.dp), tint = Color.White.copy(alpha = 0.04f)).padding(18.dp)) {
        Text(value, style = DType.hero.copy(fontSize = 34.sp, lineHeight = 38.sp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = DType.small)
    }
}

@Composable
private fun ArtistRank(rank: Int, artist: String, songs: List<Song>, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DSpace.pagePad - 8.dp)
            .height(64.dp)
            .desktopClickable(RoundedCornerShape(6.dp), onClick = onOpen)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = DType.numeric.copy(fontSize = 15.sp), modifier = Modifier.width(32.dp))
        Artwork(url = songs.first().let { it.cover.ifBlank { it.artworkUrl } }, shape = CircleShape, modifier = Modifier.size(46.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(artist, style = DType.itemTitleStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${songs.size} ${if (songs.size == 1) "play" else "plays"} lately", style = DType.itemSubtitle)
        }
        DButton("Play", { PlayerController.playFromList(songs.distinctBy { it.playId }, 0) })
    }
}
