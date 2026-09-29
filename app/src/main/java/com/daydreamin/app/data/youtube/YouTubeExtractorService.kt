package com.daydreamin.app.data.youtube

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class YtTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val durationSeconds: Int,
    /** -1 when YouTube doesn't report it — a real popularity signal used to rank search results. */
    val viewCount: Long = -1,
)

data class YtPlaylist(
    val url: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val trackCount: Long,
)

data class ResolvedStream(
    val videoId: String,
    val streamUrl: String,
    val related: List<YtTrack>,
    val bitrateKbps: Int,
    val codec: String,
)

private val BAD_KEYWORDS = listOf(
    "remix", "cover", "karaoke", "instrumental", "tribute", "mashup", "lofi", "lo-fi",
    "slowed", "reverb", "sped up", "nightcore", "parody", "bootleg", "live", "concert",
)

/**
 * On-device YouTube search + audio stream resolution via NewPipeExtractor — replaces what
 * the backend's yt-dlp/ytmusicapi calls used to do, entirely client-side.
 *
 * Speed: resolved streams are cached in memory, a song's discovered video id is remembered
 * so replaying it (or resolving it after a speculative prefetch) skips the search step
 * entirely, and concurrent requests for the same video are coalesced into one extraction.
 *
 * Recommendations: "up next" is seeded instantly from the current video's related items,
 * then quietly upgraded to YouTube Music's actual radio mix (the same algorithm behind YT
 * Music's "Start Radio" — audio/listening-based, not just "other uploads of this video")
 * once that finishes, without blocking playback start.
 */
object YouTubeExtractorService {

    private val service get() = ServiceList.YouTube

    private data class CacheEntry(val stream: ResolvedStream, val atMs: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val inFlight = ConcurrentHashMap<String, Deferred<ResolvedStream>>()

    /** googlevideo URLs expire; don't hand out a stale one. Exposed so [com.daydreamin.app.player.PlayerController]
     *  can apply the same freshness window to items it's already pre-buffered into the player's
     *  own playlist — a cache entry here going stale doesn't help an already-built MediaItem
     *  whose URI was baked in at prepare time; that needs its own, separate refresh. */
    const val STREAM_CACHE_TTL_MS = 20 * 60 * 1000L

    // Once a cache entry is within this long of expiring, a read still serves it (it's not
    // stale *yet*) but also kicks off a silent background re-resolve — so by the time it's
    // actually needed, there's already a fresh one, instead of only discovering the expiry at
    // the moment something tries to stream from the now-dead URL.
    private val refreshMarginMs = 3 * 60 * 1000L
    private val refreshing = ConcurrentHashMap.newKeySet<String>()

    /** "artist|title" -> videoId, so a repeat or speculative resolve skips the search hop. */
    private val songKeyToVideoId = ConcurrentHashMap<String, String>()

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * [country] matters for everything YouTube returns — search ranking, radio mixes, trending.
     * Without it the extractor defaults to Great Britain, so every result was UK-regioned.
     */
    fun init(country: String) {
        NewPipe.init(
            NewPipeDownloader(),
            Localization(java.util.Locale.getDefault().language.ifBlank { "en" }, country),
            ContentCountry(country),
        )
    }

    /** What's trending in music on YouTube in this region right now (YouTube Charts). */
    suspend fun trendingMusic(limit: Int = 30): List<YtTrack> = withContext(Dispatchers.IO) {
        runCatching {
            val kiosk = service.kioskList.getExtractorById("trending_music", null)
            kiosk.fetchPage()
            kiosk.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration in 60..600 } // songs, not shorts or hour-long compilations
                .take(limit)
                .mapNotNull { it.toYtTrack() }
        }.onFailure { Log.w("YtExtract", "trendingMusic failed: ${it.message}") }
            .getOrDefault(emptyList())
    }

    /** Fire-and-forget warm-up for a video we already know we'll likely play soon. */
    fun prefetch(videoId: String) {
        if (videoId.isBlank() || isFresh(videoId)) return
        backgroundScope.launch { runCatching { resolveVideoId(videoId) } }
    }

    /** Fire-and-forget warm-up for an iTunes-sourced song with no known video id yet — e.g. the top of a freshly loaded list. */
    fun prefetchSong(artist: String, title: String) {
        val key = songKey(artist, title)
        val known = songKeyToVideoId[key]
        if (known != null) {
            prefetch(known)
            return
        }
        backgroundScope.launch { runCatching { resolveForSong(artist, title) } }
    }

