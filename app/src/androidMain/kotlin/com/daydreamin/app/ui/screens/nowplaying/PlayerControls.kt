package com.daydreamin.app.ui.screens.nowplaying

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.player.PlaybackProgress
import com.daydreamin.app.player.RepeatMode as PlayerRepeatMode
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Motion
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The player reports its position about twice a second — fine for a clock, visibly steppy for a
 * progress bar or karaoke-timed lyrics. Between reports this extrapolates from the last one using
 * the real clock, so the UI moves every frame while playing. Pure UI; the player is the source of truth.
 */
@Composable
fun rememberSmoothPosition(progress: State<PlaybackProgress>, isPlaying: Boolean): State<Long> {
    // The reports are watched from an effect, never read during composition: a read here would
    // recompose the whole calling screen on every tick — twice a second, mid-gesture included.
    val initial = remember { androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { progress.value.positionMs } }
    val smooth = remember { mutableLongStateOf(initial) }
    val anchor = remember { longArrayOf(initial, SystemClock.elapsedRealtime()) }
    val playing by androidx.compose.runtime.rememberUpdatedState(isPlaying)
    LaunchedEffect(progress) {
        androidx.compose.runtime.snapshotFlow { progress.value.positionMs }.collect { reported ->
            anchor[0] = reported
            anchor[1] = SystemClock.elapsedRealtime()
            if (!playing) smooth.longValue = reported
        }
    }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) { smooth.longValue = anchor[0]; return@LaunchedEffect }
        // 20 updates a second: at the speed a progress bar moves (a few px/s) that's visually
        // continuous and precise enough for lyric timing, at a third of the redraws (and battery)
        // of ticking every display frame for the whole song.
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - anchor[1]
            // Never run more than ~1.2s ahead of the last real report (a stall, buffering).
            val estimate = anchor[0] + elapsed.coerceAtMost(1_200)
            val duration = progress.value.durationMs
            smooth.longValue = if (duration > 0) estimate.coerceAtMost(duration) else estimate
            delay(50)
        }
    }
    return smooth
}

/**
 * Thin, quiet progress line that becomes a thicker, grabbable bar the moment you touch it — the
 * track is a hairline when you're listening and a control only when you're controlling it.
 * Position is read in the draw phase, so frame-by-frame movement never recomposes anything.
 */
@Composable
fun Scrubber(
    position: () -> Long,
    durationMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val barHeight by animateDpAsState(if (dragging) 10.dp else 5.dp, spring(dampingRatio = 0.7f, stiffness = 600f), label = "barHeight")
    val duration = durationMs.coerceAtLeast(1L)
    val fraction = { if (dragging) dragFraction else (position().toFloat() / duration).coerceIn(0f, 1f) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics { contentDescription = "Seek" }
                .pointerInput(duration) {
                    detectTapGestures(onTap = { o -> onSeek(((o.x / size.width).coerceIn(0f, 1f) * duration).toLong()) })
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; dragFraction = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { onSeek((dragFraction * duration).toLong()); dragging = false },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                }
                .drawBehind {
                    val h = barHeight.toPx()
                    val top = (size.height - h) / 2
                    val r = CornerRadius(h / 2)
                    drawRoundRect(Color.White.copy(alpha = 0.16f), Offset(0f, top), Size(size.width, h), r)
                    val w = size.width * fraction()
                    if (w > 0f) drawRoundRect(Color.White.copy(alpha = if (dragging) 1f else 0.88f), Offset(0f, top), Size(w.coerceAtLeast(h), h), r)
                },
        )
        // Labels tick once a second (derived), not every frame.
        val elapsedSec by remember(duration) { derivedStateOf { ((if (dragging) (dragFraction * duration).toLong() else position()) / 1000) } }
        val labelStyle = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            fontFeatureSettings = "tnum", // tabular digits: the numbers don't jitter as they change
        )
        val labelColor by animateColorAsState(if (dragging) Color.White else Color.White.copy(alpha = 0.55f), label = "labelColor")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(elapsedSec * 1000), style = labelStyle, color = labelColor)
            Text(if (durationMs > 0) "-" + formatTime((durationMs - elapsedSec * 1000).coerceAtLeast(0)) else "--:--", style = labelStyle, color = labelColor)
        }
    }
}

