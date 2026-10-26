package com.daydreamin.app.player

import com.daydreamin.app.data.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * The single owner of playback state for the whole app. Screens read [meta] / [progress] and call
 * these functions; each platform plays the audio its own way (Android: Media3 in a foreground
 * service; desktop: its own decoder).
 */
expect object PlayerController {
    val meta: StateFlow<PlaybackMeta>
    val progress: StateFlow<PlaybackProgress>

    /** Safe to call from anywhere, any number of times — sets up the player if it isn't yet. */
    fun ensureConnected()
    fun disconnect()

    /** Warms the first seconds of [song] so tapping it starts at once (Home's top pick). */
    suspend fun warmUp(song: Song)

    fun playSong(song: Song, queueContext: List<Song> = emptyList(), autoFillQueue: Boolean = true)
    fun playFromList(songs: List<Song>, startIndex: Int)
    fun togglePlayPause()
    fun togglePlayPauseIfPlaying()
    fun seekTo(ms: Long)
    fun next()
    fun previous()
    fun toggleShuffle()
    fun cycleRepeat()
    fun moveQueueItem(from: Int, to: Int)
    fun addToQueue(song: Song)
    fun removeFromQueue(song: Song)
    fun clearQueue()
    fun playQueueItemAt(index: Int)
    fun consumeError()
}
