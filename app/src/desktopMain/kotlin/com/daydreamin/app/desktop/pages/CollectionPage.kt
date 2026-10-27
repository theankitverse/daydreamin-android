package com.daydreamin.app.desktop.pages

import androidx.compose.runtime.produceState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Person
import com.daydreamin.app.ui.components.Artwork
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.download.DownloadStatus
import com.daydreamin.app.data.download.Downloads
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.desktop.components.DButton
import com.daydreamin.app.desktop.components.DIconButton
import com.daydreamin.app.desktop.components.MenuEntry
import com.daydreamin.app.desktop.components.MenuPopup
import com.daydreamin.app.desktop.components.TrackTableState
import com.daydreamin.app.desktop.components.trackTable
import com.daydreamin.app.desktop.components.trackTableKeys
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.shell.IconTile
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DSpace
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.LikedTile
import com.daydreamin.app.ui.components.Mosaic
import com.daydreamin.app.desktop.components.NameDialog
import com.daydreamin.app.ui.components.PlaylistCover
import com.daydreamin.app.ui.components.SongActions
import com.daydreamin.app.ui.screens.home.HomeViewModel
import com.daydreamin.app.ui.screens.home.MoodCard
import com.daydreamin.app.ui.theme.rememberArtworkLight
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

/** What a collection page shows: its songs and how it presents itself. */
private class Collection(
    val kind: String,
    val title: String,
    val owner: String?,
    val songs: List<Song>?,
    val art: @Composable (Dp) -> Unit,
    val playlistId: String? = null,
    val emptyText: String = "Nothing here yet.",
    /** A YouTube playlist: can be saved to your library. */
    val sourceUrl: String? = null,
    val coverUrl: String? = null,
)

/**
 * One page for every list of songs — Liked songs, a playlist, downloads, history, a mood, a
 * "Show all": a header with the artwork, title and length, the actions, then the song table.
 */
