package com.daydreamin.app.ui.screens.queue

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.EqualizerBars
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.screens.nowplaying.PlayerAtmosphere
import com.daydreamin.app.ui.screens.nowplaying.rememberSmoothPosition
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Motion
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.glass
import com.daydreamin.app.ui.theme.rememberArtworkLight
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DefaultLight = ArtworkLight(key = BrandViolet, fill = Color(0xFF3D3A9E))
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val HeaderHeight = 92.dp
private val RowHeight = 68.dp
private const val ROW = "q:"

/** Something the user just did that one tap can take back. */
private class Undo(val label: String, val restore: () -> Unit)

/**
 * The queue, as a sheet of dark glass that slides up over Now Playing — the player's own
 * artwork light still glowing around and through it. The song playing now sits at the top, lit
 * by its artwork; below it, what's up next, which you can drag into a new order, swipe away,
 * or long-press for more.
 *
 * All changes go through PlayerController's existing queue API (move/remove/clear/jump); the
 * list here only mirrors the queue locally while a drag is in progress.
 */
@Composable
fun QueueScreen(visibility: AnimatedVisibilityScope, onBack: () -> Unit) {
    val meta by PlayerController.meta.collectAsState()
    val progressState = PlayerController.progress.collectAsState()
    val song = meta.currentSong
    val queue = meta.queue
    val light = rememberArtworkLight(song?.cover?.ifBlank { song.artworkUrl }, DefaultLight)
    val position = rememberSmoothPosition(progressState, meta.isPlaying)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    // ---- sheet presence: 0 = up, 1 = off the bottom of the screen
    val hidden = visibility.transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.88f, stiffness = 340f, visibilityThreshold = 0.001f) },
        label = "sheet",
    ) { if (it == EnterExitState.Visible) 0f else 1f }
    val pull = remember { Animatable(0f) }
    val dismissPx = with(density) { 130.dp.toPx() }
    fun release() {
        if (pull.value > dismissPx) onBack() else scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 420f)) }
    }

    // ---- local mirror of the queue, so rows can move under the finger before the engine hears about it
    var local by remember { mutableStateOf(queue) }
    val listState = rememberLazyListState()
    val reorder = remember {
        ReorderState(
            list = listState,
            scope = scope,
            onLocalMove = { fromKey, toKey ->
                val from = local.indexOfFirst { ROW + it.playId == fromKey }
                val to = local.indexOfFirst { ROW + it.playId == toKey }
                if (from >= 0 && to >= 0) {
                    local = local.toMutableList().apply { add(to, removeAt(from)) }
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            onDrop = { key ->
                val playId = key.removePrefix(ROW)
                val engineQueue = PlayerController.meta.value.queue
                val from = engineQueue.indexOfFirst { it.playId == playId }
                val to = local.indexOfFirst { it.playId == playId }
                if (from >= 0 && to >= 0 && from != to) PlayerController.moveQueueItem(from, to.coerceAtMost(engineQueue.lastIndex))
            },
        )
    }
    LaunchedEffect(queue) { if (reorder.key == null) local = queue }

    var undo by remember { mutableStateOf<Undo?>(null) }
    LaunchedEffect(undo) { if (undo != null) { delay(6_000); undo = null } }
    fun remove(s: Song) {
        val index = PlayerController.meta.value.queue.indexOfFirst { it.playId == s.playId }
        PlayerController.removeFromQueue(s)
        undo = Undo("Removed “${s.title}”") {
            PlayerController.addToQueue(s)
            val q = PlayerController.meta.value.queue
            val at = q.indexOfFirst { it.playId == s.playId }
            if (at >= 0 && index in q.indices && at != index) PlayerController.moveQueueItem(at, index)
        }
    }
    fun clearAll() {
        val snapshot = PlayerController.meta.value.queue
        if (snapshot.isEmpty()) return
        PlayerController.clearQueue()
        undo = Undo("Cleared ${snapshot.size} ${if (snapshot.size == 1) "song" else "songs"}") { snapshot.forEach(PlayerController::addToQueue) }
    }

    val headerHaze = remember { HazeState() }
    val headerStyle = remember {
        HazeStyle(backgroundColor = Color(0xFF0B0B0D), tints = listOf(HazeTint(Color.Black.copy(alpha = 0.45f))), blurRadius = 24.dp, noiseFactor = 0.04f)
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Pulling down on the list once it's already at the top drags the whole sheet, like any sheet.
    val sheetPull = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pull.value > 0f && available.y < 0f && reorder.key == null) {
                    val used = maxOf(available.y, -pull.value)
                    scope.launch { pull.snapTo(pull.value + used) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput && reorder.key == null) {
                    scope.launch { pull.snapTo(pull.value + available.y * 0.9f) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull.value > 0f) { release(); return available }
                return Velocity.Zero
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenPx = constraints.maxHeight.toFloat()
        // The room: the same artwork light as the player, dimmed a step as the sheet comes up.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - hidden.value }) {
            PlayerAtmosphere(song, light, alpha = { 1f })
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 10.dp)
                .graphicsLayer { translationY = pull.value + hidden.value * screenPx }
                .glass(SheetShape, Glass.Clear, tint = Color(0xE00B0B0D)),
        ) {
            Box(Modifier.fillMaxSize().hazeSource(headerHaze)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(sheetPull),
                    contentPadding = PaddingValues(top = HeaderHeight, bottom = navBottom + 96.dp),
                ) {
                    item(key = "now") {
                        if (song != null) {
                            Overline("Playing now")
                            NowCard(
                                song = song,
                                isPlaying = meta.isPlaying,
                                light = light,
                                progress = {
                                    val d = progressState.value.durationMs
                                    if (d > 0) position.value.toFloat() / d else 0f
                                },
                                onClick = onBack,
                            )
                        }
                    }
                    item(key = "next-title") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 26.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Up next", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                            AnimatedVisibility(visible = local.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                                Text(
                                    "Clear",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .pressable(onClick = ::clearAll)
                                        .glass(Radius.pill, Glass.Clear)
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                    if (local.isEmpty()) {
                        item(key = "empty") {
                            EmptyQueue(nothingPlaying = song == null, modifier = Modifier.animateItem())
                        }
                    }
                    itemsIndexed(local, key = { _, s -> ROW + s.playId }) { index, s ->
                        val key = ROW + s.playId
                        val isDragged = reorder.key == key
                        QueueRow(
                            song = s,
                            dragged = isDragged,
                            offsetY = { reorder.offsetFor(key) },
                            modifier = Modifier
                                .zIndex(if (isDragged || reorder.settlingKey == key) 1f else 0f)
                                .then(
                                    // The lifted row follows the finger; everything else glides out of its way.
                                    if (isDragged || reorder.settlingKey == key) Modifier
                                    else Modifier.animateItem(
                                        fadeInSpec = tween(220),
                                        placementSpec = spring(dampingRatio = 0.82f, stiffness = 520f, visibilityThreshold = IntOffset(1, 1)),
                                        fadeOutSpec = tween(160),
                                    ),
                                ),
                            onClick = { PlayerController.playQueueItemAt(PlayerController.meta.value.queue.indexOfFirst { it.playId == s.playId }) },
                            onRemove = { remove(s) },
                            onPlayNext = {
                                val q = PlayerController.meta.value.queue
                                val at = q.indexOfFirst { it.playId == s.playId }
                                if (at > 0) PlayerController.moveQueueItem(at, 0)
                            },
                            onMoveToEnd = {
                                val q = PlayerController.meta.value.queue
                                val at = q.indexOfFirst { it.playId == s.playId }
                                if (at >= 0 && at != q.lastIndex) PlayerController.moveQueueItem(at, q.lastIndex)
                            },
                            handle = Modifier.pointerInput(key) {
                                detectDragGestures(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        reorder.start(key)
                                    },
                                    onDrag = { change, amount -> change.consume(); reorder.drag(amount.y) },
                                    onDragEnd = { reorder.end() },
                                    onDragCancel = { reorder.end() },
                                )
                            },
                            isFirst = index == 0,
                        )
                    }
                }
            }

            // Header: floats over the list; frosts what scrolls under it.
            val scrolled by animateFloatAsState(
                if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4) 1f else 0f,
                tween(220),
                label = "headerFrost",
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HeaderHeight)
                    .hazeEffect(headerHaze, headerStyle) {
                        progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f, startY = with(density) { 60.dp.toPx() }, endY = with(density) { HeaderHeight.toPx() }, preferPerformance = true)
                        alpha = scrolled
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dy -> change.consume(); scope.launch { pull.snapTo((pull.value + dy).coerceAtLeast(0f)) } },
                            onDragEnd = ::release,
                            onDragCancel = ::release,
                        )
                    },
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 5.dp).clip(Radius.pill).background(Color.White.copy(alpha = 0.3f)))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundGlass(Icons.Rounded.ExpandMore, "Close", onBack)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Queue", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, letterSpacing = (-0.6).sp), color = Color.White)
                        Text(queueSummary(local), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                    }
                    ShuffleToggle(on = meta.shuffle, enabled = local.size > 1, onClick = PlayerController::toggleShuffle)
                }
            }

            UndoNote(
                undo = undo,
                onUndo = { undo?.restore?.invoke(); undo = null },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun Overline(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 24.dp, top = 6.dp, bottom = 10.dp),
    )
}

