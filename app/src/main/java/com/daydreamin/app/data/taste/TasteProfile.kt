package com.daydreamin.app.data.taste

import android.content.Context
import com.daydreamin.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** How much you've played one song, and how often you've bailed on it early. */
@Serializable
data class PlayStat(
    val song: Song,
    val plays: Int = 0,
    val skips: Int = 0,
    val lastPlayedAtMs: Long = 0,
    /** The YouTube video it actually played from — lets recommendations seed a radio mix without searching first. */
    val videoId: String? = null,
) {
    /** Skipped early more often than it's been played through: not a song you want more of. */
    val mostlySkipped: Boolean get() = skips >= 2 && skips * 2 > plays
}

/**
 * What you actually listen to, beyond the plain recent-history list: how many times each song has
 * been played, how often it was skipped in its first seconds, and which YouTube video it resolved
 * to. The recommender reads this; nothing else needs to.
 *
 * Kept in its own small file rather than the preferences store — it's rewritten on every play.
 */
object TasteProfile {
    private const val MAX_ENTRIES = 600
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var file: File? = null
    private var stats: MutableMap<String, PlayStat>? = null

    private val _plays = MutableStateFlow(0)
    /** Plays recorded this session — the recommender refreshes once enough new ones pile up. */
    val playsThisSession: StateFlow<Int> = _plays

    fun init(context: Context) {
        file = File(context.filesDir, "taste.json")
    }

    suspend fun snapshot(): Map<String, PlayStat> = mutex.withLock { load().toMap() }

    suspend fun recordPlay(song: Song) {
        if (song.playId.isBlank()) return
        update(song) { it.copy(song = song.mergedWith(it), plays = it.plays + 1, lastPlayedAtMs = System.currentTimeMillis()) }
        _plays.value++
    }

    suspend fun recordSkip(song: Song) {
        if (song.playId.isBlank()) return
        update(song) { it.copy(skips = it.skips + 1) }
    }

    /** Only for songs already on record — songs merely buffered ahead and never heard don't belong here. */
    suspend fun rememberVideoId(song: Song, videoId: String) {
        if (song.playId.isBlank() || videoId.isBlank()) return
        update(song, createIfMissing = false) { if (it.videoId == videoId) it else it.copy(videoId = videoId) }
    }

    private suspend fun update(song: Song, createIfMissing: Boolean = true, change: (PlayStat) -> PlayStat) = mutex.withLock {
        val all = load()
        val existing = all[song.playId]
        if (existing == null && !createIfMissing) return@withLock
        val before = existing ?: PlayStat(song = song)
        val after = change(before)
        if (after == existing) return@withLock
        all[song.playId] = after
        if (all.size > MAX_ENTRIES) {
            // The least-played, longest-ago songs say the least about you now.
            all.values.sortedWith(compareBy<PlayStat> { it.plays }.thenBy { it.lastPlayedAtMs })
                .take(all.size - MAX_ENTRIES)
                .forEach { all.remove(it.song.playId) }
        }
        save(all)
    }

    private suspend fun load(): MutableMap<String, PlayStat> {
        stats?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            runCatching { file?.takeIf { it.exists() }?.readText()?.let { json.decodeFromString<List<PlayStat>>(it) } }.getOrNull()
        }.orEmpty().associateByTo(mutableMapOf()) { it.song.playId }
        stats = loaded
        return loaded
    }

    private suspend fun save(all: Map<String, PlayStat>) = withContext(Dispatchers.IO) {
        val target = file ?: return@withContext
        runCatching {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(json.encodeToString(all.values.toList()))
            tmp.renameTo(target)
        }
    }

    /** The newest metadata for the song, but never forget a video id we already learned. */
    private fun Song.mergedWith(stat: PlayStat): Song = if (videoId.isNullOrBlank() && stat.song.videoId != null) copy(videoId = stat.song.videoId) else this
}