@Composable
fun CollectionPage(route: Route) {
    val c = collectionFor(route)
    val songs = c.songs
    val nav = LocalNavigator.current
    val meta by PlayerController.meta.collectAsState()
    val table = remember(route) { TrackTableState() }
    var filter by remember(route) { mutableStateOf("") }
    val shown = remember(songs, filter) {
        val q = filter.trim().lowercase()
        if (q.isEmpty()) songs.orEmpty() else songs.orEmpty().filter { q in it.title.lowercase() || q in it.artist.lowercase() || q in it.album.lowercase() }
    }
    val light = rememberArtworkLight(songs?.firstOrNull()?.let { it.cover.ifBlank { it.artworkUrl } }, DefaultPageLight)
    val playingThis = meta.isPlaying && shown.any { it.playId == meta.currentSong?.playId }
    val state = rememberLazyListState()
    val play: (Int, List<Song>) -> Unit = { i, list -> PlayerController.playFromList(list, i) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showAlbum = maxWidth > 760.dp && shown.any { it.album.isNotBlank() && it.album != "Single" }
        val wide = maxWidth > 900.dp
        val artSize = if (wide) 220.dp else 168.dp
        PageGlow(light, height = 420.dp)
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().trackTableKeys(table, { table.sorted(shown) }) { i -> play(i, table.sorted(shown)) }.focusable(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(start = DSpace.pagePad, end = DSpace.pagePad, top = 28.dp, bottom = 20.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.size(artSize).shadow(24.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))) { c.art(artSize) }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.kind.uppercase(), style = DType.label.copy(color = Color.White.copy(alpha = 0.8f)))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            c.title,
                            style = DType.hero.copy(fontSize = if (c.title.length > 22) 36.sp else if (wide) 56.sp else 40.sp, lineHeight = if (c.title.length > 22) 42.sp else 62.sp),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            listOfNotNull(c.owner, songs?.let { "${it.size} ${if (it.size == 1) "song" else "songs"}" + totalLength(it).let { t -> if (t.isNotEmpty()) ", $t" else "" } }).joinToString(" · "),
                            style = DType.body.copy(color = Color.White.copy(alpha = 0.8f)),
                        )
                    }
                }
            }
            item(key = "actions") { ActionRow(c, shown, playingThis, filter, { filter = it }) { play(0, table.sorted(shown)) } }
            when {
                songs == null -> item(key = "loading") { Text("Loading…", style = DType.body, modifier = Modifier.padding(DSpace.pagePad)) }
                songs.isEmpty() -> item(key = "empty") { Text(c.emptyText, style = DType.body, modifier = Modifier.padding(DSpace.pagePad)) }
                shown.isEmpty() -> item(key = "nomatch") { Text("No songs match “$filter”.", style = DType.body, modifier = Modifier.padding(DSpace.pagePad)) }
                else -> trackTable(
                    shown, table, showAlbum,
                    onPlay = play,
                    extraMenu = { song ->
                        if (c.playlistId != null) listOf(MenuEntry("Remove from this playlist", Icons.Rounded.RemoveCircleOutline) { SongActions.removeFromPlaylist(c.playlistId, song) })
                        else emptyList()
                    },
                )
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

@Composable
private fun ActionRow(c: Collection, shown: List<Song>, playingThis: Boolean, filter: String, onFilter: (String) -> Unit, onPlay: () -> Unit) {
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val downloadedPlaylists by AppGraph.prefs.downloadedPlaylists.collectAsState(initial = emptySet())
    val downloadLiked by AppGraph.prefs.downloadLiked.collectAsState(initial = false)
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    if (renaming && c.playlistId != null) NameDialog(title = "Rename playlist", confirm = "Rename", initial = c.title, onDismiss = { renaming = false }, onConfirm = { SongActions.renamePlaylist(c.playlistId, it); renaming = false })

    Row(Modifier.fillMaxWidth().padding(horizontal = DSpace.pagePad).padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        BigPlayButton(playingThis, size = 56.dp) { if (playingThis) PlayerController.togglePlayPause() else if (shown.isNotEmpty()) onPlay() }
        Spacer(Modifier.width(16.dp))
        DIconButton(Icons.Rounded.Shuffle, "Shuffle play", { if (shown.isNotEmpty()) PlayerController.playFromList(shown.shuffled(), 0) }, size = 40.dp, iconSize = 26.dp)
        when {
            c.playlistId != null -> {
                val on = c.playlistId in downloadedPlaylists
                DIconButton(if (on) Icons.Rounded.DownloadDone else Icons.Rounded.Download, if (on) "Remove download" else "Download", { scope.launch { AppGraph.prefs.setPlaylistDownloaded(c.playlistId, !on) } }, size = 40.dp, iconSize = 24.dp, active = on, activeColor = com.daydreamin.app.desktop.components.LocalAccent.current)
            }
            c.kind == "Playlist" && c.title == "Liked songs" -> {
                DIconButton(if (downloadLiked) Icons.Rounded.DownloadDone else Icons.Rounded.Download, if (downloadLiked) "Stop keeping liked songs downloaded" else "Download liked songs", { scope.launch { AppGraph.prefs.setDownloadLiked(!downloadLiked) } }, size = 40.dp, iconSize = 24.dp, active = downloadLiked, activeColor = com.daydreamin.app.desktop.components.LocalAccent.current)
            }
        }
        if (c.sourceUrl != null) {
            val library by AppGraph.prefs.playlists.collectAsState(initial = emptyList())
            val saved = library.any { it.sourceUrl == c.sourceUrl }
            DIconButton(
                if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                if (saved) "In your library" else "Save to your library",
                { if (!saved && shown.isNotEmpty()) SongActions.savePlaylist(c.title, shown, c.sourceUrl, c.owner, c.coverUrl) },
                size = 40.dp, iconSize = 24.dp, active = saved, activeColor = com.daydreamin.app.desktop.components.LocalAccent.current,
            )
        }
        if (c.playlistId != null) {
            Box {
                DIconButton(Icons.Rounded.MoreHoriz, "More options for ${c.title}", { menuOpen = true }, size = 40.dp, iconSize = 26.dp)
                if (menuOpen) MenuPopup(
                    listOf(
                        MenuEntry("Rename…", Icons.Rounded.Edit) { renaming = true },
                        MenuEntry("Delete playlist", Icons.Rounded.Delete, danger = true) { SongActions.deletePlaylist(c.playlistId); nav.goBack() },
                    ),
                    onDismiss = { menuOpen = false },
                )
            }
        }
        if (c.kind == "History" && shown.isNotEmpty()) DButton("Clear history", { scope.launch { AppGraph.prefs.clearHistory() } })
        Spacer(Modifier.weight(1f))
        // Find a song in this list.
        if (searching || filter.isNotEmpty()) {
            Row(
                Modifier.width(220.dp).height(34.dp).clip(RoundedCornerShape(6.dp)).background(DColors.field).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = DColors.iconIdle, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (filter.isEmpty()) Text("Search in ${if (c.playlistId != null) "playlist" else "this list"}", style = DType.small.copy(color = Color.White.copy(alpha = 0.45f)))
                    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                    BasicTextField(filter, onFilter, singleLine = true, textStyle = DType.body.copy(color = Color.White), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth().focusRequester(focus))
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                }
            }
        } else {
            DIconButton(Icons.Rounded.Search, "Search in this list", { searching = true }, iconSize = 20.dp)
        }
    }
}

