package com.daydreamin.app.desktop.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DSize
import com.daydreamin.app.desktop.theme.DSpace
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.DownloadedMark
import com.daydreamin.app.ui.components.SongActions

enum class SortBy { NONE, TITLE, ARTIST, ALBUM, DURATION }

/** A table's sort and selection; keyboard keys move the selection and play it. */
class TrackTableState {
    var sortBy by mutableStateOf(SortBy.NONE)
    var descending by mutableStateOf(false)
    var selected by mutableIntStateOf(-1)

    fun sorted(songs: List<Song>): List<Song> {
        val s = when (sortBy) {
            SortBy.NONE -> return songs
            SortBy.TITLE -> songs.sortedBy { it.title.lowercase() }
            SortBy.ARTIST -> songs.sortedBy { it.artist.lowercase() }
            SortBy.ALBUM -> songs.sortedBy { albumOf(it).lowercase() }
            SortBy.DURATION -> songs.sortedBy { it.duration }
        }
        return if (descending) s.reversed() else s
    }

    fun toggle(by: SortBy) {
        when {
            sortBy != by -> { sortBy = by; descending = false }
            !descending -> descending = true
            else -> { sortBy = SortBy.NONE; descending = false } // third click: back to the list's own order
        }
        selected = -1
    }
}

private fun albumOf(song: Song) = song.album.takeIf { it.isNotBlank() && it != "Single" }.orEmpty()

fun formatDuration(seconds: Int): String = if (seconds <= 0) "" else "%d:%02d".format(seconds / 60, seconds % 60)

/** ↑/↓ moves the selection, Enter plays it — attach to the page that holds the table. */
fun Modifier.trackTableKeys(state: TrackTableState, songs: () -> List<Song>, onPlay: (Int) -> Unit): Modifier = onKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
    val n = songs().size
    when (e.key) {
        Key.DirectionDown -> { state.selected = (state.selected + 1).coerceAtMost(n - 1); true }
        Key.DirectionUp -> { state.selected = (state.selected - 1).coerceAtLeast(0); true }
        Key.Enter -> { if (state.selected in 0 until n) { onPlay(state.selected); true } else false }
        else -> false
    }
}

/**
 * The song table, as rows of a page's list: a header (click a column to sort), then one row per
 * song. [onPlay] gets the index in the order shown. [showAlbum] is off on narrow pages.
 */
fun LazyListScope.trackTable(
    songs: List<Song>,
    state: TrackTableState,
    showAlbum: Boolean,
    onPlay: (index: Int, shown: List<Song>) -> Unit,
    extraMenu: (Song) -> List<MenuEntry?> = { emptyList() },
) {
    val shown = state.sorted(songs)
    item(key = "table-header") { TableHeader(state, showAlbum) }
    itemsIndexed(shown, key = { i, s -> "row-$i-${s.playId}" }) { index, song ->
        TrackRow(
            index = index,
            song = song,
            selected = state.selected == index,
            showAlbum = showAlbum,
            onSelect = { state.selected = index },
            onPlay = { onPlay(index, shown) },
            extraMenu = extraMenu(song),
        )
    }
}

@Composable
private fun TableHeader(state: TrackTableState, showAlbum: Boolean) {
    Column(Modifier.padding(horizontal = DSpace.pagePad)) {
        Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#", style = DType.label, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
            Spacer(Modifier.width(14.dp))
            HeaderCell("TITLE", SortBy.TITLE, state, Modifier.weight(if (showAlbum) 0.55f else 1f))
            if (showAlbum) HeaderCell("ALBUM", SortBy.ALBUM, state, Modifier.weight(0.45f))
            Spacer(Modifier.width(76.dp))
            Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) {
                HeaderCell(null, SortBy.DURATION, state, Modifier, icon = true)
            }
            Spacer(Modifier.width(36.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DColors.divider))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeaderCell(label: String?, by: SortBy, state: TrackTableState, modifier: Modifier, icon: Boolean = false) {
    val (source, hovered) = rememberHover()
    val active = state.sortBy == by
    val color = if (active || hovered) Color.White else DType.label.color
    // The column's width goes on the outside, or the tooltip's own box would swallow it.
    WithTooltip(if (icon) "Sort by length" else "Sort by ${label!!.lowercase()}", modifier = modifier) {
        Row(
            Modifier.hoverable(source).pointerHoverIcon(PointerIcon.Hand).combinedClickable(interactionSource = source, indication = null, onClick = { state.toggle(by) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon) Icon(Icons.Rounded.Schedule, contentDescription = "Length", tint = color, modifier = Modifier.size(15.dp))
            else Text(label!!, style = DType.label.copy(color = color))
            if (active) Icon(if (state.descending) Icons.Rounded.ArrowDropDown else Icons.Rounded.ArrowDropUp, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    index: Int,
    song: Song,
    selected: Boolean,
    showAlbum: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    extraMenu: List<MenuEntry?> = emptyList(),
) {
    val accent = LocalAccent.current
    val isCurrent = rememberIsCurrent(song)
    val liked = rememberLiked(song)
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val menu = songMenuEntries(song, onPlay = onPlay, extra = extraMenu)

    ContextMenuArea(menu, Modifier.padding(horizontal = DSpace.pagePad)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(DSize.rowHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        selected -> DColors.selected
                        hovered -> DColors.hover
                        else -> Color.Transparent
                    },
                )
                .hoverable(source)
                .combinedClickable(interactionSource = source, indication = null, onClick = onSelect, onDoubleClick = onPlay)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // # → ▶ under the pointer → bars while it plays
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                when {
                    hovered -> Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${song.title}",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).pointerHoverIcon(PointerIcon.Hand).combinedClickable(onClick = onPlay),
                    )
                    isCurrent && rememberIsPlaying() -> PlayingBars(accent)
                    else -> Text("${index + 1}", style = DType.numeric.copy(color = if (isCurrent) accent else DType.numeric.color), textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.width(14.dp))
            Row(Modifier.weight(if (showAlbum) 0.55f else 1f), verticalAlignment = Alignment.CenterVertically) {
                Artwork(url = song.cover.ifBlank { song.artworkUrl }, shape = RoundedCornerShape(4.dp), modifier = Modifier.size(DSize.rowArt))
                Column(Modifier.padding(start = 12.dp, end = 12.dp)) {
                    Text(song.title, style = DType.itemTitle.copy(color = if (isCurrent) accent else Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DownloadedMark(song, Modifier.padding(end = 4.dp))
                        Text(song.artist, style = DType.itemSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (showAlbum) Text(albumOf(song), style = DType.itemSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.45f).padding(end = 12.dp))
            Box(Modifier.width(76.dp), contentAlignment = Alignment.CenterEnd) {
                if (liked || hovered) {
                    DIconButton(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        tooltip = if (liked) "Remove from Liked songs" else "Save to Liked songs",
                        onClick = { SongActions.toggleLiked(song, liked) },
                        size = 28.dp, iconSize = 17.dp,
                        tint = if (liked) accent else null,
                    )
                }
            }
            Text(formatDuration(song.duration), style = DType.numeric, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
            Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterEnd) {
                if (hovered || menuOpen) DIconButton(Icons.Rounded.MoreHoriz, tooltip = "More options", onClick = { menuOpen = true }, size = 28.dp, iconSize = 18.dp)
                if (menuOpen) MenuPopup(menu(), onDismiss = { menuOpen = false })
            }
        }
    }
}
