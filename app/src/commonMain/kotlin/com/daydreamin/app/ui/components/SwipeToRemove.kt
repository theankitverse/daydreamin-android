package com.daydreamin.app.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.daydreamin.app.platform.Haptic
import com.daydreamin.app.platform.rememberHaptics
import com.daydreamin.app.ui.theme.Radius
import kotlinx.coroutines.launch

/** How far (as a share of the row's width) a row has to be swiped before letting go removes it. */
const val SwipeRemoveAt = 0.35f

/**
 * A row that can be swiped left to remove it, as in the queue: a red wash and a bin show from
 * under it, a tick is felt as it passes the point of no return, and letting go past it slides
 * it away and calls [onRemove]; short of it, it springs back.
 */
@Composable
fun SwipeToRemove(key: Any, onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // A plain float set straight from the drag (see QueueScreen's rows for why not an Animatable).
    var swipeX by remember(key) { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptics()
    Box(modifier.fillMaxWidth()) {
        val reveal = { (-swipeX / (width * SwipeRemoveAt)).coerceIn(0f, 1f) }
        Box(
            Modifier
                .matchParentSize()
                .padding(horizontal = 10.dp)
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
        Box(
            Modifier
                .graphicsLayer { translationX = swipeX; width = size.width.coerceAtLeast(1f) }
                .pointerInput(key) {
                    val removeAt = { -size.width * SwipeRemoveAt }
                    val settle = { scope.launch { animate(swipeX, 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)) { v, _ -> swipeX = v } } }
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            val wasPast = swipeX < removeAt()
                            swipeX = (swipeX + dx).coerceIn(-size.width.toFloat(), 0f)
                            if (wasPast != swipeX < removeAt()) haptic(Haptic.Threshold)
                        },
                        onDragEnd = {
                            if (swipeX < removeAt()) {
                                scope.launch {
                                    animate(swipeX, -size.width.toFloat(), animationSpec = tween(180)) { v, _ -> swipeX = v }
                                    onRemove()
                                }
                            } else settle()
                        },
                        onDragCancel = { settle() },
                    )
                },
        ) { content() }
    }
}
