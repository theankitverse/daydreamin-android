package com.daydreamin.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String = "Unknown",
    val artist: String = "Unknown",
    @SerialName("artist_id") val artistId: Long = 0,
    val album: String = "Single",
    val cover: String = "",
    @SerialName("cover_xl") val coverXl: String = "",
    val duration: Int = 0,
    val genre: String = "Music",
    val videoId: String? = null,
    val reason: String? = null,
) {
    val artworkUrl: String get() = coverXl.ifBlank { cover }
    /** Stable id to key play/queue calls with — falls back to the YouTube video id. */
    val playId: String get() = id.ifBlank { videoId.orEmpty() }
}

@Serializable
data class LyricsResponse(
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
)

/** A single parsed .lrc line used by the Lyrics screen. */
data class LyricLine(val timeMs: Long, val text: String)
