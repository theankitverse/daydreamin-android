package com.daydreamin.app.data.recommend

import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.taste.PlayStat
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import kotlin.math.exp
import kotlin.math.ln

/** A song your recommendations grow from, and how strongly it speaks for your taste (0..1]. */
internal data class Seed(val song: Song, val weight: Double)

/**
 * The taste side of recommendations — pure functions over what's already on the device, no
 * network. [HomeFeedRepository] does the fetching; this decides what the results mean.
 *
 * Signals, strongest first: liking a song, playing it a lot, playing it recently. Skipping a
 * song within its first seconds, repeatedly, counts against it (and a little against its artist).
 */
internal object Recommender {

    /** Record labels upload under their own name — "More from T-Series" isn't a real artist shelf. */
    private val LABEL_CHANNELS = setOf(
        "tseries", "zeemusiccompany", "sonymusicindia", "saregama", "saregamamusic", "tipsofficial",
        "tipsmusic", "speedrecords", "desimusicfactory", "yrf", "erosnow", "venusmusic", "shemaroo",
        "whitehillmusic", "timesmusic", "universalmusicindia", "wormusic", "lahari", "adityamusic",
        "hybelabels", "smtown", "jypentertainment", "ygentertainment", "1thek", "stonemusicentertainment",
        "tseriesbhaktisagar", "zeemusic", "sonymusic", "universalmusic", "warnermusic", "atlanticrecords",
        // "Music"/"Records" gets trimmed off channel names, so labels also appear without it:
        "zee", "sony", "tips", "speed", "times", "universal", "warner", "atlantic", "saregama",
    )

    /** Every song you've shown any interest in, scored. */
    fun songScores(history: List<Song>, liked: List<Song>, stats: Map<String, PlayStat>, nowMs: Long = System.currentTimeMillis()): Map<String, Pair<Song, Double>> {
        val scores = HashMap<String, Pair<Song, Double>>()
        fun add(song: Song, amount: Double) {
            val id = song.playId.ifBlank { return }
            val (known, score) = scores[id] ?: (song to 0.0)
            // Keep whichever copy of the song knows its YouTube video.
            val best = if (known.videoId.isNullOrBlank() && !song.videoId.isNullOrBlank()) song else known
            scores[id] = best to (score + amount)
        }
        // Recent plays: the most recent weigh most, fading over a few dozen songs.
        history.forEachIndexed { i, s -> add(s, exp(-i / 30.0)) }
        // Likes: a strong, lasting signal — newer likes a little stronger (new ones are appended last).
        liked.forEachIndexed { i, s -> add(s, 1.2 + 0.8 * exp(-(liked.size - 1 - i) / 25.0)) }
        // Repeat plays, fading over a few weeks; early skips against.
        stats.values.forEach { st ->
            val song = if (st.song.videoId.isNullOrBlank() && st.videoId != null) st.song.copy(videoId = st.videoId) else st.song
            val daysAgo = ((nowMs - st.lastPlayedAtMs).coerceAtLeast(0)) / 86_400_000.0
            add(song, 0.6 * ln(1.0 + st.plays) * exp(-daysAgo / 21.0))
            if (st.mostlySkipped) add(song, -3.0)
        }
        return scores
    }

    /** Your favourite songs across different artists — at most one per artist, so the mix isn't one person. */
    fun seeds(scores: Map<String, Pair<Song, Double>>, max: Int = 5): List<Seed> {
        val ranked = scores.values.filter { it.second > 0.3 }.sortedByDescending { it.second }
        val top = ranked.firstOrNull()?.second ?: return emptyList()
        return ranked
            .distinctBy { artistKey(it.first.artist) }
            .take(max)
            .map { (song, score) -> Seed(song, (score / top).coerceIn(0.2, 1.0)) }
    }

    /** How much you like each artist (normalized key -> score). */
    fun artistAffinity(scores: Map<String, Pair<Song, Double>>): Map<String, Double> {
        val out = HashMap<String, Double>()
        scores.values.forEach { (song, score) ->
            val key = artistKey(song.artist)
            if (key.isNotBlank()) out[key] = (out[key] ?: 0.0) + score
        }
        return out.filterValues { it > 0 }
    }

    /**
     * Merges several radio mixes into one ranking. A song scores for appearing near the top of a
     * mix seeded by something you love, more for appearing in several, and a little more if it's
     * by an artist you already play — familiar and new, not only one or the other.
     */
    fun rank(
        mixes: List<Pair<Seed, List<Song>>>,
        affinity: Map<String, Double>,
        exclude: Set<String>,
    ): List<Song> {
        val maxAffinity = affinity.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        class Entry(val song: Song, var score: Double = 0.0, var hits: Int = 0)
        val entries = LinkedHashMap<String, Entry>()
        for ((seed, mix) in mixes) {
            mix.forEachIndexed { position, song ->
                val key = titleKey(song.title)
                if (key.isBlank() || key in exclude) return@forEachIndexed
                val entry = entries.getOrPut(key) { Entry(song) }
                entry.score += seed.weight / (1.0 + position / 6.0)
                entry.hits++
            }
        }
        return entries.values
            .sortedByDescending { e ->
                val familiar = ((affinity[artistKey(e.song.artist)] ?: 0.0) / maxAffinity).coerceIn(0.0, 1.0)
                e.score + 0.15 * (e.hits - 1) + 0.35 * familiar
            }
            .map { it.song }
    }

    fun isLabel(artistKey: String) = artistKey in LABEL_CHANNELS

    fun titleKey(title: String) = YouTubeExtractorService.normalizeTitleForDedup(title)

    /** "Arijit Singh - Topic", "ArijitSinghVEVO", "Arijit Singh Official", "Arijit Singh, Shreya Ghoshal" -> "arijitsingh". */
    fun artistKey(artist: String): String = primaryArtist(artist).lowercase().replace(Regex("""[^\p{L}\p{N}]"""), "").removeSuffix("vevo")

    /**
     * The main artist, readable: "Arijit Singh - Topic" -> "Arijit Singh"; "KR\$NA x Seedhe Maut" -> "KR\$NA";
     * "Krishna Das Music" -> "Krishna Das" — YouTube radio mixes name the uploading channel, and a
     * channel called "<artist> Music" / "<artist> Official" is that artist.
     */
    fun primaryArtist(artist: String): String = artist
        .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""VEVO$"""), "")
        .replace(Regex("""\s+(Official(\s+Channel)?|Music|Records|Official Music)$""", RegexOption.IGNORE_CASE), "")
        .split(Regex("""\s*(,|&|/|\bx\b|\bfeat\.?|\bft\.?)\s*""", RegexOption.IGNORE_CASE))
        .firstOrNull { it.isNotBlank() } // "X Ambassadors" splits to ["", "Ambassadors"]
        ?.trim()
        ?: artist.trim()
}

/** No more than [max] songs by any one artist, keeping the order otherwise. */
internal fun List<Song>.capPerArtist(max: Int): List<Song> {
    val counts = HashMap<String, Int>()
    return filter { s ->
        val key = Recommender.artistKey(s.artist)
        val n = counts[key] ?: 0
        if (n >= max) false else { counts[key] = n + 1; true }
    }
}
