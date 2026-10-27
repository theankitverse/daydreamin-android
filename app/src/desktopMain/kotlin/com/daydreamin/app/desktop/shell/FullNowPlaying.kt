package com.daydreamin.app.desktop.shell

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.recommend.Recommender
import com.daydreamin.app.desktop.DesktopSettings
import com.daydreamin.app.desktop.components.ContextMenuArea
import com.daydreamin.app.desktop.components.DIconButton
import com.daydreamin.app.desktop.components.LocalAccent
import com.daydreamin.app.desktop.components.MenuPopup
import com.daydreamin.app.desktop.components.desktopClickable
import com.daydreamin.app.desktop.components.rememberHover
import com.daydreamin.app.desktop.components.rememberLiked
import com.daydreamin.app.desktop.components.songMenuEntries
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.platform.BackHandler
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.SongActions
import com.daydreamin.app.ui.screens.lyrics.LyricsViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

/**
 * Now playing filling the window, for listening rather than browsing — Apple Music's lyrics view:
 * the artwork large on the left with the song, Liked and a waveform timeline; on the right the
 * lyrics, big, following the song (or the queue). The window glows in the artwork's colours behind
 * it ([com.daydreamin.app.desktop.components.WindowBackdrop]). Esc, the corner button, or the
 * artwork / lyrics button in the player bar go back.
 */
