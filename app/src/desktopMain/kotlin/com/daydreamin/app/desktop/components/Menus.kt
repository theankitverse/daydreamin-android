package com.daydreamin.app.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberCursorPositionProvider
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.ui.components.SongActions
import com.daydreamin.app.ui.components.Toaster
import com.daydreamin.app.ui.components.toggleDownload
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** One line in a menu. A null in a menu's list is a divider. */
class MenuEntry(
    val label: String,
    val icon: ImageVector? = null,
    val shortcut: String? = null,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    /** Opens beside this entry on hover ("Add to playlist ›"). */
    val submenu: (() -> List<MenuEntry?>)? = null,
    val onClick: () -> Unit = {},
)

/**
 * Right-click (or Ctrl-click on a Mac) anywhere on [content] opens [entries] as a menu at the
 * pointer, the way desktop apps do.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContextMenuArea(entries: () -> List<MenuEntry?>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier.onPointerEvent(PointerEventType.Press) { event ->
            val ctrlClick = event.keyboardModifiers.isCtrlPressed && System.getProperty("os.name").lowercase().contains("mac")
            if (event.buttons.isSecondaryPressed || (ctrlClick && event.buttons.isPrimaryPressed)) open = true
        },
    ) {
        content()
        if (open) MenuPopup(entries(), onDismiss = { open = false })
    }
}

/** A menu at the pointer (right-click menus, the ⋯ button). */
@Composable
fun MenuPopup(entries: List<MenuEntry?>, onDismiss: () -> Unit) {
    Popup(
        popupPositionProvider = rememberCursorPositionProvider(),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onDismiss(); true } else false },
    ) {
        MenuSurface(entries, onDismiss)
    }
}

@Composable
private fun MenuSurface(entries: List<MenuEntry?>, onDismiss: () -> Unit) {
    Column(
        Modifier
            .widthIn(min = 210.dp, max = 300.dp)
            .heightIn(max = 460.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DColors.popup)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
    ) {
        entries.forEach { entry ->
            if (entry == null) {
                Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(DColors.divider))
            } else {
                MenuRow(entry, onDismiss)
            }
        }
    }
}

@Composable
private fun MenuRow(entry: MenuEntry, onDismiss: () -> Unit) {
    val (source, hovered) = rememberHover()
    val color = when {
        !entry.enabled -> Color.White.copy(alpha = 0.3f)
        entry.danger -> Color(0xFFFF8A80)
        else -> Color.White
    }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .hoverable(source)
                .desktopClickable(RoundedCornerShape(5.dp), enabled = entry.enabled) {
                    if (entry.submenu == null) { onDismiss(); entry.onClick() }
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.icon != null) {
                Icon(entry.icon, contentDescription = null, tint = color.copy(alpha = color.alpha * 0.8f), modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(entry.label, style = DType.body.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (entry.shortcut != null) Text(entry.shortcut, style = DType.small.copy(color = Color.White.copy(alpha = 0.4f)), modifier = Modifier.padding(start = 16.dp))
            if (entry.submenu != null) Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = color.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        }
        if (entry.submenu != null && hovered) {
            Popup(popupPositionProvider = BesideProvider, onDismissRequest = {}, properties = PopupProperties(focusable = false)) {
                MenuSurface(entry.submenu.invoke(), onDismiss)
            }
        }
    }
}

/** A submenu: to the right of its entry, or to the left when there's no room. */
private object BesideProvider : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val right = anchorBounds.right + 4
        val x = if (right + popupContentSize.width > windowSize.width) anchorBounds.left - popupContentSize.width - 4 else right
        val y = (anchorBounds.top - 4).coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/**
 * Everything you can do with a song, for its right-click menu and ⋯ button. [onPlay] plays it in
 * its list's context; [extra] adds what only that place can do ("Remove from this playlist").
 * Cheap: the menu is only built when it opens, from state collected once for the window.
 */
@Composable
fun songMenuEntries(song: Song, onPlay: () -> Unit, extra: List<MenuEntry?> = emptyList()): () -> List<MenuEntry?> {
    val menus = LocalShared.current.menus
    return { menus.entries(song, onPlay, extra) }
}

/** Builds song menus from the window's shared state (see [ProvideSharedState]). */
class SongMenus(
    private val likedIds: State<Set<String>>,
    private val playlists: State<List<com.daydreamin.app.data.prefs.Playlist>>,
    private val downloads: State<Map<String, com.daydreamin.app.data.download.DownloadInfo>>,
    private val nav: com.daydreamin.app.desktop.nav.Navigator,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val newPlaylistFor: (Song) -> Unit,
) {
    fun entries(song: Song, onPlay: () -> Unit, extra: List<MenuEntry?> = emptyList()): List<MenuEntry?> {
        val liked = song.playId in likedIds.value
        val download = downloads.value[song.playId]
        return buildList {
            add(MenuEntry("Play", Icons.Rounded.PlayArrow, onClick = onPlay))
            add(MenuEntry("Play next", Icons.Rounded.SkipNext) { SongActions.playNext(song) })
            add(MenuEntry("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) { SongActions.addToQueue(song) })
            add(null)
            add(MenuEntry(if (liked) "Remove from Liked songs" else "Save to Liked songs", if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder) { SongActions.toggleLiked(song, liked) })
            add(
                MenuEntry("Add to playlist", Icons.AutoMirrored.Rounded.PlaylistAdd, submenu = {
                    buildList {
                        add(MenuEntry("New playlist…", Icons.Rounded.Add) { newPlaylistFor(song) })
                        if (playlists.value.isNotEmpty()) add(null)
                        playlists.value.forEach { p -> add(MenuEntry(p.name) { SongActions.addToPlaylist(p, song) }) }
                    }
                }),
            )
            add(
                MenuEntry(
                    when (download?.status) {
                        null -> "Download"
                        com.daydreamin.app.data.download.DownloadStatus.DONE -> "Remove download"
                        com.daydreamin.app.data.download.DownloadStatus.FAILED -> "Retry download"
                        else -> "Cancel download"
                    },
                    if (download?.status == com.daydreamin.app.data.download.DownloadStatus.DONE) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                ) { toggleDownload(song, download, scope) },
            )
            add(null)
            add(MenuEntry("Go to artist", Icons.Rounded.Person) { nav.go(Route.Artist(com.daydreamin.app.data.recommend.Recommender.primaryArtist(song.artist))) })
            song.videoId?.takeIf { it.isNotBlank() }?.let { id ->
                add(MenuEntry("Copy song link", Icons.Rounded.Link) {
                    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection("https://music.youtube.com/watch?v=$id"), null) }
                    Toaster.show("Link copied")
                })
            }
            if (extra.isNotEmpty()) { add(null); addAll(extra) }
        }
    }
}
