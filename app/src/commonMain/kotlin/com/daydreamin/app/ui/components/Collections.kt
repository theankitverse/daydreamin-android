package com.daydreamin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.Playlist
import com.daydreamin.app.ui.theme.BrandPink
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.Radius

/** Liked songs' cover: the brand's violet-to-pink with a heart — the one collection that's always yours. */
@Composable
fun LikedTile(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(Radius.cardShape)
            .background(Brush.linearGradient(listOf(BrandViolet, BrandPink.copy(alpha = 0.85f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.4f))
    }
}

/** A playlist's cover: its own artwork if it came with one (a saved YouTube playlist), otherwise a mosaic of its songs. */
@Composable
fun PlaylistCover(playlist: Playlist, size: Dp, modifier: Modifier = Modifier) {
    val cover = playlist.coverUrl?.takeIf { it.isNotBlank() }
    if (cover != null) Artwork(url = cover, shape = Radius.cardShape, modifier = modifier.size(size))
    else Mosaic(playlist.songs, size, modifier)
}

/** Up to four covers in a 2×2 grid — one cover if that's all there is, a quiet glyph if none. */
@Composable
fun Mosaic(songs: List<Song>, size: Dp, modifier: Modifier = Modifier) {
    val covers = songs.map { it.artworkUrl }.filter { it.isNotBlank() }.distinct()
    Box(modifier.size(size).clip(Radius.cardShape).background(Color(0xFF16161A))) {
        when {
            covers.size >= 4 -> Column {
                for (r in 0..1) Row {
                    for (c in 0..1) Artwork(url = covers[r * 2 + c], shape = RectangleShape, edge = false, modifier = Modifier.size(size / 2))
                }
            }
            covers.isNotEmpty() -> Artwork(url = covers.first(), shape = Radius.cardShape, modifier = Modifier.size(size))
            else -> Box(
                Modifier.size(size).background(Brush.linearGradient(listOf(BrandViolet.copy(alpha = 0.35f), Color(0xFF16161A)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(size * 0.3f)) }
        }
    }
}
