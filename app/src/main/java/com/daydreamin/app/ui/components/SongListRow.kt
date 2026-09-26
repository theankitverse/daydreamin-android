package com.daydreamin.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.glass

/**
 * A song in a list (Library collections, search results). Plain (no glass) — only the song that's playing gets a faint glass
 * lane and moving bars. Tap to play (or, if it's the one playing, to open the player);
 * long-press or ⋮ for actions.
 */
@Composable
fun SongListRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extra: List<SongMenuAction> = emptyList(),
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .height(66.dp)
            .padding(horizontal = 10.dp)
            .then(if (isCurrent) Modifier.glass(Radius.cardShape, Glass.Clear) else Modifier)
            .pressable(onLongClick = { menuOpen = true }, onClick = onClick)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = song.cover.ifBlank { song.artworkUrl }, shape = Radius.thumbShape, modifier = Modifier.size(50.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isCurrent) EqualizerBars(playing = isPlaying, color = Color.White, modifier = Modifier.padding(end = 4.dp))
        Box(Modifier.size(44.dp).pressable(onClick = { menuOpen = true }), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = "More options for ${song.title}", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(22.dp))
            if (menuOpen) SongMenu(song = song, extra = extra, onDismiss = { menuOpen = false })
        }
    }
}