    /** Drops any cached stream for this song so the next resolve extracts a fresh URL — for when a
     *  URL we handed out turned out not to work (HTTP 403/404/410) despite being inside the TTL.
     *  Covers both the song's own [videoId] and whatever id an artist/title search mapped it to. */
    fun invalidateStream(artist: String, title: String, videoId: String?) {
        videoId?.let { cache.remove(it) }
        songKeyToVideoId[songKey(artist, title)]?.let { cache.remove(it) }
    }

    private fun isFresh(videoId: String) = cache[videoId]?.let { System.currentTimeMillis() - it.atMs < STREAM_CACHE_TTL_MS } == true

    /**
     * Throws with a real message on failure instead of swallowing it — callers surface
     * [Throwable.message] to the user. Not every search result actually has a usable audio
     * stream (region locks, age restriction, a video with no audio-only rendition, etc.) — on
     * failure this tries the next-best search candidate instead of giving up on the first one.
     */
    suspend fun resolveForSong(artist: String, title: String): ResolvedStream = withContext(Dispatchers.IO) {
        val key = songKey(artist, title)
        val knownVideoId = songKeyToVideoId[key]
        if (knownVideoId != null) {
            val cached = runCatching { resolveVideoId(knownVideoId) }
            if (cached.isSuccess) return@withContext cached.getOrThrow()
            songKeyToVideoId.remove(key, knownVideoId) // that id turned out to be dead — forget it and search fresh
        }

        val candidates = findVideoIdCandidates(artist, title)
        if (candidates.isEmpty()) throw IllegalStateException("No YouTube match found for \"$artist - $title\".")

        var lastError: Throwable? = null
        for (candidateId in candidates) {
            val result = runCatching { resolveVideoId(candidateId) }
            if (result.isSuccess) {
                songKeyToVideoId[key] = candidateId
                return@withContext result.getOrThrow()
            }
            lastError = result.exceptionOrNull()
        }
        throw lastError ?: IllegalStateException("No playable YouTube stream found for \"$artist - $title\".")
    }

    suspend fun resolveVideoId(videoId: String): ResolvedStream {
        cache[videoId]?.let { entry ->
            val age = System.currentTimeMillis() - entry.atMs
            if (age < STREAM_CACHE_TTL_MS) {
                if (age > STREAM_CACHE_TTL_MS - refreshMarginMs) refreshInBackground(videoId)
                return entry.stream
            }
        }

        // Coalesce concurrent requests for the same video (e.g. a prefetch racing the user
        // actually tapping Next) into a single extraction instead of doing it twice.
        val deferred = inFlight.computeIfAbsent(videoId) {
            backgroundScope.async { extractStream(videoId) }
        }
        try {
            val result = deferred.await()
            cache[videoId] = CacheEntry(result, System.currentTimeMillis())
            return result
        } finally {
            inFlight.remove(videoId, deferred)
        }
    }

    /** Fire-and-forget: re-extracts [videoId] and updates the cache entry, without making the
     *  caller that triggered this (still holding a perfectly valid, not-yet-expired URL) wait
     *  for it. Coalesced against [inFlight] the same as a normal resolve, and guarded so a
     *  video already being refreshed doesn't get a second redundant refresh queued behind it. */
    private fun refreshInBackground(videoId: String) {
        if (!refreshing.add(videoId)) return
        backgroundScope.launch {
            try {
                val deferred = inFlight.computeIfAbsent(videoId) { backgroundScope.async { extractStream(videoId) } }
                val result = runCatching { deferred.await() }
                inFlight.remove(videoId, deferred)
                result.onSuccess { cache[videoId] = CacheEntry(it, System.currentTimeMillis()) }
                    .onFailure { Log.w("YtExtract", "background refresh failed for $videoId: ${it.message}") }
            } finally {
                refreshing.remove(videoId)
            }
        }
    }

    /**
     * The real YouTube Music radio mix for [videoId] — same mechanism as YT Music's "Start
     * Radio" (playlist id `RDAMVM<videoId>`), a proper recommendation queue rather than a
     * plain "related videos" list. Called separately from stream resolution so a slow/failed
     * radio fetch never delays audio actually starting.
     *
     * [excludeKeys] lets a caller exclude normalized titles it already knows about — e.g. the
     * user's recent play history, so "recommendations" don't just replay what you heard 10
     * minutes ago. If the mix comes back thin after filtering (an obscure seed can return very
     * few real matches), it's padded out with an artist search.
     */
    suspend fun fetchRadioMix(videoId: String, seedTitle: String, seedArtist: String = "", excludeKeys: Set<String> = emptySet()): List<YtTrack> = withContext(Dispatchers.IO) {
        val primary = runCatching {
            val mixUrl = "https://www.youtube.com/watch?v=$videoId&list=RDAMVM$videoId"
            val extractor = service.getPlaylistExtractor(mixUrl)
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toYtTrack() }
        }.onFailure { Log.w("YtExtract", "radio mix fetch failed for $videoId: ${it.message}") }
            .getOrDefault(emptyList())
            .cleanForRecommendation(seedTitle, excludeKeys)

