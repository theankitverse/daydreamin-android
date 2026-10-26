package com.daydreamin.app.data.repository

import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import com.daydreamin.app.data.youtube.YtTrack
import kotlin.math.ln

/** A search query, optionally split into "song" and "artist" halves when it matches an "X by Y" shape. */
data class ParsedSearchQuery(
    val raw: String,
    val titlePart: String?,
    val artistPart: String?,
)

private val BY_PATTERN = Regex("""^(.+?)\s+by\s+([\p{L}0-9&.,'\s]+)$""", RegexOption.IGNORE_CASE)

/** Heuristic: a long, unstructured query reads more like a lyric fragment than a title/artist search. */
fun isLikelyLyricFragment(query: String): Boolean = query.trim().split(Regex("\\s+")).size > 6

fun parseSearchQuery(query: String): ParsedSearchQuery {
    val trimmed = query.trim()
    val match = BY_PATTERN.find(trimmed)
    val title = match?.groupValues?.get(1)?.trim()
    val artist = match?.groupValues?.get(2)?.trim()
    return if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
        ParsedSearchQuery(trimmed, title, artist)
    } else {
        ParsedSearchQuery(trimmed, null, null)
    }
}

/** Levenshtein-based similarity in [0, 1] — 1.0 is identical. This is what makes typos ("Arujit Singh") still match. */
fun textSimilarity(a: String, b: String): Double {
    val la = a.trim().lowercase()
    val lb = b.trim().lowercase()
    if (la.isEmpty() || lb.isEmpty()) return 0.0
    if (la == lb) return 1.0
    val dist = levenshteinDistance(la, lb)
    val maxLen = maxOf(la.length, lb.length)
    return 1.0 - dist.toDouble() / maxLen
}

private fun levenshteinDistance(a: String, b: String): Int {
    val prev = IntArray(b.length + 1) { it }
    val curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            curr[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] else 1 + minOf(prev[j], curr[j - 1], prev[j - 1])
        }
        for (j in curr.indices) prev[j] = curr[j]
    }
    return prev[b.length]
}

/**
 * Merges iTunes + YouTube results into one ranked list instead of two separate ones.
 *
 * Two lessons learned by instrumenting real queries:
 *  - "boom shaka": YouTube Music's MUSIC_SONGS search is already popularity-ranked by Google's own
 *    algorithm (its #1 result matched Spotify's exactly), but iTunes' long-tail catalog floods
 *    generic-title queries with a dozen no-name artists that happen to share the exact title —
 *    so iTunes needs a real but modest discount, not blind trust.
 *  - "is this the real life is this just fantasy" (a Bohemian Rhapsody lyric): YouTube's own
 *    search for that literal text surfaced only cover/live versions, never Queen's studio
 *    original — but iTunes had it, appearing across *six* different catalog entries (different
 *    album releases). That repetition across many independent catalog entries is itself a real
 *    popularity/legitimacy signal — a song genuinely everyone has released and re-released beats
 *    a single one-off cover, regardless of which source's internal ranking found it first.
 *  So: neither source's ranking is trusted blindly. Position gives a modest edge (slightly more
 *  for YouTube Music since it's curated), and a "catalog repeat count" — how many near-duplicate
 *  entries a song has across *both* sources combined — acts as the tie-breaking popularity signal,
 *  since NewPipeExtractor doesn't expose a usable view count for MUSIC_SONGS results (always -1).
 *
 * Other pieces:
 *  - Typo tolerance: fuzzy (edit-distance) matching against the query, not exact substring —
 *    "Arujit Singh" still finds Arijit Singh.
 *  - "X by Y" queries get an extra boost when a candidate's title/artist both match the parsed
 *    halves, on top of (not instead of) the raw-query similarity.
 *  - An exact (near-100%) title match gets its own bonus, separate from position, so the real
 *    song doesn't lose to a partial/fuzzy match that merely happens to rank earlier.
 *  - Near-duplicate results from both sources (same song, different catalog) collapse to
 *    whichever scored higher, using fuzzy title+artist matching rather than an exact key, so
 *    genuinely different songs that happen to share a title (very common) aren't merged away.
 */
