package com.daydreamin.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** The little bouncing-bars "now playing" indicator, like Spotify/Apple Music song rows use. */
@Composable
fun EqualizerBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color,
    barCount: Int = 3,
) {
    val bars = List(barCount) { index ->
        val transition = rememberInfiniteTransition(label = "eq$index")
        val duration = 420 + index * 160
        val height by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = if (playing) 1f else 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "eqHeight$index",
        )
        height
    }

    Canvas(modifier = modifier.size(16.dp)) {
        val barWidth = size.width / (barCount * 2 - 1)
        bars.forEachIndexed { index, heightFraction ->
            val x = index * barWidth * 2 + barWidth / 2
            val barHeight = size.height * heightFraction.coerceIn(0.15f, 1f)
            drawLine(
                color = color,
                start = Offset(x, size.height),
                end = Offset(x, size.height - barHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
