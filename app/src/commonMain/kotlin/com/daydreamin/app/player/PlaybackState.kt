package com.daydreamin.app.player

import com.daydreamin.app.data.model.Song
import kotlinx.serialization.Serializable

@Serializable
enum class RepeatMode { OFF, ALL, ONE }

/** Everything needed to resume exactly where playback left off after process death or a
 *  fresh app launch — the current track, the rest of the queue, playback position, and the
 *  shuffle/repeat toggles. Persisted as JSON via [com.daydreamin.app.data.prefs.AppPreferences]. */
@Serializable
data class PlaybackSnapshot(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
)

/**
 * Everything that changes rarely — song, transport state, queue. Split out from
 * [PlaybackProgress] so screens that don't render a scrubber (Home, Search, Library,
 * the bottom bar) don't recompose twice a second along with it.
 */
data class PlaybackMeta(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Song> = emptyList(),
    val error: String? = null,
    /** e.g. "Opus · 160 kbps" — the actual stream quality in use, for transparency. */
    val streamQuality: String? = null,
)

/** Ticks every 500ms during playback — kept separate from [PlaybackMeta] on purpose. */
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)
