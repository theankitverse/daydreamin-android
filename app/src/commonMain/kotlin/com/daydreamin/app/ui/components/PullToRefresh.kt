package com.daydreamin.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.daydreamin.app.platform.Haptic
import com.daydreamin.app.platform.rememberHaptics
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.glass
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** Drag travel is halved, and harder still past the refresh point, so the page stretches rather than slides. */
private const val Resistance = 0.5f

/**
 * Pull down at the top of a list to refresh it: give the list [connection] (nestedScroll) and
 * move it down by [offset]; [PullIndicator] shows the pull. While a refresh started this way is
 * running the list rests a little lowered, over the spinning indicator.
 */
class PullToRefreshState internal constructor(internal val thresholdPx: Float, internal val holdPx: Float) {
    internal var pull by mutableFloatStateOf(0f)
    internal var held: androidx.compose.runtime.State<Float>? = null
    internal var pulledRefreshing by mutableStateOf(false)
    internal var onCross: () -> Unit = {}
    internal var onRelease: () -> Unit = {}

    /** How far the list is drawn down, in pixels. */
    val offset: Float get() = maxOf(pull, held?.value ?: 0f)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Pushing back up after a pull takes the pull back first.
            if (source != NestedScrollSource.UserInput || available.y >= 0f || pull <= 0f) return Offset.Zero
            val next = (pull + available.y * Resistance).coerceAtLeast(0f)
            val used = (pull - next) / Resistance
            setPull(next)
            return Offset(0f, -used)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Only what the list couldn't scroll (it's at the top) becomes pull.
            if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            val past = if (pull > thresholdPx) 0.45f else 1f
            setPull(pull + available.y * Resistance * past)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (pull <= 0f) return Velocity.Zero
            if (pull >= thresholdPx) { pulledRefreshing = true; onRelease() }
            animate(pull, 0f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)) { v, _ -> pull = v }
            return available
        }
    }

    private fun setPull(value: Float) {
        val wasPast = pull >= thresholdPx
        pull = value
        if (wasPast != pull >= thresholdPx) onCross()
    }
}

@Composable
fun rememberPullToRefresh(refreshing: Boolean, onRefresh: () -> Unit, threshold: Dp = 84.dp, hold: Dp = 56.dp): PullToRefreshState {
    val density = LocalDensity.current
    val state = remember { with(density) { PullToRefreshState(threshold.toPx(), hold.toPx()) } }
    val haptic = rememberHaptics()
    val refresh by rememberUpdatedState(onRefresh)
    state.onCross = { haptic(Haptic.Threshold) }
    state.onRelease = { refresh() }
    // Rest lowered over the spinner from the moment the pull is let go until the refresh it started
    // has run (it may take a moment to report that it's begun; if it never does, give up after a bit).
    val isRefreshing = rememberUpdatedState(refreshing)
    LaunchedEffect(state.pulledRefreshing) {
        if (!state.pulledRefreshing) return@LaunchedEffect
        kotlinx.coroutines.withTimeoutOrNull(1_500) { snapshotFlow { isRefreshing.value }.first { it } }
        snapshotFlow { isRefreshing.value }.first { !it }
        state.pulledRefreshing = false
    }
    state.held = animateFloatAsState(if (state.pulledRefreshing) state.holdPx else 0f, spring(dampingRatio = 0.85f, stiffness = 300f), label = "pullHeld")
    return state
}

/** The pull's glyph: turns with the pull, fills in as it nears the refresh point, spins while refreshing. Place it where the list's top edge rests. */
@Composable
fun PullIndicator(state: PullToRefreshState, modifier: Modifier = Modifier) {
    val spinning = state.pulledRefreshing
    val spin = if (spinning) {
        rememberInfiniteTransition(label = "pullSpin").animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart), label = "pullSpinAngle").value
    } else 0f
    Box(
        modifier
            .offset { IntOffset(0, (state.offset / 2f - 22.dp.toPx()).roundToInt()) }
            .size(40.dp)
            .graphicsLayer {
                val reach = (state.offset / state.thresholdPx).coerceIn(0f, 1f)
                alpha = reach
                val s = 0.5f + 0.5f * reach
                scaleX = s; scaleY = s
                rotationZ = if (spinning) spin else reach * 270f
            }
            .glass(Radius.pill, Glass.Regular, tint = Color.Black.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
