package com.daydreamin.app.data.recommend

import android.util.Log
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.repository.toSong
import com.daydreamin.app.data.taste.TasteProfile
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "HomeFeed"

/** A row of songs on Home, e.g. "Because you like “Tum Hi Ho”". */
@Serializable
data class FeedShelf(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val songs: List<Song>,
)

/** Everything personal on Home, built from your taste and kept on disk between launches. */
@Serializable
data class HomeFeed(
    val generatedAtMs: Long,
    val seedIds: List<String>,
    /** The artists your mix is mostly built around, for "Based on …". */
    val basedOn: List<String>,
    val topPicks: List<Song>,
    val shelves: List<FeedShelf> = emptyList(),
)

/**
 * Home's recommendations, entirely on-device. From your plays, likes and skips
 * ([Recommender.songScores]) it picks a handful of seed songs across different artists, fetches
 * YouTube Music's radio mix for each — the same "Start radio" behind YT Music, so it's based on
 * how songs actually sound and who listens to them — and blends them into one ranked mix, plus a
 * few shelves ("Because you like …", "More from …", "Fresh finds").
 *
 * The last feed is kept on disk and shown instantly on launch; it rebuilds in the background when
 * it's more than a few hours old, when your taste has shifted, or after a handful of new plays.
 */
class HomeFeedRepository(private val app: DaydreaminApp) {
    private val file = File(app.filesDir, "home_feed.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val building = Mutex()

    private val _feed = MutableStateFlow<HomeFeed?>(null)
    val feed: StateFlow<HomeFeed?> = _feed

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Whether there's enough listening to personalize from: null until known. */
    private val _hasTaste = MutableStateFlow<Boolean?>(null)
    val hasTaste: StateFlow<Boolean?> = _hasTaste

    /** Set when the last build found nothing at all (offline, most likely) and there was no older feed to fall back on. */
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed

    private var playsAtLastBuild = 0

    fun start() {
        app.appScope.launch {
            _feed.value = readCache()
            refreshIfNeeded()
            // Keep up with a listening session: rebuild after every few new plays.
            TasteProfile.playsThisSession.collect { plays ->
                if (plays - playsAtLastBuild >= REBUILD_AFTER_PLAYS) refresh()
            }
        }
    }

    /** Rebuilds only if the feed is missing, old, or no longer matches what you've been playing. */
    suspend fun refreshIfNeeded() {
        val scores = currentScores()
        val seeds = Recommender.seeds(scores)
        _hasTaste.value = seeds.isNotEmpty()
        if (seeds.isEmpty()) return
        val current = _feed.value
        val stale = current == null ||
            System.currentTimeMillis() - current.generatedAtMs > MAX_AGE_MS ||
            seeds.map { it.song.playId }.count { it !in current.seedIds } >= 2
        if (stale) refresh()
    }

    /** Builds a fresh feed now. Returns false if it couldn't (another build is running, or nothing came back). */
    suspend fun refresh(): Boolean {
        if (!building.tryLock()) return false
        _refreshing.value = true
        try {
            val built = withTimeoutOrNull(BUILD_TIMEOUT_MS) { build() }
            if (built != null) {
                _feed.value = built
                _failed.value = false
                playsAtLastBuild = TasteProfile.playsThisSession.value
                writeCache(built)
                built.topPicks.take(2).forEach { s -> s.videoId?.let(YouTubeExtractorService::prefetch) }
            } else if (_feed.value == null) {
                _failed.value = _hasTaste.value == true
            }
            return built != null
        } finally {
            _refreshing.value = false
            building.unlock()
        }
    }

    private suspend fun currentScores(): Map<String, Pair<Song, Double>> {
        val history = app.prefs.history.first()
        val liked = app.prefs.likedSongs.first()
        val stats = TasteProfile.snapshot()
        return Recommender.songScores(history, liked, stats)
    }

    private suspend fun build(): HomeFeed? = withContext(Dispatchers.IO) {
        val history = app.prefs.history.first()
        val liked = app.prefs.likedSongs.first()
        val stats = TasteProfile.snapshot()
        val scores = Recommender.songScores(history, liked, stats)
        val seeds = Recommender.seeds(scores)
        _hasTaste.value = seeds.isNotEmpty()
        if (seeds.isEmpty()) return@withContext null

        // New songs, mostly: keep out what you just heard and what's already in your likes
        // (those have their own shelf), and anything you keep skipping.
        val recent = history.take(40).map { Recommender.titleKey(it.title) }
        val likedKeys = liked.map { Recommender.titleKey(it.title) }
        val skipped = stats.values.filter { it.mostlySkipped }.map { Recommender.titleKey(it.song.title) }
        val exclude = (recent + likedKeys + skipped).toSet()

        val gate = Semaphore(3)
        val mixes = coroutineScope {
            seeds.map { seed -> async { gate.withPermit { seed to mixFor(seed, exclude) } } }.awaitAll()
        }.filter { it.second.isNotEmpty() }
        if (mixes.isEmpty()) {
            Log.w(TAG, "no radio mixes came back for ${seeds.size} seeds")
            return@withContext null
        }

        val affinity = Recommender.artistAffinity(scores)
        val ranked = Recommender.rank(mixes, affinity, exclude)
        val topPicks = ranked.capPerArtist(3).take(30)
        val featured = topPicks.take(12).map { Recommender.titleKey(it.title) }.toSet()

        val shelves = buildList {
            // The two seeds you're most into, each with its own radio.
            mixes.sortedByDescending { it.first.weight }.take(2).forEach { (seed, mix) ->
                val songs = mix.filterNot { Recommender.titleKey(it.title) in featured }.capPerArtist(3).take(15)
                if (songs.size >= 5) add(FeedShelf("because-${seed.song.playId}", "Because you like", "“${seed.song.title}”", songs))
            }
            moreFromTopArtist(affinity, scores, exclude)?.let(::add)
            // Artists you've never played, but that keep coming up around the ones you do.
            val fresh = ranked
                .filter { val key = Recommender.artistKey(it.artist); key !in affinity && !Recommender.isLabel(key) }
                .filterNot { Recommender.titleKey(it.title) in featured }
                .capPerArtist(1)
                .take(15)
            if (fresh.size >= 5) add(FeedShelf("fresh", "Fresh finds", "Artists you haven't played yet", fresh))
        }

        HomeFeed(
            generatedAtMs = System.currentTimeMillis(),
            seedIds = seeds.map { it.song.playId },
            basedOn = seeds.map { Recommender.primaryArtist(it.song.artist) }.filter { it.isNotBlank() }.distinct().take(3),
            topPicks = topPicks,
            shelves = shelves,
        ).also { Log.d(TAG, "built: ${it.topPicks.size} picks, ${it.shelves.size} shelves from ${seeds.size} seeds (${mixes.size} mixes)") }
    }

    /** The seed's YouTube Music radio. Finds its video first if we've never played it (an imported like, say). */
    private suspend fun mixFor(seed: Seed, exclude: Set<String>): List<Song> {
        val song = seed.song
        val videoId = song.videoId?.takeIf { it.isNotBlank() }
            ?: YouTubeExtractorService.search("${song.artist} ${song.title}", limit = 3).firstOrNull()?.videoId
                ?.also { TasteProfile.rememberVideoId(song, it) }
            ?: return emptyList()
        return runCatching {
            YouTubeExtractorService.fetchRadioMix(videoId, song.title, song.artist, exclude).map { it.toSong() }
        }.getOrDefault(emptyList())
    }

    /** Songs by the artist you play most that you haven't heard yet. */
    private suspend fun moreFromTopArtist(
        affinity: Map<String, Double>,
        scores: Map<String, Pair<Song, Double>>,
        exclude: Set<String>,
    ): FeedShelf? {
        val topKey = affinity.entries.sortedByDescending { it.value }.map { it.key }.firstOrNull { !Recommender.isLabel(it) } ?: return null
        val name = scores.values.map { it.first }.firstOrNull { Recommender.artistKey(it.artist) == topKey }?.let { Recommender.primaryArtist(it.artist) } ?: return null
        val songs = YouTubeExtractorService.search(name, limit = 30)
            .map { it.toSong() }
            .filter { Recommender.artistKey(it.artist) == topKey }
            .filterNot { Recommender.titleKey(it.title) in exclude }
            .distinctBy { Recommender.titleKey(it.title) }
            .take(15)
        return if (songs.size >= 5) FeedShelf("artist-$topKey", "More from $name", null, songs) else null
    }

    private suspend fun readCache(): HomeFeed? = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) json.decodeFromString<HomeFeed>(file.readText()) else null }.getOrNull()
    }

    private suspend fun writeCache(feed: HomeFeed) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(feed))
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
        const val REBUILD_AFTER_PLAYS = 6
        const val BUILD_TIMEOUT_MS = 30_000L
    }
}
