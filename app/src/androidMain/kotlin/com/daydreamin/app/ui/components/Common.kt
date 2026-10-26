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
        // Sits under the image: shows only while a cover is loading or when it can't load at all
        // (offline, dead link), so a missing cover is a quiet music note — never an empty hole.
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = TextMuted.copy(alpha = 0.45f), modifier = Modifier.fillMaxSize(0.24f))
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
        }
    }
}




/**
 * Artwork as light rather than as a picture: decoded tiny (so upscaling alone already softens
 * it — the whole effect on Android < 12, where blur isn't available) and blurred on top of
 * that. Cheap enough to sit behind a card that scrolls.
 */
@Composable
fun ArtworkBackdrop(url: String?, modifier: Modifier = Modifier, blur: androidx.compose.ui.unit.Dp = 28.dp, tiny: Boolean = false) {
    if (url.isNullOrBlank()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    // tiny: for full-screen backdrops — decoded at ~20px, so upscaling alone already turns the
    // cover into a soft wash of its colors, and only a light blur is needed to melt the texel
    // edges (a much smaller radius than blurring a detailed image into mush).
    val decodePx = if (tiny) 20 else 48
    val model = remember(url, decodePx) {
        coil.request.ImageRequest.Builder(context)
            .data(artworkModel(url))
            .size(decodePx)
            // EXACT: must really be tiny — otherwise Coil happily reuses the full-size cover
            // already in memory, and the "wash" comes out as a sharp photo.
            .precision(coil.size.Precision.EXACT)
            .memoryCacheKey("backdrop:$decodePx:$url")
            .crossfade(400)
            .build()
    }
    val zoom = artworkZoom(url)
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
        modifier = modifier
            .graphicsLayer { scaleX = zoom * 1.15f; scaleY = zoom * 1.15f }
            .then(if (blur > 0.dp) Modifier.blur(blur, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded) else Modifier),
    )
}
