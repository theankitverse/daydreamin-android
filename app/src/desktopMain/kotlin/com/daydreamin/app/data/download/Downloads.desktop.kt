package com.daydreamin.app.data.download

import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.ui.components.Toaster
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Placeholder until the desktop player (Phase 2) — nothing is downloaded yet.
actual object Downloads {
    actual val items: StateFlow<Map<String, DownloadInfo>> = MutableStateFlow(emptyMap())
    actual fun isDownloaded(playId: String): Boolean = false
    actual fun downloadedSong(playId: String): Song? = null
    actual fun bytesUsed(): Long = 0L
    actual fun download(song: Song): Job = AppGraph.appScope.launch { Toaster.show("Downloads are coming to the desktop app soon") }
    actual suspend fun remove(song: Song): String? = null
    actual fun retryFailed() {}
    actual suspend fun removeAll() {}
}
