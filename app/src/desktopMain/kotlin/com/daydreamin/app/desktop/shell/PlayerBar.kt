package com.daydreamin.app.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daydreamin.app.AppGraph
import com.daydreamin.app.desktop.components.ContextMenuArea
import com.daydreamin.app.desktop.components.DIconButton
import com.daydreamin.app.desktop.components.DSlider
import com.daydreamin.app.desktop.components.LocalAccent
import com.daydreamin.app.desktop.components.WithTooltip
import com.daydreamin.app.desktop.components.rememberHover
import com.daydreamin.app.desktop.components.songMenuEntries
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.theme.DSize
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.player.RepeatMode
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.SongActions

private val isMac = System.getProperty("os.name").lowercase().contains("mac")
internal val cmd = if (isMac) "⌘" else "Ctrl+"

/** The bar along the bottom of the window: what's playing, the transport and timeline, and the panel and volume controls. */
@Composable
fun PlayerBar(modifier: Modifier = Modifier) {
    val meta by PlayerController.meta.collectAsState()
    BoxWithConstraints(modifier.fillMaxWidth().height(DSize.playerBarHeight).padding(horizontal = 16.dp)) {
        val compact = maxWidth < 860.dp
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(if (compact) 0.34f else 0.3f)) { NowPlayingInfo() }
            Box(Modifier.weight(0.4f), contentAlignment = Alignment.Center) { Transport(enabled = meta.currentSong != null) }
            Box(Modifier.weight(if (compact) 0.26f else 0.3f), contentAlignment = Alignment.CenterEnd) { RightControls(compact) }
        }
    }
}

@Composable
private fun NowPlayingInfo() {
    val meta by PlayerController.meta.collectAsState()
    val song = meta.currentSong ?: return
    val likedIds by AppGraph.prefs.likedIds.collectAsState(initial = emptySet())
    val liked = song.playId in likedIds
    val accent = LocalAccent.current
    val nav = LocalNavigator.current
    ContextMenuArea(songMenuEntries(song, onPlay = { PlayerController.togglePlayPause() })) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WithTooltip("Show Now playing") {
                Artwork(
                    url = song.cover.ifBlank { song.artworkUrl },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(56.dp).pointerHoverIcon(PointerIcon.Hand).clickable { DesktopUi.open(PanelTab.NOW_PLAYING) },
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f, fill = false)) {
                LinkText(song.title, DType.itemTitleStrong) { DesktopUi.open(PanelTab.NOW_PLAYING) }
                LinkText(song.artist, DType.itemSubtitle) { nav.go(Route.Search(song.artist)) }
            }
            Spacer(Modifier.width(8.dp))
            DIconButton(
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                tooltip = if (liked) "Remove from Liked songs" else "Save to Liked songs",
                onClick = { SongActions.toggleLiked(song, liked) },
                iconSize = 18.dp,
                tint = if (liked) accent else null,
            )
        }
    }
}

