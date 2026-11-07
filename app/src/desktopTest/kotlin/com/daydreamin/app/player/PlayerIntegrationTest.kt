package com.daydreamin.app.player

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.Region
import com.daydreamin.app.data.download.DownloadStatus
import com.daydreamin.app.data.download.Downloads
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.AppPreferences
import com.daydreamin.app.data.prefs.toJson
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
    interface ObjC : com.sun.jna.Library {
        fun objc_getClass(name: String): com.sun.jna.Pointer?
        fun sel_registerName(name: String): com.sun.jna.Pointer
    }

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
        // Silently: the volume is this computer's own setting, put back afterwards.
        val volume = PlayerController.volume.value
        PlayerController.setVolume(0f)
        try { scenario() } finally { PlayerController.setVolume(volume); dir.deleteRecursively() }
    }

    private fun scenario() {
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

        // 8b. The system's media controls (macOS: what a media key or AirPods press sends)
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            com.daydreamin.app.system.SystemMedia.start()
            val objc = com.sun.jna.Native.load("objc", ObjC::class.java)
            val msg = com.sun.jna.Function.getFunction("objc", "objc_msgSend")
            fun send(r: com.sun.jna.Pointer?, sel: String, vararg a: Any?) = msg.invoke(com.sun.jna.Pointer::class.java, arrayOf(r, objc.sel_registerName(sel), *a)) as com.sun.jna.Pointer?
            val target = send(send(objc.objc_getClass("DaydreaminMediaTarget"), "alloc"), "init")
            fun nowPlayingTitle(): String? {
                val dict = send(send(objc.objc_getClass("MPNowPlayingInfoCenter"), "defaultCenter"), "nowPlayingInfo") ?: return null
                val key = com.sun.jna.NativeLibrary.getInstance("/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer").getGlobalVariableAddress("MPMediaItemPropertyTitle").getPointer(0)
                return send(send(dict, "objectForKey:", key), "UTF8String")?.getString(0)
            }
            waitFor("Now Playing shows the song", 5_000) { nowPlayingTitle() == meta.currentSong?.title }
            send(target, "daydreaminPause:", null)
            waitFor("media key pauses", 3_000) { !meta.isPlaying }
            send(target, "daydreaminPlay:", null)
            waitFor("media key plays", 3_000) { meta.isPlaying }
            val before = meta.currentSong?.playId
            send(target, "daydreaminNext:", null)
            waitFor("media key skips", 15_000) { meta.currentSong?.playId != before && meta.isPlaying }
            waitFor("Now Playing follows", 5_000) { nowPlayingTitle() == meta.currentSong?.title }
            log("8b. media keys: pause, play and next work; Now Playing shows '${nowPlayingTitle()}'")
        }

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

        // 11. Streams that keep breaking off a few seconds in: the player stops and says so, instead of
        // running through the queue song after song (what pressing play on a mix used to do).
        StreamFaults.endAfterBytes = 60_000
        try {
            // (Not the downloaded one: it plays from the file.)
            val streamed = results.filterNot { Downloads.isDownloaded(it.playId) }
            val first = streamed.first().playId
            val started = linkedSetOf<String>()
            PlayerController.playFromList(streamed, 0)
            waitFor("playback to stop", 90_000) {
                meta.currentSong?.let { if (it.playId == first || started.isNotEmpty()) started += it.title }
                !meta.isPlaying && meta.error != null
            }
            log("11. streams breaking off: stopped after ${started.size} songs ($started) — '${meta.error}'")
            assertTrue(started.size <= 3, "stops after three songs that won't play, not the whole queue (${started.size})")
        } finally {
            StreamFaults.endAfterBytes = null
        }

        // 12. The sound output goes away mid-song (the computer's sound device changed): the song carries on
        // in real time through a fresh output — not swallowed in a second, the next one after it, and so on.
        PlayerController.playFromList(results.drop(2), 0)
        waitFor("a song to play", 30_000) { meta.isPlaying && !meta.isBuffering && position > 2_000 }
        val songBeforeClose = meta.currentSong?.playId
        val positionBeforeClose = position
        OutputFaults.closeOnce = true
        Thread.sleep(6_000)
        val advanced = position - positionBeforeClose
        log("12. output closed mid-song: same song=${meta.currentSong?.playId == songBeforeClose}, advanced ${advanced}ms in 6s")
        assertEquals(songBeforeClose, meta.currentSong?.playId, "still the same song")
        assertTrue(advanced in 3_000..8_000, "plays on in real time ($advanced ms in 6 s)")

        // 13. Pause, then press play on something else (a mix, a playlist): it plays in real time. The
        // output stayed stopped from the pause and swallowed song after song in seconds each.
        PlayerController.togglePlayPause()
        waitFor("pause", 5_000) { !meta.isPlaying }
        Thread.sleep(1_000)
        PlayerController.playFromList(results.filterNot { Downloads.isDownloaded(it.playId) }.reversed(), 0)
        waitFor("the new song to play", 30_000) { meta.isPlaying && !meta.isBuffering && position > 500 }
        val newSong = meta.currentSong?.playId
        val newFrom = position
        Thread.sleep(5_000)
        val newAdvanced = position - newFrom
        log("13. after a pause, play on another list: same song=${meta.currentSong?.playId == newSong}, advanced ${newAdvanced}ms in 5s")
        assertEquals(newSong, meta.currentSong?.playId, "doesn't skip on")
        assertTrue(newAdvanced in 3_500..6_500, "plays in real time ($newAdvanced ms in 5 s)")

        // 14. A playlist from Spotify, start to finish: imported from a file, its first song found by
        // name and played, the listen logged with its date, and all of it carried through a backup.
        val csv = File(AppGraph.filesDir, "Road_Trip.csv").apply {
            writeText("Track URI,Track Name,Artist Name(s),Album Name,Track Duration (ms)\nx,Believer,Imagine Dragons,Evolve,204000\ny,Yellow,Coldplay,Parachutes,266000\n")
        }
        com.daydreamin.app.data.library.PlaylistImport.import(com.daydreamin.app.platform.PickedFile(csv))
        waitFor("the import", 10_000) { runBlocking { AppGraph.prefs.playlists.first() }.any { it.name == "Road Trip" } }
        val imported = runBlocking { AppGraph.prefs.playlists.first() }.first { it.name == "Road Trip" }
        assertEquals(2, imported.songs.size)
        com.daydreamin.app.data.taste.ListeningLog.track(AppGraph.appScope)
        PlayerController.playFromList(imported.songs, 0)
        waitFor("the imported song to play", 30_000) { meta.currentSong?.title == "Believer" && meta.isPlaying && !meta.isBuffering && position > 500 }
        Thread.sleep(33_000)
        PlayerController.next()
        waitFor("the next song", 30_000) { meta.currentSong?.title == "Yellow" }
        waitFor("the listen to be logged", 5_000) { runBlocking { com.daydreamin.app.data.taste.ListeningLog.entries() }.any { it.title == "Believer" } }
        val listen = runBlocking { com.daydreamin.app.data.taste.ListeningLog.entries() }.first { it.title == "Believer" }
        log("14. imported 'Road Trip', played '${listen.title}', logged ${listen.listenedMs}ms")
        assertTrue(listen.listenedMs in 30_000..36_000, "logs the time it played (${listen.listenedMs})")
        val backup = runBlocking { AppGraph.prefs.exportLibrary() }
        assertTrue(backup.listening.any { it.title == "Believer" }, "the listen is in the backup")
        val restored = com.daydreamin.app.data.prefs.parseLibraryBackup(backup.toJson())
        assertEquals(backup.listening, restored.listening)
        assertEquals(0, runBlocking { com.daydreamin.app.data.taste.ListeningLog.merge(restored.listening) }, "restoring doesn't double the listens")

        // 15. The same file again updates that playlist instead of making a second one.
        csv.writeText("Track URI,Track Name,Artist Name(s),Album Name,Track Duration (ms)\nx,Believer,Imagine Dragons,Evolve,204000\ny,Yellow,Coldplay,Parachutes,266000\nz,Fix You,Coldplay,X&Y,295000\n")
        com.daydreamin.app.data.library.PlaylistImport.import(com.daydreamin.app.platform.PickedFile(csv))
        waitFor("the update", 10_000) { runBlocking { AppGraph.prefs.playlists.first() }.first { it.name == "Road Trip" }.songs.size == 3 }
        assertEquals(1, runBlocking { AppGraph.prefs.playlists.first() }.count { it.name.startsWith("Road Trip") })
        log("15. the same file again: updated, still one playlist")

        // 16. Paste a link: a Deezer playlist and a long YouTube one, whole, and pasting again updates.
        val deezer = com.daydreamin.app.data.library.PlaylistLink.parse("https://www.deezer.com/en/playlist/3155776842?utm=x")!!
        val deezerResult = runBlocking { com.daydreamin.app.data.library.PlaylistImport.importLink(deezer) }
        log("16. $deezerResult")
        val top = runBlocking { AppGraph.prefs.playlists.first() }.first { it.sourceUrl == "https://www.deezer.com/playlist/3155776842" }
        assertTrue(top.songs.size > 50, "the whole Deezer playlist (${top.songs.size})")
        assertEquals("Deezer", com.daydreamin.app.data.library.PlaylistImport.refreshSource(top))
        val yt = runBlocking { YouTubeExtractorService.searchPlaylists("bollywood top 200 songs", 15) }.maxBy { it.trackCount }
        val ytLink = com.daydreamin.app.data.library.PlaylistLink.parse(yt.url)!!
        val ytResult = runBlocking { com.daydreamin.app.data.library.PlaylistImport.importLink(ytLink) }
        log("    $ytResult (YouTube says ${yt.trackCount})")
        val long = runBlocking { AppGraph.prefs.playlists.first() }.first { it.sourceUrl == (ytLink as com.daydreamin.app.data.library.PlaylistLink.YouTube).url }
        assertTrue(long.songs.size >= minOf(yt.trackCount, 1_000L) * 0.8, "the whole playlist, past YouTube's first page (${long.songs.size} of ${yt.trackCount})")
        val again = runBlocking { com.daydreamin.app.data.library.PlaylistImport.importLink(ytLink) }
        assertTrue(again.startsWith("Updated"), again)
        // A song from a pasted playlist plays like any other.
        PlayerController.playFromList(top.songs, 0)
        waitFor("a Deezer playlist song to play", 30_000) { meta.currentSong?.playId == top.songs[0].playId && meta.isPlaying && !meta.isBuffering && position > 500 }
        log("    plays '${meta.currentSong?.title}' from the Deezer playlist (${meta.streamQuality})")
    }
}
