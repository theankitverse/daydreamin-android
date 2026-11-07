package com.daydreamin.app.data.scrobble

import com.daydreamin.app.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals

class ScrobblerTest {
    private val a = Song(id = "a", title = "A", artist = "X")
    private val b = Song(id = "b", title = "B", artist = "Y")

    private class Log { val now = mutableListOf<String>(); val scrobbled = mutableListOf<Pair<String, Long>>() }
    private fun timer(log: Log) = ScrobbleTimer({ log.now += it.playId }, { s, at -> log.scrobbled += s.playId to at })

    private fun ScrobbleTimer.play(song: Song, fromMs: Long, seconds: Int, durationMs: Long, sounding: Boolean = true) {
        for (t in 0..seconds) tick(fromMs + t * 1_000L, song, sounding, durationMs)
    }

    @Test fun `half a song counts, once, from when it started`() {
        val log = Log(); val t = timer(log)
        t.play(a, 5_000, 99, 200_000)
        assertEquals(emptyList(), log.scrobbled)
        t.tick(105_000, a, true, 200_000)
        t.tick(106_000, a, true, 200_000)
        t.play(a, 107_000, 60, 200_000)
        assertEquals(listOf("a" to 5_000L), log.scrobbled)
        assertEquals(listOf("a"), log.now)
    }

    @Test fun `four minutes is enough for a long song`() {
        val log = Log(); val t = timer(log)
        t.play(a, 0, 241, 3_600_000)
        assertEquals(1, log.scrobbled.size)
    }

    @Test fun `paused time doesn't count, and a short song never does`() {
        val log = Log(); val t = timer(log)
        t.play(a, 0, 150, 200_000, sounding = false)
        assertEquals(emptyList(), log.scrobbled)
        assertEquals(emptyList(), log.now)
        t.play(b, 200_000, 25, 25_000)
        assertEquals(emptyList(), log.scrobbled)
    }

    @Test fun `signature matches Last_fm's recipe`() {
        // md5("api_keyxxxxxxxxmethodauth.getSessiontokenyyyyyymysecret")
        val params = mapOf("token" to "yyyyyy", "api_key" to "xxxxxxxx", "method" to "auth.getSession")
        assertEquals(java.security.MessageDigest.getInstance("MD5").digest("api_keyxxxxxxxxmethodauth.getSessiontokenyyyyyymysecret".toByteArray()).joinToString("") { "%02x".format(it) }, lastFmSignature(params, "mysecret"))
    }

    @Test fun `a batch numbers its tracks`() {
        val p = Scrobbler.scrobbleParams(listOf(Scrobble("X", "A", "Al", 100, 200), Scrobble("Y", "B", null, 300)), "sk1")
        assertEquals("track.scrobble", p["method"])
        assertEquals("B", p["track[1]"])
        assertEquals("300", p["timestamp[1]"])
        assertEquals(null, p["album[1]"])
        assertEquals("200", p["duration[0]"])
    }

    @Test fun `video dressing comes off the title`() {
        assertEquals("Kaise Hua", scrobbleTitle("LYRICAL: Kaise Hua"))
        assertEquals("Blinding Lights", scrobbleTitle("Blinding Lights (Official Video)"))
        assertEquals("Tera Hone Laga Hoon", scrobbleTitle("Tera Hone Laga Hoon Lyrical - Ajab Prem Ki Ghazab Kahani"))
        assertEquals("Kesariya (From \"Brahmastra\")", scrobbleTitle("Kesariya (From \"Brahmastra\")"))
    }
}
