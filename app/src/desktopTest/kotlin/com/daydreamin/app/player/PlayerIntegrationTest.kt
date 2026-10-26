package com.daydreamin.app.player

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.Region
import com.daydreamin.app.data.download.DownloadStatus
import com.daydreamin.app.data.download.Downloads
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.AppPreferences
import com.daydreamin.app.data.repository.toSong
import com.daydreamin.app.data.taste.TasteProfile
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The real desktop player against real YouTube and this machine's audio output — one long scenario,
 * since the player is a single app-wide object. Needs a network connection and a sound device;
 * run with `DAYDREAMIN_INTEGRATION=1 ./gradlew :app:desktopTest`.
 */
class PlayerIntegrationTest {
    private fun log(s: String) = println("[player-test] $s")

    private fun waitFor(what: String, timeoutMs: Long, cond: () -> Boolean): Long {
        val start = System.currentTimeMillis()
        while (!cond()) {
            if (System.currentTimeMillis() - start > timeoutMs) error("timed out waiting for: $what")
            Thread.sleep(50)
        }
        return System.currentTimeMillis() - start
    }

    private val meta get() = PlayerController.meta.value
    private val position get() = PlayerController.progress.value.positionMs

    @Test
    fun playerScenario() {
        if (System.getenv("DAYDREAMIN_INTEGRATION") != "1") { log("skipped (set DAYDREAMIN_INTEGRATION=1)"); return }
        val dir = Files.createTempDirectory("daydreamin-test").toFile()
        val store = PreferenceDataStoreFactory.create { File(dir, "prefs.preferences_pb") }
        TasteProfile.init(dir)
        AppGraph.init(dir, AppPreferences(store, dir), 0)
        Region.init("IN")
        YouTubeExtractorService.init(Region.country)
        YouTubeExtractorService.opusOnly = true
        Downloads.init()
        PlayerController.ensureConnected()

        val results = runBlocking { YouTubeExtractorService.search("Tum Hi Ho Arijit Singh", limit = 5) }.map { it.toSong() }
        val song = results.first()
        log("searched: ${results.size} songs, playing '${song.title}'")

        // 1. Start
        val tapped = System.currentTimeMillis()
        PlayerController.playSong(song)
        val firstSound = waitFor("audio to start", 20_000) { meta.isPlaying && position > 300 }
        log("1. start: first sound after ${firstSound}ms (${meta.streamQuality})")
        assertTrue(meta.streamQuality.orEmpty().contains("OPUS", ignoreCase = true), "plays Opus: ${meta.streamQuality}")
        val p0 = position; Thread.sleep(3_000); val p1 = position
        log("   advanced ${p1 - p0}ms in 3s of wall time")
        assertTrue(p1 - p0 in 2_500..3_500, "plays in real time")
        val duration = PlayerController.progress.value.durationMs
        log("   duration ${duration}ms")
        assertTrue(duration > 60_000)

        // 2. Radio fills Up Next
        waitFor("Up Next", 30_000) { meta.queue.size >= 5 }
        log("2. Up Next: ${meta.queue.size} songs, first '${meta.queue.first().title}'")

        // 3. Seek
        val seekAt = System.currentTimeMillis()
        PlayerController.seekTo(120_000)
        waitFor("seek", 10_000) { position in 120_200..123_000 }
        log("3. seek to 2:00: playing from ${position}ms after ${System.currentTimeMillis() - seekAt}ms")

        // 4. Pause / resume
        PlayerController.togglePlayPause()
        waitFor("pause", 3_000) { !meta.isPlaying }
        val paused = position; Thread.sleep(1_500)
        assertTrue(position - paused < 250, "stays put while paused (${position - paused}ms)")
        PlayerController.togglePlayPause()
        waitFor("resume", 3_000) { meta.isPlaying }
        Thread.sleep(1_000)
        assertTrue(position > paused + 600, "carries on after resume")
        log("4. pause/resume ok at ${position}ms")

        // 5. Next: the lined-up song starts at once
        val nextSong = meta.queue.first()
        Thread.sleep(4_000) // let it line up the next song
        val skipAt = System.currentTimeMillis()
        PlayerController.next()
        waitFor("next song", 15_000) { meta.currentSong?.playId == nextSong.playId && position > 200 }
        log("5. next: '${nextSong.title}' playing after ${System.currentTimeMillis() - skipAt}ms")
        assertEquals(song.playId, runBlocking { AppGraph.prefs.history.first() }.getOrNull(1)?.playId, "history has the first song")

        // 6. Previous goes back
        PlayerController.previous()
        waitFor("previous", 15_000) { meta.currentSong?.playId == song.playId }
        log("6. previous: back to '${meta.currentSong?.title}'")

        // 7. Crossfade: the next song takes over 6 s before this one ends
        runBlocking { AppGraph.prefs.setCrossfadeSeconds(6) }
        Thread.sleep(500)
        val dur = PlayerController.progress.value.durationMs
        val upcoming = meta.queue.first()
        PlayerController.seekTo(dur - 12_000)
        Thread.sleep(1_500)
        var lastPosition = 0L
        waitFor("crossfade", 20_000) {
            val switched = meta.currentSong?.playId == upcoming.playId
            if (!switched) lastPosition = position
            switched
        }
        val leftAtSwitch = dur - lastPosition
        log("7. crossfade: switched to '${upcoming.title}' with ${leftAtSwitch}ms of the outgoing song left")
        assertTrue(leftAtSwitch in 5_000..7_500, "fades over the last ~6 s")
        assertNotEquals(song.playId, meta.currentSong?.playId)

        // 8. Connection drops mid-song: same song, same spot, once it's back
        waitFor("playing", 15_000) { meta.isPlaying && position > 2_000 }
        val playingSong = meta.currentSong!!.playId
        StreamFaults.dropConnection = true
        Thread.sleep(25_000) // past the 15 s already buffered
        val stuckAt = position
        log("8. connection dropped: buffering=${meta.isBuffering}, error='${meta.error}', at ${stuckAt}ms")
        StreamFaults.dropConnection = false
        waitFor("recovery", 30_000) { meta.isPlaying && !meta.isBuffering && position > stuckAt + 1_000 }
        assertEquals(playingSong, meta.currentSong?.playId, "kept the same song")
        log("   recovered: same song, now at ${position}ms")

        // 9. Download, then play from the file
        val toKeep = results[1]
        Downloads.download(toKeep)
        waitFor("download", 60_000) { Downloads.items.value[toKeep.playId]?.status == DownloadStatus.DONE }
        log("9. downloaded '${toKeep.title}': ${Downloads.bytesUsed() / 1024} KB")
        PlayerController.playSong(toKeep)
        waitFor("downloaded song playing", 10_000) { meta.currentSong?.playId == toKeep.playId && position > 300 }
        assertEquals("Downloaded", meta.streamQuality)
        log("   plays from the file (${meta.streamQuality})")

        // 10. Remembers where it was
        PlayerController.togglePlayPause()
        waitFor("pause", 3_000) { !meta.isPlaying }
        Thread.sleep(500)
        val saved = runBlocking { AppGraph.prefs.playbackSnapshotJson.first() }.orEmpty()
        assertTrue(toKeep.playId in saved, "snapshot saved")
        log("10. snapshot saved (${saved.length} chars)")
        dir.deleteRecursively()
    }
}
