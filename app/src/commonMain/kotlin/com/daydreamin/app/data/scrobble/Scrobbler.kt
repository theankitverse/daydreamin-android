package com.daydreamin.app.data.scrobble

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.daydreamin.app.AppGraph
import com.daydreamin.app.AppInfo
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.remote.NetworkModule
import com.daydreamin.app.data.taste.recapArtist
import com.daydreamin.app.platform.Log
import com.daydreamin.app.platform.openUrl
import com.daydreamin.app.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private const val TAG = "Scrobbler"
private const val API = "https://ws.audioscrobbler.com/2.0/"
/** Last.fm's rule: a song counts once half of it has played, or four minutes, whichever comes first. */
private const val MAX_WAIT_MS = 240_000L
/** And songs under 30 seconds don't count at all. */
private const val MIN_SONG_MS = 30_000L
/** Most scrobbles one request can carry. */
private const val BATCH = 50
/** Kept while offline; a month of heavy listening. */
private const val MAX_PENDING = 2_000

/** A listen on its way to Last.fm. */
@Serializable
data class Scrobble(val artist: String, val track: String, val album: String? = null, val startedAtSec: Long, val durationSec: Int = 0)

/** Last.fm's request signature: every parameter in name order, then the secret, through MD5. */
internal fun lastFmSignature(params: Map<String, String>, secret: String): String {
    val text = params.toSortedMap().entries.joinToString("") { it.key + it.value } + secret
    return MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** The name Last.fm should see: video dressing off. YouTube titles aren't release titles. */
internal fun scrobbleTitle(raw: String): String = raw
    .replace(Regex("""(?i)^\s*lyrical\s*:\s*"""), "")
    .replace(Regex("""(?i)\s*[(\[](?:official\s*)?(?:music\s*|lyric(?:al)?\s*|full\s*)?(?:video|audio|song|lyrics?)(?:\s*song)?[)\]]"""), "")
    .replace(Regex("""(?i)\s+lyrical(?:\s+video)?\b.*$"""), "")
    .trim()

/**
 * Decides when the song playing has been heard enough to count, from how long it has really
 * played. A song played again later counts again; paused and buffering time doesn't count.
 */
class ScrobbleTimer(private val onNowPlaying: (Song) -> Unit, private val onScrobble: (Song, Long) -> Unit) {
    private var song: Song? = null
    private var startedAtMs = 0L
    private var heardMs = 0L
    private var lastTickMs = 0L
    private var done = false
    private var announced = false

    fun tick(nowMs: Long, current: Song?, sounding: Boolean, durationMs: Long) {
        if (current?.playId != song?.playId) {
            song = current
            startedAtMs = nowMs
            heardMs = 0
            done = false
            announced = false
        } else if (sounding) {
            heardMs += (nowMs - lastTickMs).coerceIn(0, 5_000)
        }
        lastTickMs = nowMs
        val s = song ?: return
        if (sounding && !announced) { announced = true; onNowPlaying(s) }
        if (!done && durationMs > MIN_SONG_MS && heardMs >= minOf(durationMs / 2, MAX_WAIT_MS)) {
            done = true
            onScrobble(s, startedAtMs)
        }
    }
}

/**
 * Tells Last.fm what you listen to, if you've connected an account. Listens made offline wait in a
 * small file and go out later. The app's Last.fm key is added at build time (it's not in the code):
 * a build without one simply doesn't offer this.
 */
object Scrobbler {
    /** Who's connected, for Settings. */
    var user by mutableStateOf<String?>(null)
        private set
    var connecting by mutableStateOf(false)
        private set

    val available: Boolean get() = AppInfo.lastFmKey.isNotBlank() && AppInfo.lastFmSecret.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var session: String? = null
    private var connectJob: Job? = null
    private val file get() = File(AppGraph.filesDir, "scrobbles_pending.json")

    fun start(scope: CoroutineScope) {
        if (!available) return
        scope.launch {
            session = AppGraph.prefs.lastFmSession.first()
            user = AppGraph.prefs.lastFmUser.first()
            send()
            val timer = ScrobbleTimer(
                onNowPlaying = { song -> scope.launch { nowPlaying(song) } },
                onScrobble = { song, startedAt -> scope.launch { queue(song, startedAt); send() } },
            )
            while (true) {
                val meta = PlayerController.meta.value
                timer.tick(System.currentTimeMillis(), meta.currentSong, meta.isPlaying && !meta.isBuffering, PlayerController.progress.value.durationMs)
                delay(1_000)
            }
        }
    }

    /** Opens Last.fm to approve the app, then waits for the approval to come through. */
    fun connect(scope: CoroutineScope) {
        if (connecting) return
        connecting = true
        connectJob = scope.launch {
            try {
                val token = call(mapOf("method" to "auth.getToken"))["token"]!!.jsonPrimitive.content
                openUrl("https://www.last.fm/api/auth/?api_key=${AppInfo.lastFmKey}&token=$token", "Couldn't open the browser")
                repeat(60) { // two minutes
                    delay(2_000)
                    val answer = runCatching { call(mapOf("method" to "auth.getSession", "token" to token)) }.getOrNull()
                    val s = answer?.get("session")?.jsonObject ?: return@repeat
                    val key = s["key"]!!.jsonPrimitive.content
                    val name = s["name"]!!.jsonPrimitive.content
                    AppGraph.prefs.setLastFm(key, name)
                    session = key
                    user = name
                    com.daydreamin.app.ui.components.Toaster.show("Connected to Last.fm as $name")
                    return@launch
                }
            } catch (e: Exception) {
                Log.w(TAG, "connect failed: ${e.message}")
                com.daydreamin.app.ui.components.Toaster.show("Couldn't reach Last.fm")
            } finally {
                connecting = false
            }
        }
    }

    fun disconnect(scope: CoroutineScope) {
        connectJob?.cancel()
        session = null
        user = null
        scope.launch { AppGraph.prefs.setLastFm(null, null); mutex.withLock { file.delete() } }
    }

    private suspend fun nowPlaying(song: Song) {
        val key = session ?: return
        val artist = recapArtist(song.artist) ?: return
        runCatching {
            call(buildMap {
                put("method", "track.updateNowPlaying")
                put("artist", artist)
                put("track", scrobbleTitle(song.title))
                put("sk", key)
            })
        }.onFailure { Log.d(TAG, "now playing not sent: ${it.message}") }
    }

    private suspend fun queue(song: Song, startedAtMs: Long) {
        session ?: return
        // A label's channel isn't the artist, and Last.fm would file the song under it.
        val artist = recapArtist(song.artist) ?: return
        val entry = Scrobble(artist, scrobbleTitle(song.title), song.album.takeIf { it.isNotBlank() && it != "Single" }, startedAtMs / 1000, song.duration)
        mutex.withLock { save((load() + entry).takeLast(MAX_PENDING)) }
    }

    private suspend fun send() {
        val key = session ?: return
        mutex.withLock {
            var pending = load()
            while (pending.isNotEmpty()) {
                val batch = pending.take(BATCH)
                try {
                    call(scrobbleParams(batch, key))
                } catch (e: IOException) {
                    Log.d(TAG, "scrobbles wait: ${e.message}")
                    break
                }
                pending = pending.drop(batch.size)
                save(pending)
            }
        }
    }

    internal fun scrobbleParams(batch: List<Scrobble>, key: String): Map<String, String> = buildMap {
        put("method", "track.scrobble")
        put("sk", key)
        batch.forEachIndexed { i, s ->
            put("artist[$i]", s.artist)
            put("track[$i]", s.track)
            put("timestamp[$i]", s.startedAtSec.toString())
            s.album?.let { put("album[$i]", it) }
            if (s.durationSec > 0) put("duration[$i]", s.durationSec.toString())
        }
    }

    private fun load(): List<Scrobble> = runCatching { json.decodeFromString<List<Scrobble>>(file.readText()) }.getOrDefault(emptyList())

    private fun save(list: List<Scrobble>) {
        runCatching { if (list.isEmpty()) file.delete() else file.writeText(json.encodeToString(list)) }
    }

    /** A signed call; the answer's JSON. Throws [IOException] for anything worth trying again later. */
    private suspend fun call(params: Map<String, String>) = withContext(Dispatchers.IO) {
        val signed = params + ("api_key" to AppInfo.lastFmKey)
        val form = FormBody.Builder().apply {
            signed.forEach { (k, v) -> add(k, v) }
            add("api_sig", lastFmSignature(signed, AppInfo.lastFmSecret))
            add("format", "json")
        }.build()
        NetworkModule.http.newCall(Request.Builder().url(API).post(form).build()).execute().use { response ->
            val body = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            body["error"]?.let { throw IOException("Last.fm error ${it.jsonPrimitive.content}: ${body["message"]?.jsonPrimitive?.content}") }
            body
        }
    }
}
