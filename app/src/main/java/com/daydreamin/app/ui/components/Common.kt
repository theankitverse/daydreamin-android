package com.daydreamin.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.daydreamin.app.ui.theme.Surface
import com.daydreamin.app.ui.theme.artworkModel
import com.daydreamin.app.ui.theme.artworkZoom
import androidx.compose.runtime.remember
import com.daydreamin.app.ui.theme.SurfaceVariant
import com.daydreamin.app.ui.theme.TextMuted
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
        if (actionLabel != null) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onAction?.invoke() },
            )
        }
    }
}

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else SurfaceVariant
    val fg = if (selected) Color.White else TextSecondary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(contentPadding),
    ) { content() }
}

/**
 * Album art. [edge] draws a hairline just inside the artwork's border — on true black, a dark
 * cover otherwise has no edge at all and reads as a hole; this is how print and Apple-style
 * UIs "seat" artwork on dark backgrounds.
 */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp),
    edge: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .then(
                if (edge) Modifier.drawWithContent {
                    drawContent()
                    drawOutline(shape.createOutline(size, layoutDirection, this), Color.White.copy(alpha = 0.10f), style = Stroke(width = 0.8.dp.toPx()))
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val model = remember(url) {
                coil.request.ImageRequest.Builder(context).data(artworkModel(url)).crossfade(220).build()
            }
            val zoom = artworkZoom(url)
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
            )
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = TextMuted)
        }
    }
}

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon?.invoke()
        Text(message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Small rotating vinyl-ish accent used on the splash screen. */
@Composable
fun BrandGlow(modifier: Modifier = Modifier, color: Color) {
    val transition = rememberInfiniteTransition(label = "glow")
    val scale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)),
                CircleShape,
            ),
    )
}

/**
 * Artwork as light rather than as a picture: decoded tiny (so upscaling alone already softens
 * it — the whole effect on Android < 12, where blur isn't available) and blurred on top of
 * that. Cheap enough to sit behind a card that scrolls.
 */
@Composable
fun ArtworkBackdrop(url: String?, modifier: Modifier = Modifier, blur: androidx.compose.ui.unit.Dp = 28.dp) {
    if (url.isNullOrBlank()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val model = remember(url) {
        coil.request.ImageRequest.Builder(context).data(artworkModel(url)).size(48).crossfade(400).build()
    }
    val zoom = artworkZoom(url)
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = modifier
            .graphicsLayer { scaleX = zoom * 1.15f; scaleY = zoom * 1.15f }
            .blur(blur, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
    )
}
