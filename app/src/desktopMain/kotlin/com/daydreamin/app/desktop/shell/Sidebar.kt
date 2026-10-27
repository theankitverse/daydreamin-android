package com.daydreamin.app.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.download.DownloadStatus
import com.daydreamin.app.data.download.Downloads
import com.daydreamin.app.data.prefs.Playlist
import com.daydreamin.app.desktop.components.ContextMenuArea
import com.daydreamin.app.desktop.components.desktopGlass
import com.daydreamin.app.desktop.components.DIconButton
import com.daydreamin.app.desktop.components.LocalAccent
import com.daydreamin.app.desktop.components.MenuEntry
import com.daydreamin.app.desktop.components.songDropTarget
import com.daydreamin.app.desktop.components.WithTooltip
import com.daydreamin.app.desktop.components.desktopClickable
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DSize
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.LikedTile
import com.daydreamin.app.desktop.components.NameDialog
import com.daydreamin.app.ui.components.PlaylistCover
import com.daydreamin.app.ui.components.SongActions
import kotlinx.coroutines.launch

/** A panel: the rounded dark surface the sidebar, page and side panel each sit on. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.desktopGlass(RoundedCornerShape(DSize.panelRadius))) { content() }
}

/**
 * The sidebar: where to go (Home, Search) and your library — Liked songs, downloads, history and
 * every playlist, one click away. [narrow]: icons only, with tooltips.
 */
@Composable
fun Sidebar(narrow: Boolean, width: Dp, modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    val current = nav.current
    var naming by remember { mutableStateOf(false) }
    if (naming) NameDialog(title = "New playlist", confirm = "Create", onDismiss = { naming = false }, onConfirm = { SongActions.createPlaylist(it); naming = false })

    Column(modifier.width(width).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp, horizontal = 8.dp)) {
                NavRow(Icons.Rounded.Home, "Home", current == Route.Home, narrow, shortcut = null) { nav.go(Route.Home) }
                NavRow(Icons.Rounded.Search, "Search", current is Route.Search, narrow, shortcut = "${cmd}K") {
                    if (current !is Route.Search) nav.go(Route.Search())
                    DesktopUi.requestSearchFocus()
                }
            }
        }
        Panel(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = if (narrow) 0.dp else 16.dp, end = if (narrow) 0.dp else 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (narrow) Arrangement.Center else Arrangement.Start,
                ) {
                    if (!narrow) {
                        Row(
                            Modifier.weight(1f).desktopClickable(highlight = false) { nav.go(Route.Library) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Your Library", style = DType.panelTitle.copy(color = Color.White.copy(alpha = 0.85f)))
                        }
                    }
                    DIconButton(Icons.Rounded.Add, "New playlist", { naming = true }, iconSize = 20.dp)
                }
                LibraryList(narrow)
            }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, selected: Boolean, narrow: Boolean, shortcut: String?, onClick: () -> Unit) {
    val color = if (selected) Color.White else Color.White.copy(alpha = 0.66f)
    WithTooltip(if (narrow) label else null, shortcut) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).desktopClickable(RoundedCornerShape(6.dp), onClick = onClick).padding(horizontal = if (narrow) 0.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (narrow) Arrangement.Center else Arrangement.Start,
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
            if (!narrow) {
                Spacer(Modifier.width(16.dp))
                Text(label, style = DType.itemTitleStrong.copy(color = color, fontSize = DType.panelTitle.fontSize))
            }
        }
    }
}