        if (primary.size >= 8 || seedArtist.isBlank()) return@withContext primary

        // Thin mix (common for obscure/niche seeds) — pad it out with more from the same artist.
        val padding = runCatching { rawSearch(seedArtist, musicFilter = true) }
            .getOrDefault(emptyList())
            .mapNotNull { it.toYtTrack() }
            .cleanForRecommendation(seedTitle, excludeKeys + primary.map { normalizeTitleForDedup(it.title) })

        primary + padding
    }

    private fun extractStream(videoId: String): ResolvedStream {
        try {
            val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$videoId")
            // Only plain progressive files, and only the video's original audio: videos dubbed
            // into several languages (or with an audio-description track) list those as extra
            // streams, and "highest bitrate" alone could land on one of them.
            val playable = info.audioStreams.orEmpty()
                .filter { !it.content.isNullOrBlank() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            val original = playable.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            // Highest bitrate wins (in practice Opus ~160 kbps, the best YouTube serves without
            // Premium); Opus is more efficient than AAC per bit, so it breaks ties
            // (and near-ties — a couple kbps apart is noise, not a real quality difference).
            val audio = original.ifEmpty { playable }
                .maxWithOrNull(
                    compareBy<org.schabi.newpipe.extractor.stream.AudioStream> { it.averageBitrate.coerceAtLeast(0) / 16 }
                        .thenBy { if (it.format?.name?.contains("OPUS", ignoreCase = true) == true) 1 else 0 }
                )
                ?: throw IllegalStateException("YouTube video $videoId has no audio stream.")

            val related = info.relatedItems.orEmpty()
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toYtTrack() }
                .cleanForRecommendation(seedTitle = info.name)

            return ResolvedStream(
                videoId = videoId,
                streamUrl = audio.content!!,
                related = related,
                bitrateKbps = audio.averageBitrate.coerceAtLeast(0),
                codec = audio.format?.name ?: audio.codec ?: "unknown",
            )
        } catch (e: Exception) {
            Log.e("YtExtract", "resolveVideoId($videoId) failed: ${e.javaClass.simpleName}: ${e.message}", e)
            throw IllegalStateException("Extraction failed: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    suspend fun search(query: String, limit: Int = 25): List<YtTrack> = withContext(Dispatchers.IO) {
        runCatching { rawSearch(query, musicFilter = true).take(limit).mapNotNull { it.toYtTrack() } }
            .onFailure { Log.e("YtExtract", "search($query) failed", it) }
            .getOrDefault(emptyList())
    }

    /**
     * General (non-Music-filtered) YouTube search — used as a second net for lyric-fragment
     * queries. YT Music's MUSIC_SONGS catalog is scoped to song titles/artists, so an arbitrary
     * mid-song line often isn't findable there at all; regular YouTube search indexes video
     * titles and descriptions far more broadly (official lyric videos, captioned uploads, fan
     * edits quoting the line), which is what actually finds the song. Obvious non-song lengths
     * (shorts, full concerts/albums) are filtered out since there's no music-catalog scoping here.
     */
    suspend fun searchBroad(query: String, limit: Int = 15): List<YtTrack> = withContext(Dispatchers.IO) {
        runCatching {
            rawSearch(query, musicFilter = false)
                .filter { it.duration in 30..900 }
                .take(limit)
                .mapNotNull { it.toYtTrack() }
        }.onFailure { Log.e("YtExtract", "searchBroad($query) failed", it) }
            .getOrDefault(emptyList())
    }

    /** Real YouTube Music playlist search (the "Playlists" tab) — actual curated/user playlists, not derived from song results. */
    suspend fun searchPlaylists(query: String, limit: Int = 15): List<YtPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val handler = service.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS), "")
            val extractor = service.getSearchExtractor(handler)
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<PlaylistInfoItem>()
                .take(limit)
                .map {
                    YtPlaylist(
                        url = it.url,
                        title = it.name,
                        author = it.uploaderName ?: "YouTube Music",
                        thumbnail = it.thumbnails.firstOrNull()?.url.orEmpty(),
                        trackCount = it.streamCount,
                    )
                }
        }.onFailure { Log.e("YtExtract", "searchPlaylists($query) failed", it) }
            .getOrDefault(emptyList())
    }

    /** Loads a real playlist's tracks (from a [YtPlaylist.url]) so it can actually be played, not just browsed. */
    suspend fun fetchPlaylistTracks(playlistUrl: String, limit: Int = 50): List<YtTrack> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = service.getPlaylistExtractor(playlistUrl)
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .take(limit)
                .mapNotNull { it.toYtTrack() }
        }.onFailure { Log.e("YtExtract", "fetchPlaylistTracks($playlistUrl) failed", it) }
            .getOrDefault(emptyList())
    }

    /** Ranked candidate video ids for [artist]/[title] — clean (non-remix/live/etc.) matches first, so a failed extraction on the top pick falls through to the next-best rather than giving up. */
    private fun findVideoIdCandidates(artist: String, title: String): List<String> {
        val query = "$artist $title".trim()
        var searchError: Throwable? = null
        val musicResults = runCatching { rawSearch(query, musicFilter = true) }
            .onFailure { searchError = it; Log.e("YtExtract", "music search failed for '$query'", it) }
            .getOrDefault(emptyList())
        val candidates = musicResults.ifEmpty {
            runCatching { rawSearch("$query official audio", musicFilter = false) }
                .onFailure { searchError = it; Log.e("YtExtract", "fallback search failed for '$query'", it) }
                .getOrDefault(emptyList())
        }
        if (candidates.isEmpty()) {
            // A search that *failed* (offline, timeout, rate-limited) is not the same as a
            // search that ran fine and found nothing. Swallowing it here used to turn every
            // network outage into a misleading "No YouTube match found" — which also made it
            // indistinguishable from a genuinely-unmatchable song, so callers couldn't tell a
            // retry-worthy failure from a hopeless one. Rethrow the real cause instead.
            searchError?.let { throw it }
            Log.w("YtExtract", "no candidates found for '$query'")
            return emptyList()
        }
        val (clean, rest) = candidates.partition { item -> BAD_KEYWORDS.none { item.name.lowercase().contains(it) } }
        return (clean + rest).mapNotNull { extractVideoId(it.url) }.distinct().take(5)
    }

    private fun rawSearch(query: String, musicFilter: Boolean): List<StreamInfoItem> {
        val filters = if (musicFilter) listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS) else emptyList()
        val handler = service.searchQHFactory.fromQuery(query, filters, "")
        val extractor = service.getSearchExtractor(handler)
        extractor.fetchPage()
        return extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
    }

    private fun StreamInfoItem.toYtTrack(): YtTrack? {
        val videoId = extractVideoId(url) ?: return null
        return YtTrack(
            videoId = videoId,
            title = name,
            artist = uploaderName ?: "Unknown",
            thumbnail = thumbnails.firstOrNull()?.url.orEmpty(),
            durationSeconds = duration.toInt().coerceAtLeast(0),
            viewCount = viewCount,
        )
    }

    /**
     * YouTube's "related" list (and, occasionally, the radio mix) is often partly reposts of
     * the exact same song — different uploads of the same official audio, or the seed track
     * itself — plus the odd karaoke/live/instrumental version nobody asked for. Collapse and
     * filter those out so "Up Next" is actually a variety of real songs. [excludeKeys] are
     * pre-normalized titles the caller wants kept out entirely (e.g. recent play history).
     */
    private fun List<YtTrack>.cleanForRecommendation(seedTitle: String, excludeKeys: Set<String> = emptySet()): List<YtTrack> {
        val seedKey = normalizeTitleForDedup(seedTitle)
        val seen = mutableSetOf<String>()
        val result = mutableListOf<YtTrack>()
        for (item in this) {
            val key = normalizeTitleForDedup(item.title)
            if (key.isBlank() || key == seedKey) continue
            if (key in excludeKeys) continue
            if (!seen.add(key)) continue
            if (BAD_KEYWORDS.any { item.title.lowercase().contains(it) }) continue
            result.add(item)
        }
        return result
    }

    /** Public so callers (e.g. history-based exclusion) can build comparable keys the same way. */
    fun normalizeTitleForDedup(title: String): String {
        var t = title.lowercase()
        t = Regex("""\(.*?\)""").replace(t, "")
        t = Regex("""\[.*?]""").replace(t, "")
        t = Regex("""\b(official)?\s*(video|audio|music video|lyric video|visualizer)\b""").replace(t, "")
        t = Regex("""[^a-z0-9]""").replace(t, "")
        return t.trim()
    }

    private fun songKey(artist: String, title: String) = "${artist.trim().lowercase()}|${title.trim().lowercase()}"

    private fun extractVideoId(url: String?): String? {
        if (url == null) return null
        val marker = "watch?v="
        val idx = url.indexOf(marker)
        if (idx == -1) return url.substringAfterLast('/').takeIf { it.isNotBlank() }
        return url.substring(idx + marker.length).substringBefore('&')
    }
}