/**
 * The song playing now: lit by its own artwork (a wash of its key and fill light through the
 * glass) rather than painted in the accent color. Tapping it goes back to the player.
 */
@Composable
private fun NowCard(song: Song, isPlaying: Boolean, light: ArtworkLight, progress: () -> Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .clip(Radius.panelShape)
            .background(Brush.linearGradient(listOf(light.key.copy(alpha = 0.22f), light.fill.copy(alpha = 0.08f))))
            .glass(Radius.panelShape, Glass.Regular),
    ) {
        AnimatedContent(
            targetState = song,
            contentKey = { it.playId },
            transitionSpec = {
                (fadeIn(tween(300, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 4 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 4 })
            },
            label = "nowCard",
        ) { s ->
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(url = s.artworkUrl, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(64.dp))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(s.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.66f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                EqualizerBars(playing = isPlaying, color = Color.White, modifier = Modifier.padding(end = 6.dp))
            }
        }
        // Where you are in the song, as a hairline along the card's lower edge.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(2.dp)
                .drawBehind {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(Color.White.copy(alpha = 0.14f), cornerRadius = r)
                    val w = size.width * progress().coerceIn(0f, 1f)
                    if (w > 0f) drawRoundRect(Color.White.copy(alpha = 0.8f), Offset.Zero, Size(w, size.height), r)
                },
        )
    }
}

