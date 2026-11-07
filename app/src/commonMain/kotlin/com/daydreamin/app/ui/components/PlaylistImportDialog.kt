package com.daydreamin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.daydreamin.app.data.library.PlaylistFileException
import com.daydreamin.app.data.library.PlaylistImport
import com.daydreamin.app.data.library.PlaylistLink
import com.daydreamin.app.platform.Log
import com.daydreamin.app.platform.PickKind
import com.daydreamin.app.platform.isDesktop
import com.daydreamin.app.platform.openUrl
import com.daydreamin.app.platform.rememberPicker
import kotlinx.coroutines.launch

private const val EXPORTIFY = "https://exportify.app/"

/** Opens the import window from anywhere, with a link already in it if one was shared to the app. */
object ImportFlow {
    internal var open by mutableStateOf(false)
    internal var sharedLink by mutableStateOf("")

    fun show(link: String = "") {
        sharedLink = link
        open = true
    }
}

/** Goes once at the root of the app's window. */
@Composable
fun ImportHost() {
    if (ImportFlow.open) PlaylistImportDialog(ImportFlow.sharedLink, onDismiss = { ImportFlow.open = false })
}

private val Heading = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
private val Body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = 0.7f))
private val Strong = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)

/**
 * Paste a link, and the playlist comes in. YouTube and Deezer can be read straight away; Spotify
 * lets small apps read nothing, so a Spotify link gets the short way round through Exportify.
 */
@Composable
private fun PlaylistImportDialog(initial: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(initial) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberPicker(PickKind.OPEN_PLAYLIST) { file ->
        if (file != null) { PlaylistImport.import(file); onDismiss() }
    }
    // A playlist link just copied from another app: already there when this opens.
    LaunchedEffect(Unit) {
        if (text.isBlank()) clipboard.getText()?.text?.takeIf { PlaylistLink.parse(it) != null }?.let { text = it }
    }
    val link = PlaylistLink.parse(text)
    val canRead = link is PlaylistLink.YouTube || link is PlaylistLink.Deezer || link is PlaylistLink.DeezerShort
    val spotify = link == PlaylistLink.Spotify

    fun importNow() {
        val target = link ?: return
        working = true
        error = null
        scope.launch {
            try {
                Toaster.show(PlaylistImport.importLink(target))
                onDismiss()
            } catch (e: PlaylistFileException) {
                error = e.message
            } catch (e: Exception) {
                Log.w("PlaylistImport", "link failed: ${e.message}")
                error = "Couldn't reach it. Check your connection and try again."
            }
            working = false
        }
    }

    Dialog(onDismissRequest = { if (!working) onDismiss() }) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF17171E))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(22.dp))
                .padding(22.dp),
        ) {
            Text("Import a playlist", style = Heading)
            Spacer(Modifier.height(4.dp))
            Text("Paste a link from YouTube, YouTube Music, Deezer or Spotify.", style = Body)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                if (text.isEmpty()) Text("Playlist link", style = Body.copy(color = Color.White.copy(alpha = 0.4f)))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val problem = error ?: when {
                link is PlaylistLink.Closed -> "${link.service} doesn't let other apps read its playlists."
                text.isNotBlank() && link == null -> "That isn't a playlist link."
                else -> null
            }
            problem?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = Body.copy(color = Color(0xFFFF8A80)))
            }

            if (spotify) {
                Spacer(Modifier.height(16.dp))
                Text("Spotify doesn't let small apps read playlists, so it takes one extra step:", style = Body)
                Spacer(Modifier.height(10.dp))
                Step(1, "Open Exportify and log in with Spotify")
                Step(2, "Press Export next to your playlist")
                Step(3, if (isDesktop) "Drag the downloaded file onto Daydreamin" else "Open the downloaded file with Daydreamin")
                Spacer(Modifier.height(14.dp))
                Pill("Open Exportify", primary = true) { openUrl(EXPORTIFY, "Couldn't open the browser") }
            } else {
                Spacer(Modifier.height(16.dp))
                Pill(if (working) "Importing…" else "Import", primary = true, enabled = canRead && !working, busy = working, onClick = ::importNow)
            }
            Spacer(Modifier.height(8.dp))
            Pill("Choose a file", primary = false, enabled = !working) { picker.launch("") }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text("$number", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = Strong.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp))
    }
}

@Composable
private fun Pill(label: String, primary: Boolean, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    val fill = if (primary) Color.White else Color.White.copy(alpha = 0.1f)
    val ink = if (primary) Color.Black else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(if (enabled || busy) fill else fill.copy(alpha = fill.alpha * 0.35f))
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = Strong.copy(color = if (enabled || busy) ink else ink.copy(alpha = 0.45f)))
    }
}