@Composable
private fun collectionFor(route: Route): Collection {
    val prefs = AppGraph.prefs
    return when (route) {
        Route.Liked -> {
            val liked by prefs.likedSongs.collectAsState(initial = null)
            val name by prefs.userName.collectAsState(initial = "")
            Collection("Playlist", "Liked songs", name.ifBlank { "You" }, liked, { LikedTile(it) }, emptyText = "Songs you like show up here. Right-click any song and choose Save to Liked songs, or click its ♡.")
        }
        is Route.Playlist -> {
            val playlists by prefs.playlists.collectAsState(initial = null)
            val p = playlists?.firstOrNull { it.id == route.id }
            Collection("Playlist", p?.name ?: "Playlist", p?.author ?: "You", p?.songs ?: if (playlists == null) null else emptyList(), { s -> p?.let { PlaylistCover(it, s) } }, playlistId = route.id, emptyText = "This playlist is empty. Right-click any song and choose Add to playlist.")
        }
        Route.Downloads -> {
            val items by Downloads.items.collectAsState()
            val done = items.values.filter { it.status == DownloadStatus.DONE }.map { it.song }
            Collection("Downloads", "Downloads", "On this computer", done, { IconTile(Icons.Rounded.DownloadForOffline, it) }, emptyText = "Songs you download play without a connection. Right-click any song and choose Download.")
        }
        Route.History -> {
            val history by prefs.history.collectAsState(initial = null)
            Collection("History", "Recently played", null, history, { IconTile(Icons.Rounded.History, it) }, emptyText = "Songs you play show up here.")
        }
        is Route.Collection -> Collection(route.kind, route.title, route.subtitle, route.songs, { Mosaic(route.songs, it) })
        is Route.Mood -> {
            val vm: HomeViewModel = composeViewModel { HomeViewModel() }
            val browse by vm.browse.collectAsState()
            LaunchedEffect(route) { vm.openMood(MoodCard(route.title, route.query, 0xFF8B7CF6)) }
            val songs = browse?.takeIf { it.mood.title == route.title && !it.loading }?.songs
            Collection("Mood", route.title, "Songs for this mood", songs, { s -> songs?.let { Mosaic(it, s) } ?: IconTile(Icons.Rounded.Shuffle, s) }, emptyText = browse?.error ?: "Nothing found for this mood right now.")
        }
        is Route.Artist -> {
            val songs by produceState<List<Song>?>(null, route) {
                val all = AppGraph.repository.smartSearch(route.name).getOrDefault(emptyList())
                val name = route.name.trim().lowercase()
                // Their own songs first; if the search found none credited to them, what it found.
                value = all.filter { name in it.artist.lowercase() }.ifEmpty { all }
            }
            Collection("Artist", route.name, null, songs, { s -> songs?.firstOrNull()?.let { Artwork(url = it.cover.ifBlank { it.artworkUrl }, shape = CircleShape, modifier = Modifier.size(s)) } ?: IconTile(Icons.Rounded.Person, s) }, emptyText = "Couldn’t find songs by ${route.name} right now.")
        }
        is Route.YtPlaylist -> {
            val songs by produceState<List<Song>?>(null, route) { value = AppGraph.repository.playlistTracks(route.url).getOrDefault(emptyList()) }
            Collection("Playlist", route.title, route.author, songs, { s -> Artwork(url = route.thumbnail, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(s)) }, emptyText = "Couldn’t open this playlist right now.", sourceUrl = route.url, coverUrl = route.thumbnail)
        }
        else -> Collection("", "", null, emptyList(), { })
    }
}

/** "1 hr 32 min", "23 min 12 sec". */
private fun totalLength(songs: List<Song>): String {
    val s = songs.sumOf { it.duration.toLong() }
    if (s <= 0) return ""
    val h = s / 3600; val m = s / 60 % 60
    return if (h > 0) "$h hr $m min" else "$m min ${s % 60} sec"
}