/**
 * One upcoming song. Tap to play it, drag the handle to reorder, swipe left to remove, long-press
 * for a small glass menu. While lifted it rises a little, gains depth and a brighter glass skin.
 */
@Composable
private fun QueueRow(
    song: Song,
    dragged: Boolean,
    offsetY: () -> Float,
    modifier: Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: () -> Unit,
    onMoveToEnd: () -> Unit,
    handle: Modifier,
    isFirst: Boolean,
) {
    val scope = rememberCoroutineScope()
    val lift by animateFloatAsState(if (dragged) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")
    val swipe = remember(song.playId) { Animatable(0f) }
    var menuOpen by remember { mutableStateOf(false) }
    var rowWidth by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .padding(horizontal = 10.dp)
            .graphicsLayer {
                translationY = offsetY()
                val s = 1f + 0.035f * lift
                scaleX = s; scaleY = s
                shadowElevation = with(density) { 18.dp.toPx() } * lift
                shape = Radius.cardShape
                clip = false
            },
    ) {
        // Revealed as the row slides left: a soft red wash, then the remove glyph.
        val reveal = { (-swipe.value / (rowWidth * 0.35f)).coerceIn(0f, 1f) }
        Box(
            Modifier
                .matchParentSize()
                .clip(Radius.cardShape)
                .drawBehind { drawRect(Color(0xFFFF453A).copy(alpha = 0.22f * reveal())) },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(end = 22.dp).size(24.dp).graphicsLayer { alpha = reveal(); val s = 0.7f + 0.3f * reveal(); scaleX = s; scaleY = s },
            )
        }
        Row(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { translationX = swipe.value; rowWidth = size.width.coerceAtLeast(1f) }
                .clip(Radius.cardShape)
                // The lifted row is the only one on glass — it's the thing in your hand.
                .then(if (lift > 0.01f) Modifier.graphicsLayer { alpha = 1f }.glass(Radius.cardShape, Glass.Frosted, tint = Color(0xFF1A1A1F).copy(alpha = 0.92f * lift)) else Modifier)
                .pointerInput(song.playId) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch { swipe.snapTo((swipe.value + dx).coerceIn(-size.width.toFloat(), 0f)) }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (swipe.value < -size.width * 0.35f) {
                                    swipe.animateTo(-size.width.toFloat(), tween(180))
                                    onRemove()
                                } else {
                                    swipe.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                    )
                }
                .pressable(onLongClick = { menuOpen = true }, onClick = onClick)
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(url = song.artworkUrl, shape = Radius.thumbShape, modifier = Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(song.title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .semantics { contentDescription = "Reorder ${song.title}" }
                    .then(handle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.DragHandle, contentDescription = null, tint = Color.White.copy(alpha = 0.35f + 0.55f * lift), modifier = Modifier.size(22.dp))
            }
        }
        if (menuOpen) {
            RowMenu(
                isFirst = isFirst,
                onDismiss = { menuOpen = false },
                onPlayNext = { onPlayNext(); menuOpen = false },
                onMoveToEnd = { onMoveToEnd(); menuOpen = false },
                onRemove = { onRemove(); menuOpen = false },
            )
        }
    }
}

