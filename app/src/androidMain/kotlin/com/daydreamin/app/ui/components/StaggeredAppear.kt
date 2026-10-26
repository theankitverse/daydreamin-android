package com.daydreamin.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Wraps a LazyColumn/LazyRow item so it fades + slides up into place the first time it
 * appears, staggered by [index] — the "content cascades in" feel Apple Music/Spotify lists
 * have on first load, instead of the whole list just popping in at once.
 */
@Composable
fun StaggeredAppear(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val delay = (index * 35).coerceAtMost(300)
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = fadeIn(tween(320, delayMillis = delay)) +
            slideInVertically(initialOffsetY = { it / 6 }, animationSpec = tween(320, delayMillis = delay)),
    ) {
        content()
    }
}
