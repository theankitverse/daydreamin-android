package com.daydreamin.app.desktop.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.player.PlaybackMeta
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.NewPlaylistDialog

/**
 * What every song row needs to know — what's playing, what you've liked — collected once for the
 * whole window. Rows read it through [rememberIsCurrent] / [rememberLiked], which only redraw a row
 * when *its* answer changes (not every row whenever anything changes).
 */
class SharedState(
    val meta: State<PlaybackMeta>,
    val likedIds: State<Set<String>>,
    val menus: SongMenus,
)

val LocalShared = staticCompositionLocalOf<SharedState> { error("no shared state") }

@Composable
fun ProvideSharedState(content: @Composable () -> Unit) {
    val meta = PlayerController.meta.collectAsState()
    val likedIds = AppGraph.prefs.likedIds.collectAsState(initial = emptySet())
    val playlists = AppGraph.prefs.playlists.collectAsState(initial = emptyList())
    val downloads = com.daydreamin.app.data.download.Downloads.items.collectAsState()
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var namingFor by remember { mutableStateOf<Song?>(null) }
    val menus = remember { SongMenus(likedIds, playlists, downloads, nav, scope) { namingFor = it } }
    val shared = remember { SharedState(meta, likedIds, menus) }
    namingFor?.let { song -> NewPlaylistDialog(song, onDismiss = { namingFor = null }, onCreated = { namingFor = null }) }
    CompositionLocalProvider(LocalShared provides shared) { content() }
}

/** Whether [song] is the one playing (or loaded); redraws the caller only when that flips. */
@Composable
fun rememberIsCurrent(song: Song): Boolean {
    val shared = LocalShared.current
    val v by remember(song.playId) { derivedStateOf { shared.meta.value.currentSong?.playId == song.playId } }
    return v
}

/** Whether music is playing right now (not paused). */
@Composable
fun rememberIsPlaying(): Boolean {
    val shared = LocalShared.current
    val v by remember { derivedStateOf { shared.meta.value.isPlaying } }
    return v
}

@Composable
fun rememberLiked(song: Song): Boolean {
    val shared = LocalShared.current
    val v by remember(song.playId) { derivedStateOf { song.playId in shared.likedIds.value } }
    return v
}
