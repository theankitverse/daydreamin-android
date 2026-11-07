package com.daydreamin.app.data.taste

import com.daydreamin.app.data.model.Song
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecapTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(y: Int, m: Int, h: Int) = LocalDateTime.of(y, m, 1, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun listen(id: String, artist: String, atMs: Long, ms: Long = 180_000) = ListenEntry(atMs, id, "Song $id", artist, "", ms)
    private fun stat(id: String, artist: String, plays: Int) = PlayStat(Song(id = id, title = "Song $id", artist = artist, duration = 120), plays = plays)

    @Test fun `listening timer counts only the time a song sounds`() {
        val out = mutableListOf<ListenEntry>()
        val timer = ListenTimer { out += it }
        val a = Song(id = "a", title = "A", artist = "X")
        timer.tick(0, a, true)
        for (t in 1..40) timer.tick(t * 1_000L, a, sounding = t <= 35) // 35 s playing, 5 s paused
        timer.tick(41_000, Song(id = "b"), true)
        assertEquals(35_000, out.single().listenedMs)
        assertEquals("a", out.single().playId)
    }

    @Test fun `a song skipped early isn't a listen, and sleep isn't listening`() {
        val out = mutableListOf<ListenEntry>()
        val timer = ListenTimer { out += it }
        val a = Song(id = "a")
        timer.tick(0, a, true)
        timer.tick(10_000_000, a, true) // the computer slept
        timer.tick(10_000_500, Song(id = "b"), true)
        assertEquals(emptyList(), out)
    }

    @Test fun `a full year comes from the dated listens`() {
        val listens = List(150) { listen("a", "Arijit Singh", at(2026, 3, 12)) } + List(60) { listen("b$it", "Adele, Drake", at(2026, 5, 12)) } + listen("old", "Old", at(2025, 5, 12))
        val r = assertNotNull(buildRecap(2026, listens, emptyList(), zone))
        assertEquals("Your 2026 in Music", r.title)
        assertEquals(210, r.plays)
        assertEquals(210L * 3, r.minutes)
        assertEquals("a", r.topSongs.first().title.removePrefix("Song "))
        assertEquals("Arijit Singh", r.topArtists.first().name)
        assertEquals("Adele", r.topArtists[1].name)
        assertEquals(ListenerType.REPEAT_ROMANTIC, r.type)
    }

    @Test fun `a young log falls back on all-time plays`() {
        val r = assertNotNull(buildRecap(2026, emptyList(), listOf(stat("a", "Pritam", 6), stat("b", "T-Series", 4)), zone))
        assertEquals("Your Daydreamin so far", r.title)
        assertEquals(20L, r.minutes)
        assertEquals(listOf("Pritam"), r.topArtists.map { it.name }) // a label's channel isn't an artist
    }

    @Test fun `too little to say anything`() {
        assertNull(buildRecap(2026, emptyList(), listOf(stat("a", "Pritam", 2)), zone))
    }

    @Test fun `late listeners are night owls`() {
        val listens = List(200) { listen("s$it", "Artist ${it % 3}", at(2026, 2, 23)) }
        assertEquals(ListenerType.NIGHT_OWL, buildRecap(2026, listens, emptyList(), zone)?.type)
    }

    @Test fun `credits the first artist, not the channel dressing`() {
        assertEquals("Adele", recapArtist("Adele - Topic"))
        assertEquals("Mithoon", recapArtist("Mithoon, Arijit Singh"))
        assertEquals("Sean Kingston", recapArtist("Sean Kingston feat. Justin Bieber"))
        assertNull(recapArtist("Sony Music India"))
    }
}