fun rankAndMergeSearchResults(
    query: ParsedSearchQuery,
    itunesResults: List<Song>,
    youtubeResults: List<YtTrack>,
): List<Song> {
    val similarityWeight = if (isLikelyLyricFragment(query.raw)) 0.35 else 1.0

    // How many near-duplicate (title, artist) entries exist across BOTH catalogs combined —
    // a song genuinely released/covered many times over is more likely the real, popular one
    // than a single no-name entry that happens to share a query's exact words.
    val allPairs = itunesResults.map { it.title to it.artist } + youtubeResults.map { it.title to it.artist }
    fun catalogRepeatBonus(title: String, artist: String): Double {
        val count = allPairs.count { (t, a) -> textSimilarity(t, title) > 0.85 && textSimilarity(a, artist) > 0.55 }
        return minOf(40.0, (count - 1) * 10.0)
    }

    fun scoreOf(title: String, artist: String, positionScore: Double, trustScore: Double): Double {
        val rawSimilarity = maxOf(
            textSimilarity(query.raw, title),
            textSimilarity(query.raw, "$artist $title"),
            textSimilarity(query.raw, "$title $artist"),
        )
        var score = positionScore + rawSimilarity * 100 * similarityWeight + trustScore + catalogRepeatBonus(title, artist)
        if (rawSimilarity > 0.97) score += 40.0 // genuinely exact title match, not just close
        val titlePart = query.titlePart
        val artistPart = query.artistPart
        if (titlePart != null && artistPart != null) {
            val titleSim = textSimilarity(titlePart, title)
            val artistSim = textSimilarity(artistPart, artist)
            if (titleSim > 0.6 && artistSim > 0.6) score += 60.0
        }
        return score
    }

    data class Scored(val song: Song, val score: Double)

    // iTunes' catalog includes a lot of unranked long-tail noise for generic phrases, so its
    // position score decays faster than YouTube's. Its long-tail catalog also tends to return
    // many different no-name artists under the exact same generic title ("Boom Shaka" x10) — cap
    // each distinct title to its best few so noise doesn't crowd out variety further down the list.
    val fromItunes = itunesResults.mapIndexed { index, song ->
        val positionScore = (30 - index).coerceAtLeast(0) * 2.0
        Scored(song, scoreOf(song.title, song.artist, positionScore, trustScore = 18.0))
    }.groupBy { it.song.title.trim().lowercase() }
        .flatMap { (_, group) -> group.sortedByDescending { it.score }.take(3) }
    // YouTube Music's MUSIC_SONGS order is Google's own popularity/relevance ranking, so it gets
    // a modest edge over iTunes' position score. Use a real view count when NewPipe happens to
    // provide one; otherwise fall back to a flat "curated official catalog" floor rather than zero.
    val fromYoutube = youtubeResults.mapIndexed { index, track ->
        val positionScore = (30 - index).coerceAtLeast(0) * 2.3
        val trust = if (track.viewCount > 0) ln(track.viewCount.toDouble() + 1.0) * 6.0 else 16.0
        Scored(track.toSong(), scoreOf(track.title, track.artist, positionScore, trustScore = trust))
    }

    val ranked = (fromItunes + fromYoutube).sortedByDescending { it.score }
    val kept = mutableListOf<Scored>()
    for (candidate in ranked) {
        val isDuplicate = kept.any { existing ->
            textSimilarity(existing.song.title, candidate.song.title) > 0.85 &&
                textSimilarity(existing.song.artist, candidate.song.artist) > 0.55
        }
        if (!isDuplicate) kept.add(candidate)
    }
    return kept.map { it.song }
}
