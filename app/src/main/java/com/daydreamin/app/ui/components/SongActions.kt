package com.daydreamin.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.Playlist
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * What you can do with a song from anywhere in the app, built only on what the engine and the
 * library store already support: queue it (next / last), like it, put it in a playlist.
 */
object SongActions {
    private val app get() = DaydreaminApp.instance

    /** Right after the current song. Nothing playing? Then "next" simply means now. */
    fun playNext(song: Song) {
        val meta = PlayerController.meta.value
        val current = meta.currentSong
        if (current == null) { PlayerController.playSong(song); return }
        if (current.playId == song.playId) { Toaster.show("Already playing"); return }
        PlayerController.addToQueue(song) // no-op if it's already queued — then we just move it up
        val q = PlayerController.meta.value.queue
        val at = q.indexOfFirst { it.playId == song.playId }
        if (at > 0) PlayerController.moveQueueItem(at, 0)
        Toaster.show("Playing next")
    }

    fun addToQueue(song: Song) {
        val meta = PlayerController.meta.value
        if (meta.currentSong == null) { PlayerController.playSong(song); return }
        when {
            meta.currentSong.playId == song.playId -> Toaster.show("Already playing")
            meta.queue.any { it.playId == song.playId } -> Toaster.show("Already in your queue")
            else -> { PlayerController.addToQueue(song); Toaster.show("Added to queue") }
        }
    }

    fun toggleLiked(song: Song, currentlyLiked: Boolean) {
        app.appScope.launch { app.prefs.toggleLiked(song) }
        Toaster.show(if (currentlyLiked) "Removed from Liked songs" else "Added to Liked songs")
    }

    fun addToPlaylist(playlist: Playlist, song: Song) {
        app.appScope.launch {
            val all = app.prefs.playlists.first()
            val target = all.firstOrNull { it.id == playlist.id } ?: return@launch
            if (target.songs.any { it.playId == song.playId }) { Toaster.show("Already in “${target.name}”"); return@launch }
            app.prefs.savePlaylists(all.map { if (it.id == playlist.id) it.copy(songs = it.songs + song) else it })
            Toaster.show("Added to “${target.name}”")
        }
    }

    fun createPlaylist(name: String, firstSong: Song? = null) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        app.appScope.launch {
            val all = app.prefs.playlists.first()
            app.prefs.savePlaylists(all + Playlist(id = UUID.randomUUID().toString(), name = clean, songs = listOfNotNull(firstSong)))
            Toaster.show(if (firstSong != null) "Added to “$clean”" else "Created “$clean”")
        }
    }

    fun removeFromPlaylist(playlistId: String, song: Song) {
        app.appScope.launch {
            val all = app.prefs.playlists.first()
            app.prefs.savePlaylists(all.map { if (it.id == playlistId) it.copy(songs = it.songs.filterNot { s -> s.playId == song.playId }) else it })
        }
    }

    fun deletePlaylist(playlistId: String) {
        app.appScope.launch { app.prefs.savePlaylists(app.prefs.playlists.first().filterNot { it.id == playlistId }) }
    }

    fun renamePlaylist(playlistId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        app.appScope.launch {
            app.prefs.savePlaylists(app.prefs.playlists.first().map { if (it.id == playlistId) it.copy(name = clean) else it })
            Toaster.show("Renamed to “$clean”")
        }
    }

    /**
     * Keeps a copy of a playlist from elsewhere (a YouTube playlist, your Home mix) in your library.
     * Saving the same one twice refreshes its songs instead of making a duplicate.
     */
    fun savePlaylist(name: String, songs: List<Song>, sourceUrl: String, author: String? = null, coverUrl: String? = null) {
        if (songs.isEmpty()) { Toaster.show("Nothing to save"); return }
        app.appScope.launch {
            val all = app.prefs.playlists.first()
            val existing = all.firstOrNull { it.sourceUrl == sourceUrl }
            if (existing != null) {
                app.prefs.savePlaylists(all.map { if (it.id == existing.id) it.copy(songs = songs.distinctBy { s -> s.playId }) else it })
                Toaster.show("Updated “${existing.name}” in your library")
            } else {
                val saved = Playlist(
                    id = UUID.randomUUID().toString(),
                    name = name.trim().ifBlank { "Saved playlist" },
                    songs = songs.distinctBy { it.playId },
                    sourceUrl = sourceUrl,
                    author = author,
                    coverUrl = coverUrl,
                )
                app.prefs.savePlaylists(all + saved)
                Toaster.show("Saved “${saved.name}” to your library")
            }
        }
    }
}

/** One short confirmation at a time ("Added to queue"), shown by [ToastHost]. */
object Toaster {
    private val _message = MutableStateFlow<Pair<String, Long>?>(null)
    val message: StateFlow<Pair<String, Long>?> = _message
    fun show(text: String) { _message.value = text to System.nanoTime() }
    internal fun clear() { _message.value = null }
}

