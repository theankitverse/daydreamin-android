package com.daydreamin.app.data.taste

import com.daydreamin.app.data.model.Song
import com.daydreamin.app.platform.Log
import com.daydreamin.app.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** One song you really listened to: when, and for how long. */
@Serializable
data class ListenEntry(
    val atMs: Long,
    val playId: String,
    val title: String,
    val artist: String,
    val cover: String = "",
    val listenedMs: Long,
)

/** Less than this of a song isn't a listen: a skip, or a look at what's next. */
const val MIN_LISTEN_MS = 30_000L

/**
 * Adds up the time a song actually plays (paused and buffering don't count), and hands over an
 * entry when the song changes. Kept apart from the clock and the file so it can be tested alone.
 */
class ListenTimer(private val onListen: (ListenEntry) -> Unit) {
    private var song: Song? = null
    private var startedAtMs = 0L
    private var listenedMs = 0L
    private var lastTickMs = 0L

    fun tick(nowMs: Long, current: Song?, sounding: Boolean) {
        if (current?.playId != song?.playId) {
            finish()
            song = current
            startedAtMs = nowMs
            listenedMs = 0
        } else if (sounding) {
            // Capped: a laptop waking from sleep isn't hours of listening.
            listenedMs += (nowMs - lastTickMs).coerceIn(0, 5_000)
        }
        lastTickMs = nowMs
    }

    private fun finish() {
        val s = song ?: return
        if (listenedMs < MIN_LISTEN_MS) return
        onListen(ListenEntry(startedAtMs, s.playId, s.title, s.artist, s.cover.ifBlank { s.artworkUrl }, listenedMs))
    }
}

/**
 * Every song listened to, with its date: what Your Year in Music is made from. [TasteProfile]
 * counts plays but keeps no dates, and the history list only the last few hundred songs. One line
 * of JSON per listen, added to the end of a file on this device (and included in library backups).
 */
object ListeningLog {
    /** Newest listens carried in a library backup, so a backup stays a small file. */
    const val BACKUP_LIMIT = 5_000

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var file: File? = null

    fun init(filesDir: File) {
        file = File(filesDir, "listening_log.jsonl")
    }

    /** Watches the player from now on, on every platform alike. */
    fun track(scope: CoroutineScope) {
        val timer = ListenTimer { entry -> scope.launch { add(listOf(entry)) } }
        scope.launch {
            while (true) {
                val meta = PlayerController.meta.value
                timer.tick(System.currentTimeMillis(), meta.currentSong, meta.isPlaying && !meta.isBuffering)
                delay(1_000)
            }
        }
    }

    suspend fun entries(): List<ListenEntry> = withContext(Dispatchers.IO) {
        mutex.withLock { read() }
    }

    /** Listens from a backup that aren't here yet; how many were added. */
    suspend fun merge(incoming: List<ListenEntry>): Int = withContext(Dispatchers.IO) {
        if (incoming.isEmpty()) return@withContext 0
        mutex.withLock {
            val known = read().mapTo(HashSet()) { it.atMs to it.playId }
            val fresh = incoming.filter { (it.atMs to it.playId) !in known }
            write(fresh)
            fresh.size
        }
    }

    private suspend fun add(new: List<ListenEntry>) = withContext(Dispatchers.IO) {
        mutex.withLock { write(new) }
    }

    private fun read(): List<ListenEntry> {
        val f = file?.takeIf { it.exists() } ?: return emptyList()
        return f.useLines { lines -> lines.mapNotNull { runCatching { json.decodeFromString<ListenEntry>(it) }.getOrNull() }.toList() }
    }

    private fun write(new: List<ListenEntry>) {
        val f = file ?: return
        runCatching { f.appendText(new.joinToString("") { json.encodeToString(it) + "\n" }) }
            .onFailure { Log.w("ListeningLog", "couldn't save a listen: ${it.message}") }
    }
}
