package com.daydreamin.app.desktop.components

import com.daydreamin.app.desktop.theme.DColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.ui.components.Artwork
import kotlin.math.roundToInt

/**
 * Dragging a song onto a playlist (or Liked songs) in the sidebar to add it there. Songs in lists
 * are drag sources; sidebar rows are drop targets; while a song is being dragged it follows the
 * pointer as a small card, and the target under it lights up.
 */
object SongDrag {
    var song by mutableStateOf<Song?>(null)
        private set
    /** Where the pointer is, in window pixels. */
    var position by mutableStateOf(Offset.Zero)
        private set
    private class Target(val bounds: Rect, val label: String, val onDrop: (Song) -> Unit)
    private val targets = mutableStateMapOf<String, Target>()

    /** The drop target under the pointer. */
    val hovered: String? get() = if (song == null) null else targets.entries.firstOrNull { it.value.bounds.contains(position) }?.key
    val hoveredLabel: String? get() = hovered?.let { targets[it]?.label }

    internal fun start(song: Song, at: Offset) { this.song = song; position = at }
    internal fun move(to: Offset) { position = to }
    internal fun drop() {
        val s = song
        val target = hovered?.let { targets[it] }
        song = null
        if (s != null && target != null) target.onDrop(s)
    }
    internal fun cancel() { song = null }

    internal fun register(key: String, bounds: Rect, label: String, onDrop: (Song) -> Unit) { targets[key] = Target(bounds, label, onDrop) }
    internal fun unregister(key: String) { targets.remove(key) }
}

/** Lets [song] be dragged from here (onto a playlist in the sidebar). Clicks and double-clicks are untouched. */
fun Modifier.songDragSource(song: Song): Modifier = composed {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    this
        .onGloballyPositioned { coords = it }
        .pointerInput(song.playId) {
            detectDragGestures(
                onDragStart = { at -> coords?.let { SongDrag.start(song, it.localToWindow(at)) } },
                onDrag = { change, _ -> change.consume(); coords?.let { SongDrag.move(it.localToWindow(change.position)) } },
                onDragEnd = { SongDrag.drop() },
                onDragCancel = { SongDrag.cancel() },
            )
        }
}

/** A place a dragged song can be dropped: lit up while one is over it. */
fun Modifier.songDropTarget(key: String, label: String, onDrop: (Song) -> Unit): Modifier = composed {
    DisposableEffect(key) { onDispose { SongDrag.unregister(key) } }
    val over = SongDrag.hovered == key
    this
        .onGloballyPositioned { SongDrag.register(key, it.boundsInWindow(), label, onDrop) }
        .then(if (over) Modifier.background(LocalAccent.current.copy(alpha = 0.22f), RoundedCornerShape(6.dp)).border(1.dp, LocalAccent.current, RoundedCornerShape(6.dp)) else Modifier)
}

/** The song being dragged, following the pointer (drawn over the whole window). */
@Composable
fun SongDragGhost() {
    val song = SongDrag.song ?: return
    val target = SongDrag.hoveredLabel
    Row(
        Modifier
            .offset { IntOffset(SongDrag.position.x.roundToInt() + 14, SongDrag.position.y.roundToInt() + 10) }
            .shadow(16.dp, RoundedCornerShape(8.dp))
            .background(DColors.popup, RoundedCornerShape(8.dp))
            .padding(6.dp)
            .widthIn(max = 280.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = song.cover.ifBlank { song.artworkUrl }, shape = RoundedCornerShape(4.dp), modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (target != null) "Add to $target" else song.title,
            style = DType.itemTitleStrong.copy(color = DColors.ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 6.dp),
        )
    }
}