/**
 * Previous · play/pause · next. The play button is the one glass object in the cluster — the
 * thing your thumb goes to — and carries the buffering state as a slim ring around its edge.
 */
@Composable
fun Transport(
    isPlaying: Boolean,
    isBuffering: Boolean,
    accent: Color,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkipButton(Icons.Rounded.SkipPrevious, "Previous", nudge = -1f, onClick = onPrevious)
        Box(
            modifier = Modifier
                .size(80.dp)
                .pressable(onClick = onPlayPause)
                .glass(Radius.pill, Glass.Frosted, tint = Color.White.copy(alpha = 0.04f)),
            contentAlignment = Alignment.Center,
        ) {
            if (isBuffering) BufferingRing(accent)
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn(tween(150)) + scaleIn(initialScale = 0.6f, animationSpec = Motion.press())) togetherWith
                        (fadeOut(tween(110)) + scaleOut(targetScale = 0.6f, animationSpec = tween(110)))
                },
                label = "playPause",
            ) { playing ->
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        SkipButton(Icons.Rounded.SkipNext, "Next", nudge = 1f, onClick = onNext)
    }
}

/** Skip glyph that gives a small push in its own direction when tapped — you feel which way you went. */
@Composable
private fun SkipButton(icon: ImageVector, description: String, nudge: Float, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .size(64.dp)
            .pressable(onClick = {
                onClick()
                scope.launch {
                    offset.animateTo(nudge * with(density) { 7.dp.toPx() }, tween(90))
                    offset.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 500f))
                }
            }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(42.dp).graphicsLayer { translationX = offset.value })
    }
}

@Composable
private fun BufferingRing(accent: Color) {
    val rotation by rememberInfiniteTransition(label = "buffer").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "bufferRotation",
    )
    Box(
        Modifier
            .size(80.dp)
            .graphicsLayer { rotationZ = rotation }
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawArc(
                    color = Color.White.copy(alpha = 0.85f),
                    startAngle = 0f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            },
    )
}

/**
 * The secondary row: lyrics, shuffle, repeat, queue. A toggle that's on sits inside a small glass
 * lens — the same material language as the tab bar — instead of just changing color.
 */
@Composable
fun ActionRow(
    lyricsOn: Boolean,
    shuffleOn: Boolean,
    repeatMode: PlayerRepeatMode,
    onLyrics: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        ToggleAction(Icons.Rounded.Lyrics, "Lyrics", on = lyricsOn, onClick = onLyrics)
        ToggleAction(Icons.Rounded.Shuffle, "Shuffle", on = shuffleOn, onClick = onShuffle)
        ToggleAction(
            if (repeatMode == PlayerRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            when (repeatMode) { PlayerRepeatMode.OFF -> "Repeat off"; PlayerRepeatMode.ALL -> "Repeat all"; PlayerRepeatMode.ONE -> "Repeat one" },
            on = repeatMode != PlayerRepeatMode.OFF,
            onClick = onRepeat,
        )
        ToggleAction(Icons.Rounded.QueueMusic, "Queue", on = false, onClick = onQueue)
    }
}

@Composable
private fun ToggleAction(icon: ImageVector, description: String, on: Boolean, onClick: () -> Unit) {
    val lens by animateFloatAsState(if (on) 1f else 0f, Motion.settle(), label = "lens")
    val tint by animateColorAsState(if (on) Color.White else Color.White.copy(alpha = 0.6f), Motion.settle(), label = "actionTint")
    Box(
        modifier = Modifier.size(width = 60.dp, height = 44.dp).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 38.dp)
                .graphicsLayer { alpha = lens; scaleX = 0.8f + 0.2f * lens; scaleY = 0.8f + 0.2f * lens }
                .glass(Radius.pill, Glass.Regular),
        )
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(23.dp))
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
