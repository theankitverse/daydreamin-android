package com.daydreamin.app.system

import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.platform.Log
import com.daydreamin.app.player.PlayerController
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** What the system's media controls show. */
data class NowPlaying(
    val song: Song,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val artworkUrl: String?,
)

/** One OS's media controls: shows [NowPlaying] and turns the system's buttons into [MediaCommands]. */
interface MediaBridge {
    fun update(state: NowPlaying?)
    fun close() {}
}

/** What the system's buttons, media keys and headphones can ask for. */
object MediaCommands {
    private fun log(what: String) = Log.d("SystemMedia", "media command: $what")
    fun play() { log("play"); if (!PlayerController.meta.value.isPlaying) PlayerController.togglePlayPause() }
    fun pause() { log("pause"); PlayerController.togglePlayPauseIfPlaying() }
    fun toggle() { log("toggle"); PlayerController.togglePlayPause() }
    fun next() { log("next"); PlayerController.next() }
    fun previous() { log("previous"); PlayerController.previous() }
    fun seekTo(ms: Long) { log("seek $ms"); PlayerController.seekTo(ms.coerceAtLeast(0)) }
}

/**
 * Connects the player to the system's media controls: macOS's Now Playing (Control Center, the
 * media keys, headphones), Linux's MPRIS (GNOME/KDE media controls and keys), and the media keys
 * on Windows. Any failure here only means no system controls — never a broken app.
 */
object SystemMedia {
    private const val TAG = "SystemMedia"
    private var bridge: MediaBridge? = null

    fun start() {
        val os = System.getProperty("os.name").lowercase()
        bridge = runCatching {
            when {
                "mac" in os -> MacNowPlaying()
                "win" in os -> WindowsMediaKeys()
                else -> LinuxMpris()
            }
        }.onFailure { Log.w(TAG, "no system media controls: ${it.message}", it) }.getOrNull() ?: return
        Log.i(TAG, "connected: ${bridge!!::class.simpleName}")

        // Song or play/pause changed, or the position jumped (a seek): tell the system. Between those,
        // the system works the position out itself from the rate, so there's no need to send every tick.
        AppGraph.appScope.launch {
            var last: NowPlaying? = null
            var lastAt = 0L
            combine(PlayerController.meta, PlayerController.progress) { meta, progress ->
                meta.currentSong?.let { NowPlaying(it, meta.isPlaying && !meta.isBuffering, progress.positionMs, progress.durationMs, it.artworkUrl.ifBlank { null }) }
            }.collect { state ->
                val now = System.currentTimeMillis()
                val l = last
                // Where the system thinks the song is: it advances the position itself while playing.
                val expected = if (l == null) 0L else if (l.playing) l.positionMs + (now - lastAt) else l.positionMs
                val changed = when {
                    state == null || l == null -> state != l
                    else -> state.song.playId != l.song.playId || state.playing != l.playing ||
                        state.durationMs != l.durationMs || kotlin.math.abs(state.positionMs - expected) > 2_000
                }
                if (changed) {
                    runCatching { bridge?.update(state) }.onFailure { Log.w(TAG, "update failed: ${it.message}") }
                    last = state
                    lastAt = now
                }
            }
        }
    }

    fun stop() {
        runCatching { bridge?.close() }
        bridge = null
    }
}
