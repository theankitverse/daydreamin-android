package com.daydreamin.app.desktop.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.daydreamin.app.data.recommend.Recommender
import com.daydreamin.app.desktop.components.DIconButton
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.platform.BackHandler
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork

/**
 * Now playing filling the window, for listening rather than browsing: the artwork large on the
 * left, the lyrics following along on the right (below it on a narrow window). The player bar
 * stays underneath; Esc, the button in the corner or the artwork in the player bar go back.
 */
@Composable
fun FullNowPlaying(modifier: Modifier = Modifier) {
    val meta by PlayerController.meta.collectAsState()
    val song = meta.currentSong
    val nav = LocalNavigator.current
    BackHandler(enabled = true) { DesktopUi.expanded = false }
    if (song == null) { DesktopUi.expanded = false; return }
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        val wide = maxWidth > 900.dp
        val art = if (wide) min(maxHeight * 0.78f, maxWidth * 0.42f) else min(maxHeight * 0.42f, maxWidth * 0.8f)
        val info = @Composable {
            Artwork(url = song.cover.ifBlank { song.artworkUrl }, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(art).shadow(40.dp, RoundedCornerShape(10.dp)))
            Spacer(Modifier.height(24.dp))
            Text(song.title, style = DType.hero.copy(fontSize = 34.sp, lineHeight = 40.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(art))
            Spacer(Modifier.height(6.dp))
            LinkText(song.artist, DType.body.copy(fontSize = 17.sp, color = Color.White.copy(alpha = 0.75f))) {
                DesktopUi.expanded = false
                nav.go(Route.Artist(Recommender.primaryArtist(song.artist)))
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                Column { info() }
                Box(Modifier.weight(1f).fillMaxHeight()) { LyricsView(song) }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                info()
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f)) { LyricsView(song) }
            }
        }
        Box(Modifier.align(Alignment.TopEnd)) { DIconButton(Icons.Rounded.CloseFullscreen, "Back to the app", { DesktopUi.expanded = false }, shortcut = "Esc") }
    }
}
