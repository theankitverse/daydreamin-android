package com.daydreamin.app.desktop.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DSpace
import com.daydreamin.app.desktop.theme.DType

/**
 * A tile on Home, Library and search results: artwork, title, a line under it. The pointer brings
 * up a soft backdrop and a round play button over the artwork; clicking the tile opens it, the
 * button plays it.
 */
@Composable
fun CardTile(
    title: String,
    subtitle: String?,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    round: Boolean = false,
    menu: (() -> List<MenuEntry?>)? = null,
    art: @Composable (Modifier) -> Unit,
) {
    val (source, hovered) = rememberHover()
    val accent = LocalAccent.current
    // [modifier] (often a weight in a row) goes on the outermost element, or a wrapper would swallow it.
    val body = @Composable { outer: Modifier ->
        Column(
            outer
                .clip(RoundedCornerShape(8.dp))
                .background(if (hovered) DColors.hover else Color.Transparent)
                .hoverable(source)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onOpen)
                .padding(10.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                art(Modifier.fillMaxWidth().aspectRatio(1f).shadow(if (hovered) 10.dp else 4.dp, if (round) CircleShape else RoundedCornerShape(6.dp)))
                if (onPlay != null) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = hovered || playing,
                        enter = fadeIn() + slideInVertically { it / 3 },
                        exit = fadeOut() + slideOutVertically { it / 3 },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    ) {
                        WithTooltip(if (playing) "Pause" else "Play") {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .shadow(8.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(accent)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable(role = Role.Button, onClick = onPlay),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = DType.itemTitleStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = DType.itemSubtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (menu != null) ContextMenuArea(menu, modifier) { body(Modifier.fillMaxWidth()) } else body(modifier)
}

/** How many [minWidth] tiles fit across [available], with [gap] between them. */
fun columnsFor(available: Dp, minWidth: Dp, gap: Dp, max: Int = 9): Int =
    (((available + gap) / (minWidth + gap)).toInt()).coerceIn(2, max)

/**
 * A titled row of tiles: exactly as many as fit the window's width, with "Show all" for the rest —
 * no sideways scrolling, which a mouse wheel can't do.
 */
@Composable
fun <T> Shelf(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    minTile: Dp = 168.dp,
    onShowAll: (() -> Unit)?,
    tile: @Composable (T, Modifier) -> Unit,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = DSpace.pagePad - 10.dp)) {
        val columns = columnsFor(maxWidth, minTile, 0.dp)
        Column {
            SectionHeader(title, subtitle, onShowAll.takeIf { items.size > columns }, Modifier.padding(horizontal = 10.dp))
            Row(Modifier.fillMaxWidth()) {
                items.take(columns).forEach { tile(it, Modifier.weight(1f)) }
                repeat((columns - items.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The same tiles, wrapped into as many rows as they need (a "Show all" page, Library). */
@Composable
fun <T> TileGrid(items: List<T>, modifier: Modifier = Modifier, minTile: Dp = 168.dp, tile: @Composable (T, Modifier) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = DSpace.pagePad - 10.dp)) {
        val columns = columnsFor(maxWidth, minTile, 0.dp)
        Column {
            items.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { tile(it, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, onShowAll: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = DSpace.titleToContent - 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DType.section, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = DType.itemSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onShowAll != null) {
            val (source, hovered) = rememberHover()
            Text(
                "Show all",
                style = DType.label.copy(color = if (hovered) Color.White else DType.itemSubtitle.color, letterSpacing = 0.2.sp),
                modifier = Modifier.hoverable(source).pointerHoverIcon(PointerIcon.Hand).clickable(interactionSource = source, indication = null, onClick = onShowAll).padding(4.dp),
            )
        }
    }
}

private val Double.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
