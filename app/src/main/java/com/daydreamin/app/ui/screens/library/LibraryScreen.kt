package com.daydreamin.app.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.Playlist
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.AmbientGlow
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.EqualizerBars
import com.daydreamin.app.ui.components.NameDialog
import com.daydreamin.app.ui.components.SolidPillButton
import com.daydreamin.app.ui.components.SongActions
import com.daydreamin.app.ui.components.SongListRow
import com.daydreamin.app.ui.components.SongMenuAction
import com.daydreamin.app.ui.components.Toaster
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.components.shimmer
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Motion
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.Space
import com.daydreamin.app.ui.theme.glass
import com.daydreamin.app.ui.theme.rememberArtworkLight
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

private val DefaultLight = ArtworkLight(key = BrandViolet, fill = Color(0xFF3D3A9E))
private val TopBarHeight = 56.dp
private val RowHeight = 66.dp

private data class NowPlaying(val playId: String?, val isPlaying: Boolean)

/**
 * Your collection. One segmented glass control switches between the three things the app keeps
 * for you — Liked songs (the main one, with a lit hero), Recently played, and Playlists — and the
 * page is lit by the artwork of whatever collection you're looking at.
 */
@Composable
fun LibraryScreen(contentPadding: PaddingValues, onOpenPlayer: () -> Unit, onGoHome: () -> Unit) {
    val vm: LibraryViewModel = composeViewModel()
    val tab by vm.tab.collectAsState()
    val likedOrNull by vm.likedSongs.collectAsState()
    val historyOrNull by vm.history.collectAsState()
    val playlistsOrNull by vm.playlists.collectAsState()
    val openId by vm.openPlaylistId.collectAsState()
    val meta = PlayerController.meta.collectAsState()
    val nowPlaying by remember { derivedStateOf { NowPlaying(meta.value.currentSong?.playId, meta.value.isPlaying) } }

    val liked = likedOrNull.orEmpty()
    val history = historyOrNull.orEmpty()
    val playlists = playlistsOrNull.orEmpty()
    val openPlaylist = playlists.firstOrNull { it.id == openId }
    BackHandler(enabled = openPlaylist != null) { vm.openPlaylist(null) }
    LaunchedEffect(LibraryLaunch.tab) {
        LibraryLaunch.tab?.let { vm.onTabChange(it); vm.openPlaylist(null); LibraryLaunch.tab = null }
    }
    // A playlist that was deleted while open closes itself.
    LaunchedEffect(openId, playlistsOrNull) { if (openId != null && playlistsOrNull != null && openPlaylist == null) vm.openPlaylist(null) }

    val leadArt = when {
        openPlaylist != null -> openPlaylist.songs.firstOrNull()
        tab == LibraryTab.LIKED -> liked.firstOrNull()
        tab == LibraryTab.RECENT -> history.firstOrNull()
        else -> playlists.firstNotNullOfOrNull { it.songs.firstOrNull() }
    }
    val light = rememberArtworkLight(leadArt?.cover?.ifBlank { leadArt.artworkUrl }, DefaultLight)

    val listState = rememberLazyListState()
    val scrollY = { if (listState.firstVisibleItemIndex > 0) 2_000f else listState.firstVisibleItemScrollOffset.toFloat() }
    val haze = remember { HazeState() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val loaded = likedOrNull != null && historyOrNull != null && playlistsOrNull != null

    // Switching collection (or opening a playlist) starts at the top of it.
    LaunchedEffect(tab, openId) { if (listState.firstVisibleItemIndex > 1) listState.scrollToItem(1) }

    val play = { songs: List<Song>, index: Int, song: Song ->
        // Tapping what's already playing opens the player instead of restarting it.
        if (nowPlaying.playId == song.playId) onOpenPlayer() else PlayerController.playFromList(songs, index)
    }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            AmbientGlow(light = light, scrollY = scrollY)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = statusTop + TopBarHeight, bottom = contentPadding.calculateBottomPadding() + Space.l),
            ) {
                item(key = "title") { LargeTitle(openPlaylist == null, liked.size, history.size, playlists.size) }
                if (openPlaylist != null) {
                    playlistDetail(openPlaylist, light, { nowPlaying }, onBack = { vm.openPlaylist(null) }, play = play)
                } else {
                    item(key = "tabs") {
                        Segmented(tab = tab, onTab = vm::onTabChange, modifier = Modifier.padding(horizontal = Space.gutter).padding(bottom = Space.l))
                    }
                    if (!loaded) {
                        items(6) { RowPlaceholder() }
                    } else when (tab) {
                        LibraryTab.LIKED -> likedTab(liked, light, { nowPlaying }, onGoHome, play)
                        LibraryTab.RECENT -> recentTab(history, { nowPlaying }, onClear = vm::clearHistory, play = { index, song ->
                            // Recently played isn't a playlist: play the song and let radio follow it.
                            if (nowPlaying.playId == song.playId) onOpenPlayer() else PlayerController.playSong(song)
                        }, onGoHome = onGoHome)
                        LibraryTab.PLAYLISTS -> playlistsTab(playlists, onOpen = { vm.openPlaylist(it.id) })
                    }
                }
            }
        }
        TopBar(
            haze = haze,
            statusTop = statusTop,
            scrollY = scrollY,
            title = openPlaylist?.name ?: "Library",
        )
    }
}

