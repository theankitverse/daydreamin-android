package com.daydreamin.app.desktop.shell

import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.desktop.components.ContextMenuArea
import com.daydreamin.app.desktop.components.DButton
import com.daydreamin.app.desktop.components.DIconButton
import com.daydreamin.app.desktop.components.LocalAccent
import com.daydreamin.app.desktop.components.MenuEntry
import com.daydreamin.app.desktop.components.MenuPopup
import com.daydreamin.app.desktop.components.desktopClickable
import com.daydreamin.app.desktop.components.rememberHover
import com.daydreamin.app.desktop.components.songMenuEntries
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.SongActions
import com.daydreamin.app.ui.screens.lyrics.LyricsViewModel
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.rememberArtworkLight
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

private val DefaultLight = ArtworkLight(BrandViolet, Color(0xFF3D3A9E))

/** The panel beside the page: Now playing, the queue, or the lyrics. */
@Composable
fun SidePanel(modifier: Modifier = Modifier) {
    val meta by PlayerController.meta.collectAsState()
    val song = meta.currentSong
    val light = rememberArtworkLight(song?.let { it.cover.ifBlank { it.artworkUrl } }, DefaultLight)
    Panel(modifier) {
        // The song's own light, washing down from the top of the panel.
        Box(Modifier.fillMaxWidth().height(320.dp).background(Brush.verticalGradient(listOf(light.key.copy(alpha = 0.32f), light.fill.copy(alpha = 0.10f), Color.Transparent))))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PanelTab.entries.forEach { tab -> TabChip(tab.label, DesktopUi.panelTab == tab) { DesktopUi.open(tab) } }
                }
                DIconButton(Icons.Rounded.Close, "Close panel", { DesktopUi.close() }, iconSize = 18.dp)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (song == null) {
                    Text("Play something and it shows up here.", style = DType.body, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                } else when (DesktopUi.panelTab) {
                    PanelTab.NOW_PLAYING -> NowPlayingView(song, meta.queue)
                    PanelTab.QUEUE -> QueueView(song, meta.queue)
                    PanelTab.LYRICS -> LyricsView(song)
                }
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    val bg by animateColorAsState(if (selected) Color.White else if (hovered) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.07f), tween(120), label = "tab")
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .combinedClickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = DType.small.copy(fontWeight = FontWeight.SemiBold, color = if (selected) Color.Black else Color.White)) }
}

@Composable
private fun NowPlayingView(song: Song, queue: List<Song>) {
    val likedIds by AppGraph.prefs.likedIds.collectAsState(initial = emptySet())
    val liked = song.playId in likedIds
    val accent = LocalAccent.current
    val nav = LocalNavigator.current
    val meta by PlayerController.meta.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val menu = songMenuEntries(song, onPlay = { PlayerController.togglePlayPause() })
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        ContextMenuArea(menu) {
            Artwork(url = song.cover.ifBlank { song.artworkUrl }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, style = DType.section.copy(fontSize = 20.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                LinkText(song.artist, DType.body) { nav.go(Route.Artist(com.daydreamin.app.data.recommend.Recommender.primaryArtist(song.artist))) }
            }
            DIconButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Remove from Liked songs" else "Save to Liked songs", { SongActions.toggleLiked(song, liked) }, tint = if (liked) accent else null)
            Box {
                DIconButton(Icons.Rounded.MoreHoriz, "More options", { menuOpen = true })
                if (menuOpen) MenuPopup(menu(), onDismiss = { menuOpen = false })
            }
        }
        meta.streamQuality?.let { Text(it, style = DType.small.copy(color = Color.White.copy(alpha = 0.4f)), modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(20.dp))
        queue.firstOrNull()?.let { next ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Next in queue", style = DType.panelTitle, modifier = Modifier.weight(1f))
                    Text("Open queue", style = DType.small.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.desktopClickable { DesktopUi.open(PanelTab.QUEUE) }.padding(4.dp))
                }
                Spacer(Modifier.height(8.dp))
                QueueRow(next, onPlay = { PlayerController.playQueueItemAt(0) }, onRemove = { PlayerController.removeFromQueue(next) }, onTop = null)
            }
        }
    }
}