@Composable
private fun RowMenu(isFirst: Boolean, onDismiss: () -> Unit, onPlayNext: () -> Unit, onMoveToEnd: () -> Unit, onRemove: () -> Unit) {
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(-16.dp.roundToPx(), 56.dp.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(220.dp)
                .glass(RoundedCornerShape(20.dp), Glass.Regular, tint = Color(0xF2141418))
                .padding(vertical = 6.dp),
        ) {
            if (!isFirst) MenuItem(Icons.Rounded.SkipNext, "Play next", onPlayNext)
            MenuItem(Icons.Rounded.VerticalAlignBottom, "Move to end", onMoveToEnd)
            MenuItem(Icons.Rounded.DeleteOutline, "Remove from queue", onRemove, tint = Color(0xFFFF6B61))
        }
    }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.White) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun RoundGlass(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).pressable(onClick = onClick).glass(Radius.pill, Glass.Clear),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

/** Same glass-lens toggle as the player's action row. */
@Composable
private fun ShuffleToggle(on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val lens by animateFloatAsState(if (on) 1f else 0f, Motion.settle(), label = "shuffleLens")
    Box(
        Modifier
            .size(width = 52.dp, height = 40.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = lens }.glass(Radius.pill, Glass.Regular))
        Icon(
            Icons.Rounded.Shuffle,
            contentDescription = if (on) "Shuffle on" else "Shuffle off",
            tint = Color.White.copy(alpha = 0.6f + 0.4f * lens),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun EmptyQueue(nothingPlaying: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).glass(CircleShape, Glass.Regular), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(if (nothingPlaying) "Nothing playing" else "Nothing up next", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(
            if (nothingPlaying) "Play something from Home and your queue will build itself from there."
            else "Playback will stop after this song. Start any song from Home for a fresh radio mix.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.58f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UndoNote(undo: Undo?, onUndo: () -> Unit, modifier: Modifier) {
    var shown by remember { mutableStateOf<Undo?>(null) }
    if (undo != null) shown = undo
    AnimatedVisibility(
        visible = undo != null,
        enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
        modifier = modifier,
    ) {
        Row(
            Modifier
                .padding(horizontal = 24.dp)
                .glass(Radius.pill, Glass.Frosted, tint = Color(0xE6141418))
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                shown?.label.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Undo",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Black,
                modifier = Modifier
                    .pressable(onClick = onUndo)
                    .clip(Radius.pill)
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private fun queueSummary(queue: List<Song>): String {
    if (queue.isEmpty()) return "Nothing up next"
    val count = "${queue.size} ${if (queue.size == 1) "song" else "songs"}"
    val known = queue.filter { it.duration > 0 }
    // Only quote a total when we actually know most of the lengths — a guess reads as a fact.
    if (known.size < queue.size * 0.8) return count
    val minutes = (queue.sumOf { it.duration } / 60).coerceAtLeast(1)
    val length = if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
    return "$count · $length"
}

// ---------------------------------------------------------------- drag to reorder

/**
 * Drag-to-reorder over a LazyColumn. The lifted row is drawn at the finger (its layout slot moves
 * underneath it as it crosses neighbours, which is what makes the neighbours glide aside via
 * animateItem), the list scrolls on its own near the edges, and on release the row springs from
 * wherever it is into its new slot.
 */
private class ReorderState(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val onLocalMove: (fromKey: String, toKey: String) -> Unit,
    private val onDrop: (key: String) -> Unit,
) {
    var key by mutableStateOf<String?>(null)
        private set
    var settlingKey by mutableStateOf<String?>(null)
        private set
    private var initialOffset = 0
    private var delta by mutableFloatStateOf(0f)
    private val settle = Animatable(0f)
    private var autoScroll: Job? = null

    private fun info(k: Any?) = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == k }

    fun offsetFor(k: String): Float = when (k) {
        key -> info(k)?.let { initialOffset + delta - it.offset } ?: 0f
        settlingKey -> settle.value
        else -> 0f
    }

    fun start(k: String) {
        val i = info(k) ?: return
        key = k
        initialOffset = i.offset
        delta = 0f
        autoScroll?.cancel()
        autoScroll = scope.launch {
            while (key != null) {
                withFrameNanos { }
                val cur = info(key) ?: continue
                val top = initialOffset + delta
                val bottom = top + cur.size
                val edge = cur.size * 0.9f
                val viewEnd = list.layoutInfo.viewportEndOffset - list.layoutInfo.afterContentPadding
                val speed = when {
                    top < edge -> -((edge - top) / edge).coerceIn(0f, 1f) * 22f
                    bottom > viewEnd - edge -> ((bottom - (viewEnd - edge)) / edge).coerceIn(0f, 1f) * 22f
                    else -> 0f
                }
                if (speed != 0f) {
                    list.scrollBy(speed)
                    checkSwap()
                }
            }
        }
    }

    fun drag(dy: Float) {
        delta += dy
        checkSwap()
    }

    private fun checkSwap() {
        val k = key ?: return
        val cur = info(k) ?: return
        val mid = initialOffset + delta + cur.size / 2f
        val target = list.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != k && (it.key as? String)?.startsWith(ROW) == true && mid > it.offset && mid < it.offset + it.size
        } ?: return
        // LazyColumn anchors its scroll position to the first visible item; if that's one of the
        // two swapping, pin the position so the list doesn't jump with it.
        val firstIndex = list.firstVisibleItemIndex
        val firstOffset = list.firstVisibleItemScrollOffset
        val pin = target.index == firstIndex || cur.index == firstIndex
        onLocalMove(k, target.key as String)
        if (pin) scope.launch { list.scrollToItem(firstIndex, firstOffset) }
    }

    fun end() {
        val k = key ?: return
        val from = offsetFor(k)
        autoScroll?.cancel()
        key = null
        settlingKey = k
        onDrop(k)
        scope.launch {
            settle.snapTo(from)
            settle.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 520f))
            if (settlingKey == k) settlingKey = null
        }
    }
}
