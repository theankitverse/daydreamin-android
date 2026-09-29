package com.daydreamin.app.data.recommend

import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.repository.cleanChannelName
import com.daydreamin.app.data.repository.cleanVideoTitle
import com.daydreamin.app.data.taste.PlayStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {

    @Test fun `video titles lose their clutter but never their name`() {
        assertEquals("Shiv kailasho ke Vasi", cleanVideoTitle("Shiv kailasho ke Vasi || Official Music Video"))
        assertEquals("Kesariya", cleanVideoTitle("Kesariya (Official Video) [4K]"))
        assertEquals("Tum Hi Ho", cleanVideoTitle("Tum Hi Ho - Lyrical Video"))
        assertEquals("Video Games", cleanVideoTitle("Video Games"))
        assertEquals("Butter", cleanVideoTitle("Butter"))
        assertEquals("Who", cleanVideoTitle("Who | Official MV"))
    }

    @Test fun `channel names read as artists`() {
        assertEquals("Arijit Singh", cleanChannelName("Arijit Singh - Topic"))
        assertEquals("Krishna Das", Recommender.primaryArtist("Krishna Das Music"))
        assertEquals(Recommender.artistKey("Krishna Das"), Recommender.artistKey("Krishna Das Music"))
        assertEquals(Recommender.artistKey("KR\$NA"), Recommender.artistKey("KR\$NA x Seedhe Maut"))
        assertEquals("Ambassadors", Recommender.primaryArtist("X Ambassadors"))
        assertTrue(Recommender.isLabel(Recommender.artistKey("T-Series")))
        assertTrue(Recommender.isLabel(Recommender.artistKey("HYBE LABELS")))
    }

    @Test fun `seeds are one per artist, strongest first`() {
        val a1 = Song(id = "a1", title = "A One", artist = "Artist A")
        val a2 = Song(id = "a2", title = "A Two", artist = "Artist A - Topic")
        val b1 = Song(id = "b1", title = "B One", artist = "Artist B")
        val scores = Recommender.songScores(history = listOf(a1, a2, b1), liked = listOf(b1), stats = emptyMap())
        val seeds = Recommender.seeds(scores)
        assertEquals(listOf("b1", "a1"), seeds.map { it.song.playId })
    }

    @Test fun `songs you keep skipping never seed a mix`() {
        val kept = Song(id = "k", title = "Kept", artist = "One")
        val skipped = Song(id = "s", title = "Skipped", artist = "Two")
        val stats = mapOf("s" to PlayStat(skipped, plays = 2, skips = 3, lastPlayedAtMs = System.currentTimeMillis()))
        val seeds = Recommender.seeds(Recommender.songScores(listOf(skipped, kept), emptyList(), stats))
        assertEquals(listOf("k"), seeds.map { it.song.playId })
    }

    @Test fun `a song in several mixes outranks one in a single mix`() {
        val seedA = Seed(Song(id = "x", title = "X", artist = "A"), 1.0)
        val seedB = Seed(Song(id = "y", title = "Y", artist = "B"), 1.0)
        val shared = Song(id = "shared", title = "Shared Song", artist = "C")
        val onlyA = Song(id = "onlyA", title = "Only A", artist = "D")
        val ranked = Recommender.rank(
            mixes = listOf(seedA to listOf(onlyA, shared), seedB to listOf(shared)),
            affinity = emptyMap(),
            exclude = emptySet(),
        )
        assertEquals("shared", ranked.first().playId)
    }
}
