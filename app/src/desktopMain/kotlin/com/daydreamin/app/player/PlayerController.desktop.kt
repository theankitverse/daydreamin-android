package com.daydreamin.app.player

import com.daydreamin.app.data.model.Song
import com.daydreamin.app.ui.components.Toaster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Placeholder until the desktop player (Phase 2): keeps the queue so every screen works, but plays no sound.
actual object PlayerController {
    private val _meta = MutableStateFlow(PlaybackMeta())
    actual val meta: StateFlow<PlaybackMeta> = _meta
    private val _progress = MutableStateFlow(PlaybackProgress())
    actual val progress: StateFlow<PlaybackProgress> = _progress

    actual fun ensureConnected() {}
    actual fun disconnect() {}
    actual suspend fun warmUp(song: Song) {}

    actual fun playSong(song: Song, queueContext: List<Song>, autoFillQueue: Boolean) {
        val queue = queueContext.ifEmpty { listOf(song) }
        _meta.value = _meta.value.copy(currentSong = song, queue = queue, isPlaying = false)
        Toaster.show("Playback comes to the desktop app in the next step")
    }
    actual fun playFromList(songs: List<Song>, startIndex: Int) { songs.getOrNull(startIndex)?.let { playSong(it, songs) } }
    actual fun togglePlayPause() {}
    actual fun togglePlayPauseIfPlaying() {}
    actual fun seekTo(ms: Long) {}
    actual fun next() { move(1) }
    actual fun previous() { move(-1) }
    private fun move(by: Int) {
        val m = _meta.value
        val i = m.queue.indexOfFirst { it.playId == m.currentSong?.playId }
        m.queue.getOrNull(i + by)?.let { _meta.value = m.copy(currentSong = it) }
    }
    actual fun toggleShuffle() { _meta.value = _meta.value.copy(shuffle = !_meta.value.shuffle) }
    actual fun cycleRepeat() { _meta.value = _meta.value.copy(repeatMode = RepeatMode.entries[(_meta.value.repeatMode.ordinal + 1) % RepeatMode.entries.size]) }
    actual fun moveQueueItem(from: Int, to: Int) {
        val q = _meta.value.queue.toMutableList()
        if (from !in q.indices || to !in q.indices) return
        q.add(to, q.removeAt(from)); _meta.value = _meta.value.copy(queue = q)
    }
    actual fun addToQueue(song: Song) { _meta.value = _meta.value.copy(queue = _meta.value.queue + song) }
    actual fun removeFromQueue(song: Song) { _meta.value = _meta.value.copy(queue = _meta.value.queue.filterNot { it.playId == song.playId }) }
    actual fun clearQueue() { _meta.value = _meta.value.copy(queue = listOfNotNull(_meta.value.currentSong)) }
    actual fun playQueueItemAt(index: Int) { _meta.value.queue.getOrNull(index)?.let { _meta.value = _meta.value.copy(currentSong = it) } }
    actual fun consumeError() { _meta.value = _meta.value.copy(error = null) }
}
