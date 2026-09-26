package com.daydreamin.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.daydreamin.app.ui.theme.ArtworkLight

/**
 * Light spilling from the top of the screen, as if the lead artwork were a lamp just above it:
 * a key light from the upper left and a second, different-hued fill light lower on the right.
 * Drifts up and dims as you scroll (read in the draw phase only — scrolling never recomposes it).
 */
@Composable
fun AmbientGlow(light: ArtworkLight, scrollY: () -> Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(680.dp)
            .graphicsLayer {
                val y = scrollY()
                translationY = -y * 0.35f
                alpha = 1f - (y / 1400f).coerceIn(0f, 1f) * 0.7f
            }
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to light.key.copy(alpha = 0.36f),
                        0.4f to light.key.copy(alpha = 0.12f),
                        1f to Color.Transparent,
                        center = Offset(size.width * 0.08f, 0f),
                        radius = size.width * 1.1f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        0f to light.fill.copy(alpha = 0.26f),
                        0.5f to light.fill.copy(alpha = 0.08f),
                        1f to Color.Transparent,
                        center = Offset(size.width * 0.98f, size.height * 0.26f),
                        radius = size.width * 0.85f,
                    ),
                )
            },
    )
}

