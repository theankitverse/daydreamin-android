package com.daydreamin.app.desktop.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import com.daydreamin.app.data.library.PlaylistImport
import com.daydreamin.app.platform.PickedFile
import java.io.File
import java.net.URI

private val PLAYLIST_FILES = setOf("csv", "m3u", "m3u8", "txt")

/** Playlist files dropped on the window are imported (what Exportify downloads, say). */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun Modifier.acceptsPlaylistFiles(): Modifier {
    val target = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val files = (event.dragData() as? DragData.FilesList)?.readFiles().orEmpty()
                    .mapNotNull { runCatching { File(URI(it)) }.getOrNull() }
                    .filter { it.extension.lowercase() in PLAYLIST_FILES }
                files.forEach { PlaylistImport.import(PickedFile(it)) }
                return files.isNotEmpty()
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { it.dragData() is DragData.FilesList }, target = target)
}
