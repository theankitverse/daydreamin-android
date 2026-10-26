package com.daydreamin.app.system

import com.daydreamin.app.data.model.Song
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** macOS only: what Now Playing shows after an update, read back from the system. */
class MacNowPlayingTest {
    private interface ObjC : Library {
        fun objc_getClass(name: String): Pointer?
        fun sel_registerName(name: String): Pointer
    }
    private val objc by lazy { Native.load("objc", ObjC::class.java) }
    private val msg by lazy { Function.getFunction("objc", "objc_msgSend") }
    private fun send(r: Pointer?, s: String, vararg a: Any?) = msg.invoke(Pointer::class.java, arrayOf(r, objc.sel_registerName(s), *a)) as Pointer?
    private fun string(p: Pointer?) = p?.let { send(it, "UTF8String")?.getString(0) }
    private fun info(key: String): Pointer? {
        val center = send(objc.objc_getClass("MPNowPlayingInfoCenter"), "defaultCenter")
        val dict = send(center, "nowPlayingInfo") ?: return null
        val k = NativeLibrary.getInstance("/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer").getGlobalVariableAddress(key).getPointer(0)
        return send(dict, "objectForKey:", k)
    }
    private fun number(key: String) = info(key)?.let { msg.invokeDouble(arrayOf(it, objc.sel_registerName("doubleValue"))) }

    @Test fun showsTheSongAndPlaybackState() {
        if (!System.getProperty("os.name").lowercase().contains("mac")) return
        val bridge = MacNowPlaying()
        val song = Song(id = "abc", title = "Kesariya", artist = "Arijit Singh", album = "Brahmastra", videoId = "BddP6PYo2gs")
        bridge.update(NowPlaying(song, playing = true, positionMs = 42_000, durationMs = 268_000, artworkUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"))

        assertEquals("Kesariya", string(info("MPMediaItemPropertyTitle")))
        assertEquals("Arijit Singh", string(info("MPMediaItemPropertyArtist")))
        assertEquals("Brahmastra", string(info("MPMediaItemPropertyAlbumTitle")))
        assertEquals(268.0, number("MPMediaItemPropertyPlaybackDuration"))
        assertEquals(42.0, number("MPNowPlayingInfoPropertyElapsedPlaybackTime"))
        assertEquals(1.0, number("MPNowPlayingInfoPropertyPlaybackRate"))
        val center = send(objc.objc_getClass("MPNowPlayingInfoCenter"), "defaultCenter")
        assertEquals(1L, Pointer.nativeValue(send(center, "playbackState")), "playing")

        // The cover is fetched in the background and added when it's here.
        val start = System.currentTimeMillis()
        while (info("MPMediaItemPropertyArtwork") == null && System.currentTimeMillis() - start < 10_000) Thread.sleep(100)
        assertNotNull(info("MPMediaItemPropertyArtwork"), "artwork shows")
        println("[mac-test] artwork after ${System.currentTimeMillis() - start}ms")

        bridge.update(NowPlaying(song, playing = false, positionMs = 50_000, durationMs = 268_000, artworkUrl = song.artworkUrl.ifBlank { "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg" }))
        assertEquals(0.0, number("MPNowPlayingInfoPropertyPlaybackRate"))
        assertEquals(2L, Pointer.nativeValue(send(center, "playbackState")), "paused")

        // The command target answers each command the system sends.
        val target = send(send(objc.objc_getClass("DaydreaminMediaTarget"), "alloc"), "init")
        for (sel in listOf("daydreaminPlay:", "daydreaminPause:", "daydreaminToggle:", "daydreaminNext:", "daydreaminPrevious:")) {
            val status = msg.invokeLong(arrayOf(target, objc.sel_registerName(sel), Pointer.NULL))
            assertEquals(0L, status, "$sel handled")
        }
        assertTrue(true)
        bridge.update(null)
        println("[mac-test] ok")
    }
}