@Composable
private fun LibraryList(narrow: Boolean) {
    val nav = LocalNavigator.current
    val current = nav.current
    val liked by AppGraph.prefs.likedSongs.collectAsState(initial = emptyList())
    val history by AppGraph.prefs.history.collectAsState(initial = emptyList())
    val playlists by AppGraph.prefs.playlists.collectAsState(initial = emptyList())
    val downloads by Downloads.items.collectAsState()
    val downloaded = downloads.values.count { it.status == DownloadStatus.DONE }
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)) {
            item {
                LibraryRow(
                    narrow, "Liked songs", "Playlist · ${liked.size} songs", current == Route.Liked, onClick = { nav.go(Route.Liked) }, onPlay = { PlayerController.playFromList(liked, 0) }.takeIf { liked.isNotEmpty() },
                    drop = Modifier.songDropTarget("liked", "Liked songs") { song -> if (liked.none { it.playId == song.playId }) SongActions.toggleLiked(song, currentlyLiked = false) },
                ) { LikedTile(it) }
            }
            item {
                LibraryRow(narrow, "Downloads", "$downloaded on this computer", current == Route.Downloads, onClick = { nav.go(Route.Downloads) }) { IconTile(Icons.Rounded.DownloadDone, it) }
            }
            item {
                LibraryRow(narrow, "Recently played", "${history.size} songs", current == Route.History, onClick = { nav.go(Route.History) }, onPlay = { PlayerController.playFromList(history, 0) }.takeIf { history.isNotEmpty() }) { IconTile(Icons.Rounded.History, it) }
            }
            items(playlists, key = { it.id }) { p -> PlaylistRow(p, narrow, current == Route.Playlist(p.id)) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

@Composable
private fun PlaylistRow(p: Playlist, narrow: Boolean, selected: Boolean) {
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val downloadedIds by AppGraph.prefs.downloadedPlaylists.collectAsState(initial = emptySet())
    var renaming by remember { mutableStateOf(false) }
    if (renaming) NameDialog(title = "Rename playlist", confirm = "Rename", initial = p.name, onDismiss = { renaming = false }, onConfirm = { SongActions.renamePlaylist(p.id, it); renaming = false })
    val keep = p.id in downloadedIds
    ContextMenuArea({
        listOf(
            MenuEntry("Play", Icons.Rounded.PlayArrow, enabled = p.songs.isNotEmpty()) { PlayerController.playFromList(p.songs, 0) },
            MenuEntry("Shuffle play", Icons.Rounded.Shuffle, enabled = p.songs.isNotEmpty()) { PlayerController.playFromList(p.songs.shuffled(), 0) },
            null,
            MenuEntry(if (keep) "Remove download" else "Download", if (keep) Icons.Rounded.DownloadDone else Icons.Rounded.Download) {
                scope.launch { AppGraph.prefs.setPlaylistDownloaded(p.id, !keep) }
            },
            MenuEntry("Rename…", Icons.Rounded.Edit) { renaming = true },
            MenuEntry("Delete", Icons.Rounded.Delete, danger = true) { SongActions.deletePlaylist(p.id); if (selected) nav.go(Route.Library) },
        )
    }) {
        LibraryRow(
            narrow, p.name,
            "Playlist · ${p.author ?: "You"}",
            selected,
            onClick = { nav.go(Route.Playlist(p.id)) },
            onPlay = { PlayerController.playFromList(p.songs, 0) }.takeIf { p.songs.isNotEmpty() },
            drop = Modifier.songDropTarget("playlist-${p.id}", p.name) { song -> SongActions.addToPlaylist(p, song) },
        ) { PlaylistCover(p, it) }
    }
}

@Composable
private fun LibraryRow(
    narrow: Boolean,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
    drop: Modifier = Modifier,
    art: @Composable (Dp) -> Unit,
) {
    WithTooltip(if (narrow) title else null) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .then(drop)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) DColors.selected else Color.Transparent)
                .desktopClickable(RoundedCornerShape(6.dp), onClick = onClick)
                .padding(horizontal = if (narrow) 0.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (narrow) Arrangement.Center else Arrangement.Start,
        ) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(5.dp))) { art(46.dp) }
            if (!narrow) {
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(title, style = DType.itemTitle.copy(color = if (selected) LocalAccent.current else Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = DType.itemSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun IconTile(icon: ImageVector, size: Dp, tint: Color = Color.White) {
    Box(
        Modifier.size(size).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2A2A3A), Color(0xFF15151C)))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint.copy(alpha = 0.85f), modifier = Modifier.size(size * 0.45f)) }
}