/** A small glass note that rises, waits, and leaves. Place once, above the bottom chrome. */
@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val message by Toaster.message.collectAsState()
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        shown = m.first
        delay(2_200)
        Toaster.clear()
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(160)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 450f)) { it },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
        modifier = modifier,
    ) {
        Text(
            shown,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .glass(Radius.pill, Glass.Frosted, tint = Color(0xE6141418))
                .padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

/** An extra, context-specific entry for [SongMenu] (e.g. "Remove from this playlist"). */
class SongMenuAction(val label: String, val icon: ImageVector, val destructive: Boolean = false, val onClick: () -> Unit)

/** The "⋮" button for a song row: opens [SongMenu] anchored to itself. */
@Composable
fun SongMenuButton(song: Song, modifier: Modifier = Modifier, extra: List<SongMenuAction> = emptyList(), tint: Color = Color.White.copy(alpha = 0.55f)) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier.size(44.dp).pressable(onClick = { open = true }), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MoreHoriz, contentDescription = "More options for ${song.title}", tint = tint, modifier = Modifier.size(22.dp))
        if (open) SongMenu(song = song, extra = extra, onDismiss = { open = false })
    }
}

/**
 * A small glass panel of song actions, anchored where it was opened. "Add to playlist" slides to
 * a second page (with "New playlist" right there) instead of stacking another dialog on top.
 */
@Composable
fun SongMenu(song: Song, onDismiss: () -> Unit, extra: List<SongMenuAction> = emptyList()) {
    val prefs = DaydreaminApp.instance.prefs
    val likedIds by prefs.likedIds.collectAsState(initial = emptySet())
    var page by remember { mutableStateOf(0) }
    var naming by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val liked = song.playId in likedIds

    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(0, 40.dp.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 5 * dir }) togetherWith
                    (fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { -it / 5 * dir })
            },
            label = "songMenuPage",
            modifier = Modifier
                .width(264.dp)
                .glass(RoundedCornerShape(20.dp), Glass.Regular, tint = Color(0xF2141418)),
        ) { p ->
            Column(Modifier.padding(vertical = 6.dp)) {
                if (p == 0) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(url = song.artworkUrl, shape = Radius.thumbShape, modifier = Modifier.size(40.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(song.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Divider()
                    MenuRow(Icons.Rounded.SkipNext, "Play next") { SongActions.playNext(song); onDismiss() }
                    MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { SongActions.addToQueue(song); onDismiss() }
                    MenuRow(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Remove from Liked" else "Add to Liked") {
                        SongActions.toggleLiked(song, liked); onDismiss()
                    }
                    MenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", trailing = Icons.Rounded.ChevronRight) { page = 1 }
                    extra.forEach { a -> MenuRow(a.icon, a.label, destructive = a.destructive) { a.onClick(); onDismiss() } }
                } else {
                    MenuRow(Icons.Rounded.ChevronLeft, "Add to playlist", dim = true) { page = 0 }
                    Divider()
                    PlaylistPickerRows(song, onNewPlaylist = { naming = true }, onPicked = onDismiss)
                }
            }
        }
    }
    if (naming) NewPlaylistDialog(song, onDismiss = { naming = false }, onCreated = { naming = false; onDismiss() })
}

/**
 * "New playlist…", then every playlist you have — ticked where [song] already is. Tapping one
 * adds the song and calls [onPicked]. Shared by the song menu and Now Playing's menu.
 */
@Composable
fun PlaylistPickerRows(song: Song, onNewPlaylist: () -> Unit, onPicked: () -> Unit) {
    val playlists by DaydreaminApp.instance.prefs.playlists.collectAsState(initial = emptyList())
    MenuRow(Icons.Rounded.Add, "New playlist…", onClick = onNewPlaylist)
    Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
        playlists.forEach { pl ->
            val has = pl.songs.any { it.playId == song.playId }
            MenuRow(
                if (has) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.QueueMusic,
                pl.name,
                dim = has,
            ) { SongActions.addToPlaylist(pl, song); onPicked() }
        }
    }
}

/** Names a new playlist with [song] already in it. */
@Composable
fun NewPlaylistDialog(song: Song, onDismiss: () -> Unit, onCreated: () -> Unit) {
    NameDialog(
        title = "New playlist",
        confirm = "Create",
        onDismiss = onDismiss,
        onConfirm = { name -> SongActions.createPlaylist(name, firstSong = song); onCreated() },
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(0.7.dp).background(Color.White.copy(alpha = 0.08f)))
}

@Composable
internal fun MenuRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    dim: Boolean = false,
    trailing: ImageVector? = null,
    onClick: () -> Unit,
) {
    val color = when { destructive -> Color(0xFFFF6B61); dim -> Color.White.copy(alpha = 0.6f); else -> Color.White }
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing != null) Icon(trailing, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

/** Glass text-entry card (playlist names) — replaces stock Material alert dialogs. */
@Composable
fun NameDialog(title: String, confirm: String, initial: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(24.dp), Glass.Regular, tint = Color(0xF2141418))
                .padding(22.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), Glass.Clear).padding(horizontal = 14.dp, vertical = 13.dp),
                    ) {
                        if (text.isEmpty()) Text("Playlist name", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.4f))
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.pressable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    confirm,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (text.isBlank()) Color.Black.copy(alpha = 0.4f) else Color.Black,
                    modifier = Modifier
                        .pressable(onClick = { if (text.isNotBlank()) onConfirm(text) })
                        .glass(Radius.pill, Glass.Clear, tint = Color.White.copy(alpha = if (text.isBlank()) 0.5f else 1f))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}