// ---------------------------------------------------------------- tabs

private fun LazyListScope.likedTab(
    liked: List<Song>,
    light: ArtworkLight,
    nowPlaying: () -> NowPlaying,
    onGoHome: () -> Unit,
    play: (List<Song>, Int, Song) -> Unit,
) {
    if (liked.isEmpty()) {
        item(key = "liked-empty") {
            EmptyCollection(
                icon = Icons.Rounded.FavoriteBorder,
                title = "Songs you love live here",
                body = "Tap the heart on anything that's playing. Liked songs are also saved for offline listening over Wi-Fi.",
                action = "Find something on Home",
                onAction = onGoHome,
                modifier = Modifier.animateItem(),
            )
        }
        return
    }
    item(key = "liked-hero") {
        CollectionHero(
            title = "Liked songs",
            songs = liked,
            light = light,
            onPlay = { PlayerController.playFromList(liked, 0) },
            onShuffle = { PlayerController.playFromList(liked.shuffled(), 0) },
            modifier = Modifier.animateItem(),
        )
    }
    itemsIndexed(liked, key = { _, s -> "liked-" + s.playId }) { index, s ->
        val np = nowPlaying()
        SongListRow(s, isCurrent = np.playId == s.playId, isPlaying = np.isPlaying, onClick = { play(liked, index, s) }, modifier = Modifier.animateItem())
    }
}

private fun LazyListScope.recentTab(
    history: List<Song>,
    nowPlaying: () -> NowPlaying,
    onClear: () -> Unit,
    play: (Int, Song) -> Unit,
    onGoHome: () -> Unit,
) {
    if (history.isEmpty()) {
        item(key = "recent-empty") {
            EmptyCollection(
                icon = Icons.Rounded.History,
                title = "Nothing played yet",
                body = "Everything you listen to shows up here, newest first, so the good stuff is easy to find again.",
                action = "Start listening",
                onAction = onGoHome,
                modifier = Modifier.animateItem(),
            )
        }
        return
    }
    item(key = "recent-header") {
        Row(
            Modifier.animateItem().fillMaxWidth().padding(start = Space.gutter, end = Space.m, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Recently played", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("${history.size} ${if (history.size == 1) "song" else "songs"} · newest first", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
            }
            ConfirmPill(label = "Clear", confirmLabel = "Clear all?", onConfirm = { onClear(); Toaster.show("Listening history cleared") })
        }
    }
    itemsIndexed(history, key = { _, s -> "recent-" + s.playId }) { index, s ->
        val np = nowPlaying()
        SongListRow(s, isCurrent = np.playId == s.playId, isPlaying = np.isPlaying, onClick = { play(index, s) }, modifier = Modifier.animateItem())
    }
}

