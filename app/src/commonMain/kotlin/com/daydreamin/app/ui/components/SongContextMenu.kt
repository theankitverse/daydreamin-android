package com.daydreamin.app.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.Radius
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.roundToInt

/**
 * The long-press menu: the song lifts off the page, everything behind it blurs and dims, and its
 * menu springs open beside it. Songs ask for it with [show]; [SongContextMenuHost] (once, over the
 * whole app) draws it.
 */
object SongContextMenu {
    class Request(val song: Song, val anchor: Rect, val extra: List<SongMenuAction>)

    var request by mutableStateOf<Request?>(null)
        private set

    /** Opens the menu for [song], lifting it from [anchor] (where it sits, in window coordinates). */
    fun show(song: Song, anchor: Rect, extra: List<SongMenuAction> = emptyList()) {
        request = Request(song, anchor, extra)
    }

    fun dismiss() {
        request = null
    }
}

/** Where a song sits on screen, kept up to date by [contextAnchor] so its menu can lift it from there. */
class ContextAnchor {
    var bounds: Rect = Rect.Zero
}

@Composable
fun rememberContextAnchor() = remember { ContextAnchor() }

fun Modifier.contextAnchor(anchor: ContextAnchor): Modifier = onGloballyPositioned { anchor.bounds = it.boundsInWindow() }

@Composable
fun SongContextMenuHost(hazeState: HazeState) {
    val request = SongContextMenu.request
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = request != null
    // The last request stays on screen while the menu closes.
    val last = remember { arrayOfNulls<SongContextMenu.Request>(1) }
    if (request != null) last[0] = request
    if (visible.isIdle && !visible.currentState) { last[0] = null; return }
    val shown = last[0] ?: return

    val transition = rememberTransition(visible, label = "contextMenu")
    val p by transition.animateFloat(
        transitionSpec = { if (targetState) spring(dampingRatio = 0.78f, stiffness = 420f) else tween(170) },
        label = "lift",
    ) { if (it) 1f else 0f }
    com.daydreamin.app.platform.BackHandler(enabled = request != null) { SongContextMenu.dismiss() }

    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val blurStyle = remember { HazeStyle(backgroundColor = BgBase, tints = emptyList(), blurRadius = 24.dp, noiseFactor = 0f) }

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val topSafe = WindowInsets.systemBars.getTop(density) + with(density) { 8.dp.toPx() }
        val bottomSafe = height - WindowInsets.systemBars.getBottom(density) - with(density) { 8.dp.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val edge = with(density) { 16.dp.toPx() }

        // Behind: the page, blurred and dimmed. A tap anywhere on it closes the menu.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p }
                .then(if (HazeDefaults.blurEnabled()) Modifier.hazeEffect(hazeState, blurStyle) else Modifier)
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.45f)) }
                .pointerInput(Unit) { detectTapGestures { SongContextMenu.dismiss() } },
        )

        // The song, lifted where it was.
        val anchor = shown.anchor.translate(-origin)
        val card = anchor.height > with(density) { 120.dp.toPx() }
        Box(
            Modifier
                .offset { IntOffset(anchor.left.roundToInt(), anchor.top.roundToInt()) }
                .size(with(density) { anchor.width.toDp() }, with(density) { anchor.height.toDp() })
                .graphicsLayer {
                    val s = 1f + 0.04f * p
                    scaleX = s; scaleY = s
                    shadowElevation = 22.dp.toPx() * p
                    shape = RoundedCornerShape(16.dp)
                    clip = true
                }
                .background(Color(0xFF1B1B22).copy(alpha = p)),
        ) { LiftedSong(shown.song, card) }

        // The menu: under the song if it fits, over it if not.
        val below = anchor.bottom + gap + menuSize.height <= bottomSafe
        val menuX = if (card) anchor.left else anchor.right - menuSize.width
        val x = menuX.coerceIn(edge, (width - menuSize.width - edge).coerceAtLeast(edge))
        val y = if (below) anchor.bottom + gap else (anchor.top - gap - menuSize.height).coerceAtLeast(topSafe)
        SongMenuCard(
            song = shown.song,
            extra = shown.extra,
            onDismiss = { SongContextMenu.dismiss() },
            showSong = false,
            modifier = Modifier
                .onSizeChanged { menuSize = it }
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .graphicsLayer {
                    // Grows out of the corner nearest the song.
                    val fromRight = !card && x + menuSize.width >= anchor.right - 1f
                    transformOrigin = TransformOrigin(if (fromRight) 1f else 0f, if (below) 0f else 1f)
                    val s = 0.6f + 0.4f * p
                    scaleX = s; scaleY = s
                    alpha = (p * 1.4f).coerceIn(0f, 1f)
                },
        )
    }
}

/** The lifted song: a row (artwork, title, artist), or for a card on Home, its artwork with the title under it. */
@Composable
private fun LiftedSong(song: Song, card: Boolean) {
    val art = song.cover.ifBlank { song.artworkUrl }
    if (card) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Artwork(url = art, shape = Radius.cardShape, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            Text(song.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(url = art, shape = Radius.thumbShape, modifier = Modifier.size(48.dp))
            Column(Modifier.padding(start = 14.dp)) {
                Text(song.title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
