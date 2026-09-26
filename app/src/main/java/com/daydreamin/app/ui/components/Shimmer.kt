package com.daydreamin.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.daydreamin.app.ui.theme.SurfaceVariant
import com.daydreamin.app.ui.theme.SurfaceHigh

/** A sweeping shimmer highlight, the standard "content is loading" language premium apps use instead of a bare spinner. */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmerTranslate",
    )
    val brush = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.05f)),
        start = Offset(translate, 0f),
        end = Offset(translate + 400f, 400f),
    )
    return this.background(brush)
}

@Composable
fun ShimmerSongRow(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).shimmer())
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .align(Alignment.CenterVertically),
        ) {
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
        }
    }
}

@Composable
fun ShimmerSongList(modifier: Modifier = Modifier, count: Int = 6) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        repeat(count) { ShimmerSongRow() }
    }
}