private fun LazyListScope.playlistsTab(playlists: List<Playlist>, onOpen: (Playlist) -> Unit) {
    item(key = "pl-new") {
        var naming by remember { mutableStateOf(false) }
        Row(
            Modifier.animateItem().fillMaxWidth().height(76.dp).pressable(onClick = { naming = true }).padding(horizontal = Space.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(58.dp).glass(Radius.cardShape, Glass.Regular), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Text("New playlist", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(start = 16.dp))
        }
        if (naming) NameDialog(title = "New playlist", confirm = "Create", onDismiss = { naming = false }, onConfirm = { SongActions.createPlaylist(it); naming = false })
    }
    if (playlists.isEmpty()) {
        item(key = "pl-empty") {
            EmptyCollection(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = "Make it yours",
                body = "Collect songs for a mood, a trip, a person. Add any song from its ⋮ menu.",
                modifier = Modifier.animateItem(),
            )
        }
        return
    }
    itemsIndexed(playlists, key = { _, p -> "pl-" + p.id }) { _, p ->
        Row(
            Modifier.animateItem().fillMaxWidth().height(76.dp).pressable(onClick = { onOpen(p) }).padding(start = Space.gutter, end = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mosaic(p.songs, size = 58.dp)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(songCount(p.songs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f))
        }
    }
}

private fun LazyListScope.playlistDetail(
    playlist: Playlist,
    light: ArtworkLight,
    nowPlaying: () -> NowPlaying,
    onBack: () -> Unit,
    play: (List<Song>, Int, Song) -> Unit,
) {
    item(key = "pd-back") {
        Row(
            Modifier.animateItem().padding(start = 10.dp, bottom = 6.dp).pressable(onClick = onBack).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = Color.White.copy(alpha = 0.75f))
            Text("Playlists", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
        }
    }
    item(key = "pd-hero-" + playlist.id) {
        CollectionHero(
            title = playlist.name,
            songs = playlist.songs,
            light = light,
            onPlay = { PlayerController.playFromList(playlist.songs, 0) },
            onShuffle = { PlayerController.playFromList(playlist.songs.shuffled(), 0) },
            trailing = { PlaylistMenu(playlist, onDeleted = onBack) },
            modifier = Modifier.animateItem(),
        )
    }
    if (playlist.songs.isEmpty()) {
        item(key = "pd-empty") {
            EmptyCollection(
                icon = Icons.Rounded.MusicNote,
                title = "An empty stage",
                body = "Add songs from their ⋮ menu anywhere in the app — “Add to playlist”.",
                modifier = Modifier.animateItem(),
            )
        }
        return
    }
    itemsIndexed(playlist.songs, key = { _, s -> "pd-" + s.playId }) { index, s ->
        val np = nowPlaying()
        SongListRow(
            s,
            isCurrent = np.playId == s.playId,
            isPlaying = np.isPlaying,
            onClick = { play(playlist.songs, index, s) },
            extra = listOf(SongMenuAction("Remove from this playlist", Icons.Rounded.RemoveCircleOutline, destructive = true) {
                SongActions.removeFromPlaylist(playlist.id, s)
                Toaster.show("Removed from “${playlist.name}”")
            }),
            modifier = Modifier.animateItem(),
        )
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun LargeTitle(showCounts: Boolean, liked: Int, played: Int, playlists: Int) {
    Column(Modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.xs, bottom = Space.m + 2.dp)) {
        Text("Library", style = MaterialTheme.typography.displaySmall, color = Color.White)
        if (showCounts) {
            Text(
                listOf("$liked liked", "$played played", "$playlists ${if (playlists == 1) "playlist" else "playlists"}").joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

/** Three-way glass switch with a lens that springs to the chosen collection. */
@Composable
private fun Segmented(tab: LibraryTab, onTab: (LibraryTab) -> Unit, modifier: Modifier = Modifier) {
    val tabs = LibraryTab.entries
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp).glass(Radius.pill, Glass.Clear).padding(4.dp)) {
        val w = maxWidth / tabs.size
        val x by animateDpAsState(w * tab.ordinal, spring(dampingRatio = 0.75f, stiffness = 480f), label = "segLens")
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(w)
                .fillMaxHeight()
                .clip(Radius.pill)
                .background(Color.White.copy(alpha = 0.95f)),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEach { t ->
                val selected = t == tab
                val color by animateColorAsState(if (selected) Color.Black else Color.White.copy(alpha = 0.75f), Motion.settle(), label = "segText")
                Box(
                    Modifier.weight(1f).fillMaxHeight().pressable(role = androidx.compose.ui.semantics.Role.Tab, onClick = { onTab(t) }),
                    contentAlignment = Alignment.Center,
                ) { Text(t.label, style = MaterialTheme.typography.labelLarge, color = color) }
            }
        }
    }
}

/** A collection's front cover: its artwork mosaic lit from within, name, size, and Play / Shuffle. */
@Composable
private fun CollectionHero(
    title: String,
    songs: List<Song>,
    light: ArtworkLight,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.m, bottom = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mosaic(
            songs,
            size = 136.dp,
            modifier = Modifier.shadow(28.dp, Radius.cardShape, ambientColor = light.key, spotColor = light.key),
        )
        Column(Modifier.weight(1f).padding(start = 18.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 23.sp, letterSpacing = (-0.5).sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(collectionSummary(songs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(14.dp))
            if (songs.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SolidPillButton(label = "Play", icon = Icons.Rounded.PlayArrow, onClick = onPlay)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(40.dp).pressable(onClick = onShuffle).glass(Radius.pill, Glass.Regular),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle play", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    if (trailing != null) {
                        Spacer(Modifier.width(8.dp))
                        trailing()
                    }
                }
            } else if (trailing != null) {
                trailing()
            }
        }
    }
}

/** Up to four covers in a 2×2 grid — one cover if that's all there is, a quiet glyph if none. */
@Composable
private fun Mosaic(songs: List<Song>, size: Dp, modifier: Modifier = Modifier) {
    val covers = songs.map { it.artworkUrl }.filter { it.isNotBlank() }.distinct()
    Box(modifier.size(size).clip(Radius.cardShape).background(Color(0xFF16161A))) {
        when {
            covers.size >= 4 -> Column {
                for (r in 0..1) Row {
                    for (c in 0..1) Artwork(url = covers[r * 2 + c], shape = androidx.compose.ui.graphics.RectangleShape, edge = false, modifier = Modifier.size(size / 2))
                }
            }
            covers.isNotEmpty() -> Artwork(url = covers.first(), shape = Radius.cardShape, modifier = Modifier.size(size))
            else -> Box(
                Modifier.matchParentSize().background(Brush.linearGradient(listOf(BrandViolet.copy(alpha = 0.35f), Color(0xFF16161A)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(size * 0.3f)) }
        }
    }
}

@Composable
private fun PlaylistMenu(playlist: Playlist, onDeleted: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box(Modifier.size(40.dp).pressable(onClick = { open = true }).glass(Radius.pill, Glass.Clear), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MoreHoriz, contentDescription = "Playlist options", tint = Color.White, modifier = Modifier.size(20.dp))
        if (open) {
            Popup(alignment = Alignment.TopEnd, offset = with(density) { IntOffset(0, 44.dp.roundToPx()) }, onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                Column(Modifier.width(220.dp).glass(androidx.compose.foundation.shape.RoundedCornerShape(20.dp), Glass.Regular, tint = Color(0xF2141418)).padding(vertical = 6.dp)) {
                    var confirming by remember { mutableStateOf(false) }
                    LaunchedEffect(confirming) { if (confirming) { delay(3_000); confirming = false } }
                    Row(
                        Modifier.fillMaxWidth().pressable(onClick = {
                            if (confirming) { SongActions.deletePlaylist(playlist.id); Toaster.show("Deleted “${playlist.name}”"); open = false; onDeleted() } else confirming = true
                        }).padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFFF6B61), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(if (confirming) "Tap again to delete" else "Delete playlist", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFFF6B61))
                    }
                }
            }
        }
    }
}

/** A small glass pill that asks once before doing something you can't take back. */
@Composable
private fun ConfirmPill(label: String, confirmLabel: String, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(3_000); armed = false } }
    val tint by animateColorAsState(if (armed) Color(0xFFFF453A).copy(alpha = 0.28f) else Color.Transparent, tween(200), label = "confirmTint")
    Text(
        if (armed) confirmLabel else label,
        style = MaterialTheme.typography.labelLarge,
        color = if (armed) Color(0xFFFFB3AD) else Color.White.copy(alpha = 0.85f),
        modifier = Modifier
            .pressable(onClick = { if (armed) { armed = false; onConfirm() } else armed = true })
            .glass(Radius.pill, Glass.Clear, tint = tint)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun EmptyCollection(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).glass(CircleShape, Glass.Regular), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = Color.Black,
                modifier = Modifier.pressable(onClick = onAction).clip(Radius.pill).background(Color.White).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun RowPlaceholder() {
    Row(Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).clip(Radius.thumbShape).shimmer())
        Column(Modifier.padding(horizontal = 14.dp)) {
            Box(Modifier.size(width = 170.dp, height = 13.dp).clip(Radius.pill).shimmer())
            Spacer(Modifier.height(7.dp))
            Box(Modifier.size(width = 110.dp, height = 11.dp).clip(Radius.pill).shimmer())
        }
    }
}

