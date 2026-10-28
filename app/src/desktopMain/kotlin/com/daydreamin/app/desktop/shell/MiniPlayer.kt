package com.daydreamin.app.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.daydreamin.app.desktop.components.DIconButton
import com.daydreamin.app.desktop.components.desktopClickable
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.resources.Res
import com.daydreamin.app.resources.splash_logo
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.ArtworkBackdrop
import org.jetbrains.compose.resources.painterResource

/**
 * The mini player: a small glass card that stays on top of everything else, with the song, the
 * transport and the time, standing in for the main window (which comes back with the expand
 * button). Drag it anywhere.
 */
@Composable
fun ApplicationScope.MiniPlayerWindow(onExpand: () -> Unit, onQuit: () -> Unit, wrap: @Composable (@Composable () -> Unit) -> Unit) {
    // Bottom right of the screen, clear of the Dock / taskbar.
    val corner = remember {
        val area = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        WindowPosition((area.x + area.width - 380 - 24).dp, (area.y + area.height - 112 - 24).dp)
    }
    val state = rememberWindowState(size = DpSize(380.dp, 112.dp), position = corner)
    Window(
        onCloseRequest = onExpand,
        state = state,
        title = "Daydreamin",
        icon = painterResource(Res.drawable.splash_logo),
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        onKeyEvent = { com.daydreamin.app.handleShortcut(it) },
    ) {
        wrap {
            val meta by PlayerController.meta.collectAsState()
            val progress by PlayerController.progress.collectAsState()
            val song = meta.currentSong
            val shape = RoundedCornerShape(16.dp)
            WindowDraggableArea(Modifier.fillMaxSize().padding(4.dp).clip(shape)) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10)).border(1.dp, Color.White.copy(alpha = 0.12f), shape)) {
                    // The song's colours behind the glass, as in the main window.
                    song?.let { ArtworkBackdrop(it.cover.ifBlank { it.artworkUrl }, Modifier.fillMaxSize().alpha(0.7f), blur = 0.dp, tiny = true) }
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.6f)))))
                    Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(url = song?.let { it.cover.ifBlank { it.artworkUrl } }, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(84.dp))
                        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, end = 4.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(song?.title ?: "Nothing playing", style = DType.itemTitleStrong.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song?.artist ?: "", style = DType.itemSubtitle.copy(color = Color.White.copy(alpha = 0.7f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DIconButton(Icons.Rounded.SkipPrevious, "Previous", { PlayerController.previous() }, iconSize = 22.dp, enabled = song != null, tint = Color.White)
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(Color.White).desktopClickable(CircleShape, highlight = false, enabled = song != null) { PlayerController.togglePlayPause() },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(if (meta.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (meta.isPlaying) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(20.dp)) }
                                DIconButton(Icons.Rounded.SkipNext, "Next", { PlayerController.next() }, iconSize = 22.dp, enabled = song != null, tint = Color.White)
                            }
                            // The time, as a thin line along the bottom.
                            val fraction = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
                            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            DIconButton(Icons.Rounded.OpenInFull, "Back to the full window", onExpand, size = 28.dp, iconSize = 16.dp, tint = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.width(0.dp))
                            DIconButton(Icons.Rounded.Close, "Quit Daydreamin", onQuit, size = 28.dp, iconSize = 16.dp, tint = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}