/** Text that underlines under the pointer, like links in desktop music apps. */
@Composable
internal fun LinkText(text: String, style: androidx.compose.ui.text.TextStyle, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    Text(
        text,
        style = style.copy(textDecoration = if (hovered) TextDecoration.Underline else null, color = if (hovered) Color.White else style.color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.hoverable(source).pointerHoverIcon(PointerIcon.Hand).clickable(interactionSource = source, indication = null, onClick = onClick),
    )
}

@Composable
private fun Transport(enabled: Boolean) {
    val meta by PlayerController.meta.collectAsState()
    val progress by PlayerController.progress.collectAsState()
    val accent = LocalAccent.current
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DIconButton(Icons.Rounded.Shuffle, if (meta.shuffle) "Turn shuffle off" else "Shuffle", { PlayerController.toggleShuffle() }, iconSize = 18.dp, active = meta.shuffle, activeColor = accent, enabled = enabled)
            DIconButton(Icons.Rounded.SkipPrevious, "Previous", { PlayerController.previous() }, iconSize = 24.dp, shortcut = "${cmd}←", enabled = enabled)
            PlayButton(meta.isPlaying, meta.isBuffering, enabled)
            DIconButton(Icons.Rounded.SkipNext, "Next", { PlayerController.next() }, iconSize = 24.dp, shortcut = "${cmd}→", enabled = enabled)
            DIconButton(
                if (meta.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                when (meta.repeatMode) { RepeatMode.OFF -> "Repeat"; RepeatMode.ALL -> "Repeat one"; RepeatMode.ONE -> "Turn repeat off" },
                { PlayerController.cycleRepeat() },
                iconSize = 18.dp,
                active = meta.repeatMode != RepeatMode.OFF,
                activeColor = accent,
                enabled = enabled,
            )
        }
        Spacer(Modifier.height(4.dp))
        var dragging by remember { mutableStateOf<Float?>(null) }
        val duration = progress.durationMs
        val fraction = if (duration > 0) progress.positionMs.toFloat() / duration else 0f
        val shownMs = dragging?.let { (it * duration).toLong() } ?: progress.positionMs
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time(shownMs), style = DType.numeric, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
            DSlider(
                value = fraction,
                onValueChange = { dragging = it },
                onCommit = { f -> dragging = null; if (duration > 0) PlayerController.seekTo((f * duration).toLong()) },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            Text(if (duration > 0) time(duration) else "–:––", style = DType.numeric, modifier = Modifier.width(44.dp))
        }
    }
}

@Composable
private fun PlayButton(playing: Boolean, buffering: Boolean, enabled: Boolean) {
    val (source, hovered) = rememberHover()
    WithTooltip(if (playing) "Pause" else "Play", "Space") {
        Box(
            Modifier
                .size(if (hovered) 38.dp else 36.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.White.copy(alpha = 0.3f))
                .hoverable(source)
                .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                .clickable(interactionSource = source, indication = null, role = Role.Button, enabled = enabled) { PlayerController.togglePlayPause() },
            contentAlignment = Alignment.Center,
        ) {
            if (buffering && playing) CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun RightControls(compact: Boolean) {
    val accent = LocalAccent.current
    val volume by PlayerController.volume.collectAsState()
    var beforeMute by remember { mutableFloatStateOf(0.7f) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DIconButton(Icons.Rounded.ViewSidebar, "Now playing view", { DesktopUi.toggle(PanelTab.NOW_PLAYING) }, iconSize = 18.dp, active = DesktopUi.showing(PanelTab.NOW_PLAYING), activeColor = accent)
        DIconButton(Icons.Rounded.Lyrics, "Lyrics", { DesktopUi.toggle(PanelTab.LYRICS) }, iconSize = 18.dp, active = DesktopUi.showing(PanelTab.LYRICS), activeColor = accent)
        DIconButton(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", { DesktopUi.toggle(PanelTab.QUEUE) }, iconSize = 19.dp, active = DesktopUi.showing(PanelTab.QUEUE), activeColor = accent)
        Spacer(Modifier.width(4.dp))
        DIconButton(
            when { volume == 0f -> Icons.AutoMirrored.Rounded.VolumeOff; volume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown; else -> Icons.AutoMirrored.Rounded.VolumeUp },
            tooltip = if (volume == 0f) "Unmute" else "Mute",
            onClick = {
                if (volume > 0f) { beforeMute = volume; PlayerController.setVolume(0f) } else PlayerController.setVolume(beforeMute.coerceAtLeast(0.1f))
            },
            iconSize = 18.dp,
        )
        DSlider(
            value = volume,
            onValueChange = { PlayerController.setVolume(it) },
            onCommit = { PlayerController.setVolume(it) },
            modifier = Modifier.width(if (compact) 64.dp else 96.dp),
        )
    }
}

private fun time(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
