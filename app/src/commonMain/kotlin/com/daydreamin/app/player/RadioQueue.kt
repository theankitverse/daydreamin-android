package com.daydreamin.app.player

import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.repository.toSong
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/** A song's radio shorter than this (after the per-artist cap) gets extended — see [buildRadioQueue]. */
private const val MIN_RADIO_LENGTH = 15

/**
 * Up Next for [song] (whose YouTube video is [videoId]): YouTube Music's radio mix for it — the
 * same "Start radio" behind YT Music — without songs you just heard, at most three per artist,
 * and extended with the radios of a couple of other artists in it when it comes back short.
 * Shared by the Android and desktop players.
 */
suspend fun buildRadioQueue(song: Song, videoId: String): List<Song> {
    val app = AppGraph
    val recentTitles = app.prefs.history.first().take(15)
        .map { YouTubeExtractorService.normalizeTitleForDedup(it.title) }
        .toSet()

    val primaryMix = YouTubeExtractorService
        .fetchRadioMix(videoId, song.title, song.artist, recentTitles)
        .map { it.toSong() }
        .filterNot { it.playId == song.playId }

    // No more than a few tracks per artist, no matter how narrow the radio was.
    var radioTracks = primaryMix
        .distinctBy { YouTubeExtractorService.normalizeTitleForDedup(it.title) }
        .capPerArtist(max = 3)

    if (radioTracks.size < MIN_RADIO_LENGTH) {
        val songArtist = song.artist.trim().lowercase()
        val extenders = radioTracks
            .filter { !it.videoId.isNullOrBlank() && it.artist.trim().lowercase() != songArtist }
            .distinctBy { it.artist.trim().lowercase() }
            .take(2)
        val known = recentTitles + primaryMix.map { YouTubeExtractorService.normalizeTitleForDedup(it.title) }
        val extensions = coroutineScope {
            extenders.map { t ->
                async {
                    runCatching { YouTubeExtractorService.fetchRadioMix(t.videoId!!, t.title, t.artist, known) }
                        .getOrDefault(emptyList())
                        .map { it.toSong() }
                        .filterNot { it.playId == song.playId }
                }
            }.awaitAll()
        }
        radioTracks = interleaveMany(radioTracks, extensions)
            .distinctBy { YouTubeExtractorService.normalizeTitleForDedup(it.title) }
            .capPerArtist(max = 3)
    }
    return radioTracks
}

/** Weaves each list in [secondaries] into [primary] every third slot instead of just appending them, so an extended radio doesn't read as "one song's radio, then a tacked-on chunk". */
internal fun interleaveMany(primary: List<Song>, secondaries: List<List<Song>>): List<Song> {
    if (secondaries.all { it.isEmpty() }) return primary
    val leftovers = secondaries.map { ArrayDeque(it) }
    val result = mutableListOf<Song>()
    primary.forEachIndexed { index, song ->
        result.add(song)
        if ((index + 1) % 3 == 0) {
            leftovers.forEach { queue -> if (queue.isNotEmpty()) result.add(queue.removeFirst()) }
        }
    }
    leftovers.forEach { result.addAll(it) }
    return result.distinctBy { it.playId }
}

/** Same artist (or artist-component, for collab credits like "KR$NA, Seedhe Maut") never
 *  appears more than [max] times — the actual fix for "recommendations are just the same
 *  one or two artists", regardless of how narrow the underlying radio mix was. */
internal fun List<Song>.capPerArtist(max: Int): List<Song> {
    val counts = mutableMapOf<String, Int>()
    return filter { song ->
        val components = song.artist.lowercase()
            .split(Regex("""[,&/]|\bx\b|\bfeat\.?\b|\bft\.?\b|\bwith\b"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (components.isEmpty()) return@filter true
        if (components.any { (counts[it] ?: 0) >= max }) return@filter false
        components.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        true
    }
}