@Composable
private fun QueueView(song: Song, queue: List<Song>) {
    val state = rememberLazyListState()
    // The row being dragged and how far it has moved.
    var drag by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            item { Text("Now playing", style = DType.panelTitle, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 6.dp)) }
            item { QueueRow(song, current = true, onPlay = { PlayerController.togglePlayPause() }, onRemove = null, onTop = null) }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Next up", style = DType.panelTitle, modifier = Modifier.weight(1f))
                    if (queue.isNotEmpty()) DButton("Clear", { PlayerController.clearQueue() })
                }
            }
            if (queue.isEmpty()) item { Text("Nothing lined up — songs like this one will follow.", style = DType.small, modifier = Modifier.padding(8.dp)) }
            itemsIndexed(queue, key = { _, s -> "q-" + s.playId }) { i, s ->
                // Drag a song up or down the queue; it follows the pointer and drops where it's let go.
                val dragging = drag?.first == i
                Box(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) drag!!.second else 0f; shadowElevation = if (dragging) 16f else 0f }
                        .pointerInput(i, queue.size) {
                            detectDragGestures(
                                onDragStart = { drag = i to 0f },
                                onDrag = { change, amount -> change.consume(); drag = drag?.let { it.first to it.second + amount.y } },
                                onDragEnd = {
                                    drag?.let { (from, dy) -> PlayerController.moveQueueItem(from, (from + (dy / rowPx).roundToInt()).coerceIn(0, queue.lastIndex)) }
                                    drag = null
                                },
                                onDragCancel = { drag = null },
                            )
                        },
                ) {
                    QueueRow(s, onPlay = { PlayerController.playQueueItemAt(i) }, onRemove = { PlayerController.removeFromQueue(s) }, onTop = if (i > 0) ({ PlayerController.moveQueueItem(i, 0) }) else null)
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(song: Song, onPlay: () -> Unit, onRemove: (() -> Unit)?, onTop: (() -> Unit)?, current: Boolean = false) {
    val (source, hovered) = rememberHover()
    val accent = LocalAccent.current
    val extra = buildList {
        if (onTop != null) add(MenuEntry("Move to top of queue", Icons.Rounded.VerticalAlignTop, onClick = onTop))
        if (onRemove != null) add(MenuEntry("Remove from queue", Icons.Rounded.RemoveCircleOutline, onClick = onRemove))
    }
    ContextMenuArea(songMenuEntries(song, onPlay = onPlay, extra = extra)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (hovered) DColors.hover else Color.Transparent)
                .hoverable(source)
                .combinedClickable(interactionSource = source, indication = null, onClick = {}, onDoubleClick = onPlay)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Artwork(url = song.cover.ifBlank { song.artworkUrl }, shape = RoundedCornerShape(4.dp), modifier = Modifier.size(40.dp))
                if (hovered && !current) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.5f)).pointerHoverIcon(PointerIcon.Hand).combinedClickable(onClick = onPlay), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${song.title}", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(song.title, style = DType.itemTitle.copy(color = if (current) accent else Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = DType.itemSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hovered && onRemove != null) DIconButton(Icons.Rounded.Close, "Remove from queue", onRemove, size = 26.dp, iconSize = 16.dp)
        }
    }
}

@Composable
internal fun LyricsView(song: Song) {
    val vm: LyricsViewModel = composeViewModel { LyricsViewModel() }
    val state by vm.state.collectAsState()
    val progress = PlayerController.progress.collectAsState()
    LaunchedEffect(song.playId) { vm.load(song.artist, song.title) }
    val lines = state.syncedLines
    // Only the line changing redraws the lyrics, not every tick of the clock.
    val active by remember(lines) { derivedStateOf { lines.indexOfLast { it.timeMs <= progress.value.positionMs + 250 } } }
    val list = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) list.animateScrollToItem((active - 2).coerceAtLeast(0)) }
    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> Text("Finding the lyrics…", style = DType.body, modifier = Modifier.align(Alignment.Center))
            lines.isNotEmpty() -> LazyColumn(state = list, modifier = Modifier.fillMaxSize().fadeEdges().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 24.dp, bottom = 200.dp)) {
                itemsIndexed(lines) { i, line ->
                    val (source, hovered) = rememberHover()
                    val color = when {
                        i == active -> Color.White
                        hovered -> Color.White.copy(alpha = 0.8f)
                        i < active -> Color.White.copy(alpha = 0.5f)
                        else -> Color.White.copy(alpha = 0.32f)
                    }
                    Text(
                        line.text.ifBlank { "♪" },
                        style = DType.section.copy(fontSize = 19.sp, lineHeight = 26.sp, color = color),
                        modifier = Modifier
                            .fillMaxWidth()
                            .hoverable(source)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .combinedClickableNoIndication { PlayerController.seekTo(line.timeMs) }
                            .padding(vertical = 7.dp),
                    )
                }
            }
            state.plainText != null -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                item { Text(state.plainText!!, style = DType.body.copy(color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, lineHeight = 24.sp)) }
            }
            state.offline -> Text("Lyrics need a connection.", style = DType.body, modifier = Modifier.align(Alignment.Center))
            else -> Text("No lyrics for this one.", style = DType.body, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Lines fade out at the top and bottom of the lyrics instead of being cut off. */
private fun Modifier.fadeEdges() = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = 40.dp.toPx() / size.height
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, edge to Color.Black, 1f - edge to Color.Black, 1f to Color.Transparent),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoIndication(onClick: () -> Unit) = this.then(
    Modifier.combinedClickable(interactionSource = null, indication = null, onClick = onClick),
)