/** Frosts in as content scrolls beneath it, and carries the title once the large one is gone. */
@Composable
private fun TopBar(haze: HazeState, statusTop: Dp, scrollY: () -> Float, title: String) {
    val density = LocalDensity.current
    val style = remember { HazeStyle(backgroundColor = BgBase, tints = listOf(HazeTint(Color.Black.copy(alpha = 0.58f))), blurRadius = 30.dp, noiseFactor = 0.04f) }
    val solidUntil = with(density) { (statusTop + TopBarHeight - 6.dp).toPx() }
    val fadeEnd = with(density) { (statusTop + TopBarHeight + 16.dp).toPx() }
    val fadeDistance = with(density) { 72.dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(statusTop + TopBarHeight + 16.dp)
            .hazeEffect(haze, style) {
                inputScale = dev.chrisbanes.haze.HazeInputScale.Auto
                progressive = HazeProgressive.verticalGradient(startY = solidUntil, startIntensity = 1f, endY = fadeEnd, endIntensity = 0f, preferPerformance = true)
                alpha = (scrollY() / fadeDistance).coerceIn(0f, 1f)
            },
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusTop + 16.dp)
                .padding(horizontal = 64.dp)
                // Appears as the large title scrolls away beneath it.
                .graphicsLayer { alpha = ((scrollY() - with(density) { 40.dp.toPx() }) / with(density) { 30.dp.toPx() }).coerceIn(0f, 1f) },
        )
    }
}

private fun songCount(songs: List<Song>) = "${songs.size} ${if (songs.size == 1) "song" else "songs"}"

private fun collectionSummary(songs: List<Song>): String {
    if (songs.isEmpty()) return "No songs yet"
    val known = songs.filter { it.duration > 0 }
    if (known.size < songs.size * 0.8) return songCount(songs)
    val minutes = (songs.sumOf { it.duration } / 60).coerceAtLeast(1)
    val length = if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
    return "${songCount(songs)} · $length"
}
