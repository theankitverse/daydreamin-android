package com.daydreamin.app.ui.components

import androidx.compose.foundation.background
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
    val blur = remember {
        HazeStyle(backgroundColor = BgBase, tints = listOf(HazeTint(Color.Black.copy(alpha = 0.38f))), blurRadius = 30.dp, noiseFactor = 0.05f)
    }
    Box(
        modifier = modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .height(64.dp)
            .pressable(onClick = onClick)
            .shadow(elevation = 18.dp, shape = MiniShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(MiniShape)
            .then(if (hazeState != null) Modifier.hazeEffect(hazeState, blur) else Modifier.background(BgBase))
            .background(tint.copy(alpha = 0.17f))
            .glass(MiniShape, Glass.Regular),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = Space.xs, end = Space.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(url = song.artworkUrl, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = Space.s)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MiniControl(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                description = if (isPlaying) "Pause" else "Play",
                onClick = onTogglePlay,
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
private fun MiniControl(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(46.dp).clip(Radius.pill).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(28.dp))
    }
}
