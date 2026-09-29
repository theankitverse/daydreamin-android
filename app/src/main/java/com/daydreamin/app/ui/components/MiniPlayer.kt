package com.daydreamin.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import coil.imageLoader
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.daydreamin.app.ui.theme.Motion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.Space
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import com.daydreamin.app.ui.theme.glass
import com.daydreamin.app.ui.theme.rememberArtworkColor
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private val MiniShape = RoundedCornerShape(20.dp)

/**
 * A floating slab of glass: it blurs whatever scrolls beneath it, carries a faint wash of the
 * current artwork's color, and shows progress as a hairline along its lower edge.
 * [progress] is read only while drawing, so the 2×/second position ticks never recompose it.
 */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: () -> Float,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
) {
    val tint = rememberArtworkColor(song.cover.ifBlank { song.artworkUrl }, fallback = BrandViolet)
    // Warm the full-size cover into memory while the song plays, so opening Now Playing has the
    // real artwork to fly from the first frame (a cache hit — no load-then-fade mid-flight).
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(song.artworkUrl) {
        if (song.artworkUrl.isNotBlank()) {
            context.imageLoader.enqueue(
                coil.request.ImageRequest.Builder(context).data(com.daydreamin.app.ui.theme.artworkModel(song.artworkUrl)).size(1080).build(),
            )
        }
    }
    val blur = remember {
        HazeStyle(backgroundColor = BgBase, tints = listOf(HazeTint(Color.Black.copy(alpha = 0.32f))), blurRadius = 34.dp, noiseFactor = 0.05f)
    }
    Box(
        modifier = modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .height(64.dp)
            .pressable(onClick = onClick)
            .shadow(elevation = 18.dp, shape = MiniShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(MiniShape)
            .then(if (hazeState != null) Modifier.hazeEffect(hazeState, blur) { inputScale = HazeInputScale.Auto } else Modifier.background(BgBase))
            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.06f))))
            .glass(MiniShape, Glass.Regular),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = Space.xs, end = Space.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A new song crossfades in (art and text together) instead of the text just changing.
            Crossfade(targetState = song, animationSpec = tween(320), label = "miniSong", modifier = Modifier.weight(1f)) { shown ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(
                        url = shown.artworkUrl,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .size(48.dp)
                            // Now Playing grows out of (and lands back into) exactly this spot…
                            .onGloballyPositioned { PlayerSheet.miniArtworkBounds = it.boundsInWindow() }
                            // …and while it's open or in flight, the flying artwork *is* this artwork.
                            .graphicsLayer { alpha = ((0.12f - PlayerSheet.expansion) / 0.12f).coerceIn(0f, 1f) }, // cross-fades with the flying one over the last stretch
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = Space.s)) {
                        Text(shown.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(shown.artist, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            MiniControl(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                description = if (isPlaying) "Pause" else "Play",
                onClick = onTogglePlay,
                morph = true,
            )
            MiniControl(icon = Icons.Rounded.SkipNext, description = "Next", onClick = onNext)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .height(2.dp)
                .drawBehind {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = r)
                    val w = size.width * progress().coerceIn(0f, 1f)
                    if (w > 0f) drawRoundRect(Color.White.copy(alpha = 0.85f), Offset.Zero, Size(w, size.height), r)
                },
        )
    }
}

@Composable
private fun MiniControl(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, morph: Boolean = false) {
    Box(
        modifier = Modifier.size(46.dp).clip(Radius.pill).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (morph) {
            // Play ↔ pause swap with a small scale-and-fade, so the state change is felt, not just seen.
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(initialScale = 0.7f, animationSpec = Motion.press())) togetherWith
                        (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f, animationSpec = tween(120)))
                },
                label = "playPause",
            ) { shown -> Icon(shown, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(28.dp)) }
        } else {
            Icon(icon, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(28.dp))
        }
    }
}
