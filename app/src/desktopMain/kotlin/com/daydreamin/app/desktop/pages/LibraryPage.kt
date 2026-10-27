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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.download.DownloadStatus
import com.daydreamin.app.data.download.Downloads
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.Playlist
import com.daydreamin.app.desktop.components.CardTile
import com.daydreamin.app.desktop.components.DButton
import com.daydreamin.app.desktop.components.DIconButton
import com.daydreamin.app.desktop.components.MenuEntry
import com.daydreamin.app.desktop.components.TileGrid
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.shell.IconTile
import com.daydreamin.app.desktop.theme.DSpace
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.LikedTile
import com.daydreamin.app.ui.components.NameDialog
import com.daydreamin.app.ui.components.PlaylistCover
import com.daydreamin.app.ui.components.SongActions
import kotlinx.coroutines.launch

private enum class LibraryFilter(val label: String) { ALL("All"), MADE("Made by you"), SAVED("Saved"), COLLECTIONS("Songs") }

/** One tile in the library: a collection of yours or a playlist. */
private class LibraryItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
    val route: Route,
    val playlist: Playlist? = null,
    val art: @Composable (androidx.compose.ui.unit.Dp) -> Unit,
)

/**
 * Your Library: Liked songs, downloads, history and every playlist as a grid of tiles — filter
 * to the ones you made or saved, sort by name, and create a playlist. Right-click a playlist for
 * what you can do with it.
 */
@Composable
fun LibraryPage() {
    val nav = LocalNavigator.current
    val prefs = AppGraph.prefs
    val liked by prefs.likedSongs.collectAsState(initial = emptyList())
    val history by prefs.history.collectAsState(initial = emptyList())
    val playlists by prefs.playlists.collectAsState(initial = emptyList())
    val downloadedIds by prefs.downloadedPlaylists.collectAsState(initial = emptySet())
    val downloads by Downloads.items.collectAsState()
    val downloaded = downloads.values.filter { it.status == DownloadStatus.DONE }.map { it.song }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var byName by rememberSaveable { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    val scope = rememberCoroutineScope()
    if (naming) NameDialog(title = "New playlist", confirm = "Create", onDismiss = { naming = false }, onConfirm = { SongActions.createPlaylist(it); naming = false })
    renaming?.let { p -> NameDialog(title = "Rename playlist", confirm = "Rename", initial = p.name, onDismiss = { renaming = null }, onConfirm = { SongActions.renamePlaylist(p.id, it); renaming = null }) }

    val collections = listOf(
        LibraryItem("liked", "Liked songs", "Playlist · ${liked.size} songs", liked, Route.Liked) { LikedTile(it) },
        LibraryItem("downloads", "Downloads", "${downloaded.size} on this computer", downloaded, Route.Downloads) { IconTile(Icons.Rounded.DownloadForOffline, it) },
        LibraryItem("history", "Recently played", "${history.size} songs", history, Route.History) { IconTile(Icons.Rounded.History, it) },
    )
    val lists = playlists
        .filter { when (filter) { LibraryFilter.MADE -> !it.isSaved; LibraryFilter.SAVED -> it.isSaved; LibraryFilter.COLLECTIONS -> false; LibraryFilter.ALL -> true } }
        .let { if (byName) it.sortedBy { p -> p.name.lowercase() } else it }
        .map { p -> LibraryItem(p.id, p.name, "Playlist · ${p.author ?: "You"} · ${p.songs.size} songs", p.songs, Route.Playlist(p.id), p) { s -> PlaylistCover(p, s) } }
    val items = (if (filter == LibraryFilter.ALL || filter == LibraryFilter.COLLECTIONS) collections else emptyList()) + lists
    val state = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        PageGlow(DefaultPageLight, height = 260.dp)
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)) {
            item(key = "title") {
                Row(Modifier.fillMaxWidth().padding(horizontal = DSpace.pagePad), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your Library", style = DType.pageTitle, modifier = Modifier.weight(1f))
                    DButton("New playlist", { naming = true }, icon = Icons.Rounded.Add)
                }
            }
            item(key = "filters") {
                Row(Modifier.fillMaxWidth().padding(horizontal = DSpace.pagePad).padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryFilter.entries.forEach { f -> ViewChip(f.label, filter == f) { filter = f } }
                    }
                    DIconButton(
                        if (byName) Icons.Rounded.SortByAlpha else Icons.Rounded.Schedule,
                        if (byName) "Sorted by name — sort by when added" else "Sorted by when added — sort by name",
                        { byName = !byName },
                        iconSize = 20.dp,
                    )
                }
            }
            if (items.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (filter == LibraryFilter.SAVED) "Playlists you save from Search show up here." else "Playlists you make show up here.",
                        style = DType.body, modifier = Modifier.padding(horizontal = DSpace.pagePad, vertical = 12.dp),
                    )
                }
            } else item(key = "grid") {
                TileGrid(items) { item, m ->
                    val p = item.playlist
                    CardTile(
                        item.title, item.subtitle,
                        onOpen = { nav.go(item.route) },
                        onPlay = { PlayerController.playFromList(item.songs, 0) }.takeIf { item.songs.isNotEmpty() },
                        modifier = m,
                        menu = {
                            buildList {
                                add(MenuEntry("Play", Icons.Rounded.PlayArrow, enabled = item.songs.isNotEmpty()) { PlayerController.playFromList(item.songs, 0) })
                                add(MenuEntry("Shuffle play", Icons.Rounded.Shuffle, enabled = item.songs.isNotEmpty()) { PlayerController.playFromList(item.songs.shuffled(), 0) })
                                if (p != null) {
                                    val keep = p.id in downloadedIds
                                    add(null)
                                    add(MenuEntry(if (keep) "Remove download" else "Download", if (keep) Icons.Rounded.DownloadDone else Icons.Rounded.Download) { scope.launch { prefs.setPlaylistDownloaded(p.id, !keep) } })
                                    add(MenuEntry("Rename…", Icons.Rounded.Edit) { renaming = p })
                                    add(MenuEntry("Delete", Icons.Rounded.Delete, danger = true) { SongActions.deletePlaylist(p.id) })
                                }
                            }
                        },
                    ) { am -> Box(am.clip(RoundedCornerShape(6.dp))) { BoxWithConstraints { item.art(maxWidth) } } }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}
