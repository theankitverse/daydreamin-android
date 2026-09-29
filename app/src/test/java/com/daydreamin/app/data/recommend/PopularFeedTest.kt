package com.daydreamin.app.data.recommend

import com.daydreamin.app.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopularFeedTest {

    private fun song(n: Int, artist: String, genre: String = "Pop") = Song(id = "s$n", title = "Song $n", artist = artist, genre = genre)

    /** A chart like India's today: one artist with a run of hits, several genres. */
    private val regional = listOf(
        song(1, "Karan Aujla", "Hip-Hop/Rap"), song(2, "Karan Aujla", "Hip-Hop/Rap"), song(3, "Karan Aujla", "Hip-Hop/Rap"),
        song(4, "Karan Aujla", "Hip-Hop/Rap"), song(5, "Arijit Singh", "Bollywood"), song(6, "Shreya Ghoshal", "Bollywood"),
        song(7, "Pritam", "Bollywood"), song(8, "Anirudh", "Tamil"), song(9, "Badshah", "Hip-Hop/Rap"),
        song(10, "Divine", "Hip-Hop/Rap"), song(11, "Seedhe Maut", "Hip-Hop/Rap"), song(12, "Vishal Mishra", "Bollywood"),
        song(13, "Jubin Nautiyal", "Bollywood"), song(14, "KK", "Bollywood"), song(15, "Unknown Artist", "Music"),
    ) + (16..40).map { song(it, "Artist $it", if (it % 2 == 0) "Bollywood" else "Hip-Hop/Rap") }
    private val trending = (100..120).map { song(it, "Channel ${it % 7}", "Music") }
    private val global = (200..230).map { song(it, "US Artist $it", "Country") }

    private fun feed(skipped: Set<String> = emptySet(), sources: PopularSources = PopularSources(regional, trending, global)) =
        PopularFeed.compose(sources, skipped, "IN", nowMs = 0)!!

    @Test fun `no artist takes more than two slots in any list`() {
        val f = feed()
        (listOf(f.topPicks, f.heroMix) + f.shelves.map { it.songs }).forEach { list ->
            list.groupBy { Recommender.artistKey(it.artist) }.forEach { (artist, songs) -> assertTrue("$artist x${songs.size}", songs.size <= 2) }
        }
    }

    @Test fun `no song appears in both the top list and a shelf below it`() {
        val f = feed()
        val seen = f.topPicks.map { it.playId }.toMutableSet()
        f.shelves.forEach { shelf -> shelf.songs.forEach { assertTrue("${it.playId} repeated in ${shelf.key}", seen.add(it.playId)) } }
    }

    @Test fun `genre shelves come from the charts, biggest first, never the catch-all`() {
        val genres = feed().shelves.filter { it.key.startsWith("genre-") }.map { it.title }
        assertTrue(genres.isNotEmpty())
        assertTrue(genres.none { it.contains("Music") })
        assertTrue(genres.contains("Popular in Country")) // it came from the global chart's data, not a list in the code
    }

    @Test fun `songs you keep skipping stay out`() {
        val skippedKey = Recommender.titleKey("Song 1")
        val f = feed(skipped = setOf(skippedKey))
        assertTrue((f.topPicks + f.heroMix + f.shelves.flatMap { it.songs }).none { Recommender.titleKey(it.title) == skippedKey })
    }

    @Test fun `falls back to trending when the regional chart is down`() {
        val f = feed(sources = PopularSources(emptyList(), trending, emptyList()))
        assertTrue(f.topPicks.isNotEmpty())
        assertTrue(f.topPicks.all { it in trending })
    }

    @Test fun `nothing at all means no feed, not an empty one`() {
        assertNull(PopularFeed.compose(PopularSources(emptyList(), emptyList(), emptyList()), emptySet(), "IN"))
    }

    @Test fun `it's marked popular and knows its country`() {
        val f = feed()
        assertTrue(f.isPopular)
        assertEquals("IN", f.region)
        assertEquals("India", PopularFeed.regionName("IN"))
        assertEquals("the United States", PopularFeed.regionName("US"))
    }

    @Test fun `a thin personal mix is topped up after your songs, without repeats or what you've heard`() {
        val personal = listOf(song(900, "Taylor Swift"), song(901, "Taylor Swift"), song(902, "Taylor Swift"))
        val heard = Recommender.titleKey("Song 5")
        val out = PopularFeed.topUp(personal, PopularSources(regional, trending, global), exclude = setOf(heard), target = 30)
        assertEquals(personal, out.take(3))
        assertEquals(30, out.size)
        assertEquals(out.size, out.map { it.playId }.distinct().size)
        assertTrue(out.none { Recommender.titleKey(it.title) == heard })
    }

    @Test fun `the hero mix blends sources rather than repeating the top list`() {
        val f = feed()
        assertTrue(f.heroMix.any { it in trending })
        assertTrue(f.heroMix.any { it in global })
    }
}
