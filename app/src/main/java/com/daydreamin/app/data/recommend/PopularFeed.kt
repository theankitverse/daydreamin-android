package com.daydreamin.app.data.recommend

import com.daydreamin.app.data.model.Song
import java.util.Locale

/** What's popular right now, from the live charts — the raw material for a Home with no listening history yet. */
internal data class PopularSources(
    /** Apple Music's most-played in your country. */
    val regional: List<Song>,
    /** YouTube Charts' trending music videos in your country. */
    val trending: List<Song>,
    /** Most-played in the US, as a window on what's big elsewhere (empty when you're in the US). */
    val global: List<Song>,
) {
    val isEmpty: Boolean get() = regional.isEmpty() && trending.isEmpty() && global.isEmpty()
}

/**
 * Turns the charts into Home. Nothing is picked by hand: the songs, their order and even which
 * genre shelves appear all come from the charts as they are today. Songs you keep skipping are
 * left out, no artist gets more than two slots in any row, and no song appears twice between the
 * top-songs list and the shelves below it.
 */
internal object PopularFeed {

    fun compose(sources: PopularSources, skipped: Set<String>, region: String, nowMs: Long = System.currentTimeMillis()): HomeFeed? {
        if (sources.isEmpty) return null
        val place = regionName(region)
        fun List<Song>.usable() = filterNot { key(it) in skipped }.distinctBy(::key)

        val regional = sources.regional.usable()
        val trending = sources.trending.usable()
        val global = sources.global.usable()

        // The list Home leads with: your country's most-played, or trending if that feed was down.
        val topSongs = regional.ifEmpty { trending }.capPerArtist(2).take(20)
        val shown = topSongs.map(::key).toMutableSet()

        val shelves = buildList {
            val trendingShelf = trending.filterNot { key(it) in shown }.capPerArtist(2).take(15)
            if (trendingShelf.size >= 5) {
                add(FeedShelf("trending", "Trending on YouTube", "Music videos taking off in $place", trendingShelf))
                shown += trendingShelf.map(::key)
            }
            // A shelf for each genre that's big in the charts right now, biggest first.
            genreShelves(regional + global, shown).forEach(::add)
            if (global.isNotEmpty()) {
                val globalShelf = global.filterNot { key(it) in shown }.capPerArtist(2).take(15)
                if (globalShelf.size >= 5) add(FeedShelf("global", "Popular in the US", "Most played there right now", globalShelf))
            }
        }

        // The hero mix blends the sources so pressing Play isn't just the top-songs list again.
        val mix = interleave(topSongs, trending, global).distinctBy(::key).capPerArtist(2).take(30)

        return HomeFeed(
            generatedAtMs = nowMs,
            seedIds = emptyList(),
            basedOn = emptyList(),
            topPicks = topSongs,
            shelves = shelves,
            kind = HomeFeed.KIND_POPULAR,
            region = region,
            mix = mix,
        )
    }

    /** [personal] first, then popular songs you haven't heard, up to [target] — no artist more than three times. */
    fun topUp(personal: List<Song>, sources: PopularSources, exclude: Set<String>, target: Int): List<Song> {
        val have = personal.map(::key).toSet()
        val extra = (sources.regional + sources.trending).filterNot { key(it) in exclude || key(it) in have }
        return (personal + extra).distinctBy(::key).capPerArtist(3).take(target)
    }

    /** The shelves a thin personal mix gets below it, until there's enough listening to stand on its own. */
    fun supportingShelves(sources: PopularSources, exclude: Set<String>, region: String): List<FeedShelf> {
        val place = regionName(region)
        val top = sources.regional.filterNot { key(it) in exclude }.distinctBy(::key).capPerArtist(2).take(15)
        val trending = sources.trending.filterNot { key(it) in exclude || key(it) in top.map(::key) }.distinctBy(::key).capPerArtist(2).take(15)
        return listOfNotNull(
            top.takeIf { it.size >= 5 }?.let { FeedShelf("popular-top", "Top songs in $place", "Most played right now", it) },
            trending.takeIf { it.size >= 5 }?.let { FeedShelf("popular-trending", "Trending on YouTube", "Music videos taking off in $place", it) },
        )
    }

    private fun genreShelves(pool: List<Song>, shown: MutableSet<String>, max: Int = 3): List<FeedShelf> =
        pool.groupBy { it.genre.trim() }
            .filterKeys { it.isNotBlank() && !it.equals("Music", ignoreCase = true) }
            .entries
            .sortedByDescending { it.value.size }
            .mapNotNull { (genre, songs) ->
                val shelf = songs.filterNot { key(it) in shown }.capPerArtist(2).take(15)
                if (shelf.size < 5) return@mapNotNull null
                shown += shelf.map(::key)
                FeedShelf("genre-${genre.lowercase()}", "Popular in $genre", null, shelf)
            }
            .take(max)

    private fun interleave(vararg lists: List<Song>): List<Song> {
        val out = mutableListOf<Song>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) lists.forEach { it.getOrNull(i)?.let(out::add) }
        return out
    }

    private fun key(song: Song) = Recommender.titleKey(song.title)

    /** "India", "the United States" — ready to follow "in". */
    fun regionName(region: String): String {
        val name = Locale("", region).getDisplayCountry(Locale.ENGLISH).ifBlank { region }
        return if (region.uppercase() in TAKES_THE) "the $name" else name
    }

    /** Countries whose English name takes "the". */
    private val TAKES_THE = setOf("US", "GB", "AE", "NL", "PH", "BS", "GM", "CZ", "DO", "CF", "MV", "KY", "VA", "CD", "CG", "MH", "SB", "KM", "TC", "VI", "VG", "FO", "FK")
}
