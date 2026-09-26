package com.daydreamin.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary

@Composable
fun SongRow(
    song: Song,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    trailing: @Composable (RowScope.() -> Unit) = { SongRowMenu(song = song) },
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "rowScale")
    val titleColor by animateColorAsState(
        if (isPlaying) MaterialTheme.colorScheme.primary else TextPrimary,
        animationSpec = tween(200),
        label = "titleColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = song.artworkUrl, modifier = Modifier.size(52.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                song.title,
                color = titleColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPlaying) {
            EqualizerBars(playing = true, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
        }
        trailing()
    }
}

@Composable
fun SongRowMenu(song: Song, onClick: (() -> Unit)? = null) {
    IconButton(onClick = { onClick?.invoke() }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextSecondary)
    }
}