@Composable
fun FullNowPlaying(modifier: Modifier = Modifier) {
    val meta by PlayerController.meta.collectAsState()
    val song = meta.currentSong
    BackHandler(enabled = true) { DesktopUi.expanded = false }
    if (song == null) { LaunchedEffect(Unit) { DesktopUi.expanded = false }; return }
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 40.dp)) {
        val wide = maxWidth > 900.dp
        if (wide) {
            val art = min(maxHeight * 0.56f, maxWidth * 0.3f).coerceAtLeast(200.dp)
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.42f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    SongColumn(song, art)
                }
                Column(Modifier.weight(0.58f).fillMaxHeight().padding(start = 24.dp)) {
                    ViewSwitch(Modifier.padding(top = 8.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) { FullContent(song, meta.queue) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                SongHeader(song)
                ViewSwitch(Modifier.padding(top = 12.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) { FullContent(song, meta.queue) }
            }
        }
    }
}

@Composable
private fun FullContent(song: Song, queue: List<Song>) {
    Crossfade(DesktopUi.fullView, animationSpec = tween(220), label = "full-view") { view ->
        when (view) {
            FullView.LYRICS -> AppleLyrics(song, Modifier.fillMaxSize())
            FullView.QUEUE -> Box(Modifier.fillMaxSize().padding(top = 12.dp)) { QueueView(song, queue) }
        }
    }
}

/** "Lyrics | Queue" as one pill, and the way back to the app. */
@Composable
private fun ViewSwitch(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(3.dp)) {
            FullView.entries.forEach { v ->
                val selected = DesktopUi.fullView == v
                val bg by animateColorAsState(if (selected) Color.White.copy(alpha = 0.9f) else Color.Transparent, tween(160), label = "seg")
                Box(
                    Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .desktopClickable(RoundedCornerShape(50), highlight = !selected) { DesktopUi.fullView = v }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(v.label, style = DType.small.copy(fontWeight = FontWeight.SemiBold, color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f)))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        DIconButton(Icons.Rounded.CloseFullscreen, "Back to the app", { DesktopUi.expanded = false }, shortcut = "Esc")
    }
}

/** The left half: big artwork, the song, Liked and more, and the waveform timeline. */
@Composable
private fun SongColumn(song: Song, art: Dp) {
    val nav = LocalNavigator.current
    val menu = songMenuEntries(song, onPlay = { PlayerController.togglePlayPause() })
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(art)) {
        ContextMenuArea(menu) {
            Artwork(
                url = song.cover.ifBlank { song.artworkUrl },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(art).shadow(48.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color.Black),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(song.title, style = DType.section.copy(fontSize = 24.sp, lineHeight = 30.sp, color = Color.White), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        LinkText(song.artist, DType.body.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))) {
            DesktopUi.expanded = false
            nav.go(Route.Artist(Recommender.primaryArtist(song.artist)))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LikePill(song)
            MoreButton(menu)
        }
        Spacer(Modifier.height(22.dp))
        Waveform(song, Modifier.fillMaxWidth().height(34.dp))
    }
}

/** On a narrow window: the song as a row along the top. */
@Composable
private fun SongHeader(song: Song) {
    val menu = songMenuEntries(song, onPlay = { PlayerController.togglePlayPause() })
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(url = song.cover.ifBlank { song.artworkUrl }, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(96.dp).shadow(24.dp, RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text(song.title, style = DType.section.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = DType.body.copy(color = Color.White.copy(alpha = 0.7f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Waveform(song, Modifier.widthIn(max = 360.dp).fillMaxWidth().height(26.dp))
        }
        LikePill(song)
        Spacer(Modifier.width(6.dp))
        MoreButton(menu)
    }
}

@Composable
private fun LikePill(song: Song) {
    val liked = rememberLiked(song)
    val accent = LocalAccent.current
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .desktopClickable(RoundedCornerShape(50)) { SongActions.toggleLiked(song, liked) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null, tint = if (liked) accent else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (liked) "In Liked songs" else "Add to Liked", style = DType.small.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
    }
}

@Composable
private fun MoreButton(menu: () -> List<com.daydreamin.app.desktop.components.MenuEntry?>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            DIconButton(Icons.Rounded.MoreHoriz, "More options", { open = true }, iconSize = 18.dp)
        }
        if (open) MenuPopup(menu(), onDismiss = { open = false })
    }
}

/**
 * The timeline as a waveform: bars (made up from the song, the same every time it plays) light up
 * as it plays. Click or drag along it to move through the song.
 */
@Composable
private fun Waveform(song: Song, modifier: Modifier) {
    val progress by PlayerController.progress.collectAsState()
    val bars = remember(song.playId) {
        val r = Random(song.playId.hashCode())
        val raw = FloatArray(64) { 0.2f + r.nextFloat() * 0.8f }
        // Neighbours averaged, so it rises and falls like sound rather than noise.
        FloatArray(raw.size) { i -> (raw[(i - 1).coerceAtLeast(0)] + raw[i] * 2 + raw[(i + 1).coerceAtMost(raw.lastIndex)]) / 4f }
    }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = progress.durationMs
    val fraction = dragging ?: if (duration > 0) (progress.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val seek = { f: Float -> if (duration > 0) PlayerController.seekTo((f.coerceIn(0f, 1f) * duration).toLong()) }
    Canvas(
        modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(duration) { detectTapGestures { seek(it.x / size.width) } }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = it.x / size.width },
                    onHorizontalDrag = { change, _ -> change.consume(); dragging = (change.position.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging?.let(seek); dragging = null },
                    onDragCancel = { dragging = null },
                )
            },
    ) {
        val step = size.width / bars.size
        val barWidth = step * 0.55f
        bars.forEachIndexed { i, h ->
            val x = i * step + (step - barWidth) / 2
            val barHeight = size.height * h
            val played = (i + 0.5f) / bars.size <= fraction
            drawRoundRect(
                color = if (played) Color.White else Color.White.copy(alpha = 0.3f),
                topLeft = Offset(x, (size.height - barHeight) / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

/**
 * The lyrics, Apple Music style: big and bold, the line being sung bright and full size, the others
 * dimmer, smaller and softly blurred the further they are from it, the whole page gliding up line by
 * line. Scrolling with the mouse brings every line into focus to pick one (click to jump there); it
 * picks up the song again a few seconds later.
 */
@Composable
private fun AppleLyrics(song: Song, modifier: Modifier) {
    val vm: LyricsViewModel = composeViewModel { LyricsViewModel() }
    val state by vm.state.collectAsState()
    val progress = PlayerController.progress.collectAsState()
    LaunchedEffect(song.playId) { vm.load(song.artist, song.title) }
    val lines = state.syncedLines
    // Only the line changing recomposes the lyrics, not every tick of the clock.
    val active by remember(lines) { derivedStateOf { lines.indexOfLast { it.timeMs <= progress.value.positionMs + 300 } } }
    val list = rememberLazyListState()
    var scrolledAt by remember { mutableLongStateOf(0L) }
    var browsing by remember { mutableStateOf(false) }
    LaunchedEffect(scrolledAt) { if (scrolledAt > 0) { browsing = true; delay(3_500); browsing = false } }
    val size = DesktopSettings.lyricsSize.fontSize
    val style = TextStyle(fontSize = size, lineHeight = size * 1.18f, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).sp * size.value, color = Color.White)

    BoxWithConstraints(modifier) {
        // The line being sung sits about a quarter of the way down.
        val anchor = maxHeight * 0.24f
        LaunchedEffect(active, lines, browsing) {
            if (browsing || lines.isEmpty()) return@LaunchedEffect
            val target = active.coerceAtLeast(0)
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            // Close by: glide there. Far (a seek, the first look): jump.
            if (item != null) list.animateScrollBy(item.offset.toFloat(), spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow))
            else list.scrollToItem(target)
        }
        when {
            state.loading -> Message("Finding the lyrics…")
            lines.isNotEmpty() -> LazyColumn(
                state = list,
                modifier = Modifier
                    .fillMaxSize()
                    .fadeEdges()
                    .onPointerEvent(PointerEventType.Scroll) { scrolledAt = System.nanoTime() },
                contentPadding = PaddingValues(top = anchor, bottom = maxHeight * 0.6f),
            ) {
                itemsIndexed(lines) { i, line ->
                    LyricLine(
                        text = line.text,
                        style = style,
                        state = when { i == active -> LineState.SUNG; i < active -> LineState.PAST; else -> LineState.AHEAD },
                        blur = if (!DesktopSettings.lyricsBlur || browsing || i == active) 0.dp else (abs(i - active).coerceAtMost(4) * 1.4f).dp,
                        onClick = { PlayerController.seekTo(line.timeMs); browsing = false },
                    )
                }
            }
            state.plainText != null -> LazyColumn(Modifier.fillMaxSize().fadeEdges(), contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp)) {
                item { Text("These lyrics aren't timed to the song.", style = DType.small.copy(color = Color.White.copy(alpha = 0.55f)), modifier = Modifier.padding(start = 12.dp, bottom = 16.dp)) }
                itemsIndexed(state.plainText!!.split("\n\n")) { _, verse ->
                    Text(verse.trim(), style = style.copy(fontSize = size * 0.72f, lineHeight = size * 0.72f * 1.3f, color = Color.White.copy(alpha = 0.85f)), modifier = Modifier.widthIn(max = 760.dp).padding(horizontal = 12.dp, vertical = 14.dp))
                }
            }
            state.offline -> Message("Lyrics need a connection.")
            else -> Message("No lyrics for this one.\nEnjoy the music.")
        }
    }
}

private enum class LineState { PAST, SUNG, AHEAD }

@Composable
private fun LyricLine(text: String, style: TextStyle, state: LineState, blur: Dp, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    val sung = state == LineState.SUNG
    val alpha by animateFloatAsState(
        when { sung -> 1f; hovered -> 0.75f; state == LineState.PAST -> 0.42f; else -> 0.3f },
        tween(420), label = "line-alpha",
    )
    val scale by animateFloatAsState(if (sung) 1f else 0.95f, spring(dampingRatio = 0.8f, stiffness = 140f), label = "line-scale")
    val soft by animateDpAsState(if (hovered) 0.dp else blur, tween(420), label = "line-blur")
    val instrumental = text.isBlank()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            if (instrumental) "•  •  •" else text,
            style = style,
            modifier = Modifier
                .widthIn(max = 820.dp)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    this.alpha = alpha
                }
                .then(if (soft > 0.2.dp) Modifier.blur(soft, BlurredEdgeTreatment.Unbounded) else Modifier),
        )
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Text(text, style = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.55f)), modifier = Modifier.padding(start = 12.dp))
    }
}

/** Lines fade out at the top and bottom instead of being cut off. */
private fun Modifier.fadeEdges() = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val top = 56.dp.toPx() / size.height
        val bottom = 120.dp.toPx() / size.height
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, top to Color.Black, 1f - bottom to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }
