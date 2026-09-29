package com.daydreamin.app.data.repository

import com.daydreamin.app.data.model.LyricsResponse
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.remote.AppleChartSong
import com.daydreamin.app.data.remote.ItunesRssEntry
import com.daydreamin.app.data.remote.ItunesSearchItem
import com.daydreamin.app.data.remote.LrcLibEntry
import com.daydreamin.app.data.remote.NetworkModule
import com.daydreamin.app.data.youtube.ResolvedStream
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import com.daydreamin.app.data.youtube.YtPlaylist
import com.daydreamin.app.data.youtube.YtTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Everything here talks to either a free public API (iTunes, LRCLIB) or resolves audio
 * on-device via [YouTubeExtractorService] — there is no backend of ours anywhere in this
 * chain, so the app works fully offline-of-a-server, anywhere, for free.
 */
class MusicRepository {

    private val itunes get() = NetworkModule.itunes
    private val lrcLib get() = NetworkModule.lrcLib

    suspend fun search(query: String): Result<List<Song>> = safeCall {
        itunes.search(term = query).results
            .filter { it.trackName.isNotBlank() }
            .map { it.toSong() }
    }

    /**
     * The real search engine behind the Search screen: iTunes and YouTube are queried in
     * parallel (not sequentially — this is what keeps it fast despite hitting two sources),
     * then merged into one popularity-ranked, typo-tolerant, deduped list. See
     * [rankAndMergeSearchResults] for the actual ranking logic.
     */
    suspend fun smartSearch(query: String): Result<List<Song>> = safeCall {
        val parsed = parseSearchQuery(query)
        coroutineScope {
            val itunesDeferred = async {
                val itunesQuery = if (parsed.titlePart != null && parsed.artistPart != null) {
                    "${parsed.titlePart} ${parsed.artistPart}"
                } else parsed.raw
                runCatching { itunes.search(term = itunesQuery).results.filter { it.trackName.isNotBlank() }.map { it.toSong() } }
                    .getOrDefault(emptyList())
            }
            val youtubeDeferred = async { YouTubeExtractorService.search(parsed.raw, limit = 25) }
            // Lyric-fragment queries aren't reliably covered by YT Music's curated MUSIC_SONGS
            // catalog (it's scoped to song titles/artists, not arbitrary text) — a general video
            // search catches lyric videos and captioned uploads that actually contain the line.
            val broadDeferred = if (isLikelyLyricFragment(parsed.raw)) {
                async { YouTubeExtractorService.searchBroad(parsed.raw, limit = 15) }
            } else null
            val youtubeResults = youtubeDeferred.await() + broadDeferred?.await().orEmpty()
            rankAndMergeSearchResults(parsed, itunesDeferred.await(), youtubeResults)
        }
    }

    suspend fun searchPlaylists(query: String): Result<List<YtPlaylist>> = safeCall {
        YouTubeExtractorService.searchPlaylists(query)
    }

    suspend fun playlistTracks(playlistUrl: String): Result<List<Song>> = safeCall {
        YouTubeExtractorService.fetchPlaylistTracks(playlistUrl).map { it.toSong() }
    }

    /**
     * What's being streamed most in [country] (Apple Music), falling back to the iTunes Store
     * chart if that feed is down. The store chart ranks paid downloads, so it's the weaker
     * signal — ringtones and novelty buys are filtered out of it.
     */
    suspend fun popular(country: String): Result<List<Song>> = safeCall {
        val streamed = runCatching {
            NetworkModule.appleCharts.mostPlayed(country.lowercase()).feed.results.map { it.toSong() }
        }.getOrDefault(emptyList())
        streamed.ifEmpty {
            itunes.chart(country.lowercase()).feed.entry.map { it.toSong() }
                .filterNot { s -> NOVELTY.containsMatchIn(s.title) }
        }
    }

    private fun AppleChartSong.toSong() = Song(
        id = id,
        title = name,
        artist = artistName,
        cover = artworkUrl100.replace("100x100bb", "200x200bb"),
        coverXl = artworkUrl100.replace("100x100bb", "600x600bb"),
        genre = genres.firstOrNull()?.name ?: "Music",
    )

    /**
     * Resolves a playable stream URL for [song] and, as a bonus, YouTube's related tracks for
     * free in the same call. Most songs already carry a known [Song.videoId] (search results,
     * radio mix, recommendations), so that exact video is tried first — but a specific video id
     * can turn out unplayable (no separate audio stream, taken down, region-locked) with no
     * warning until extraction actually runs. When that happens, fall back to a fresh
     * artist+title search instead of failing outright — [YouTubeExtractorService.resolveForSong]
     * already tries several candidate videos, so a different upload of the same song usually
     * plays fine even when the one id we had didn't.
     */
    suspend fun resolveStream(song: Song): Result<ResolvedStream> = safeCall {
        val videoId = song.videoId?.takeIf { it.isNotBlank() }
        if (videoId != null) {
            val direct = runCatching { YouTubeExtractorService.resolveVideoId(videoId) }
            if (direct.isSuccess) return@safeCall direct.getOrThrow()
        }
        YouTubeExtractorService.resolveForSong(song.artist, song.title)
    }

    suspend fun lyrics(artist: String, title: String): Result<LyricsResponse> = safeCall {
        val cleanArtist = normalizeForLyrics(artist)
        val cleanTitle = normalizeForLyrics(title)
        // YouTube-sourced songs (Up Next, search-via-YouTube) often carry a noisy "artist" —
        // an uploader/channel name rather than a real artist — so fielded search alone misses
        // often; a primary-artist split and a loose combined-text search catch most of those.
        val primaryArtist = cleanArtist
            .split(Regex("""[,&]|\bfeat\.?\b|\bft\.?\b""", RegexOption.IGNORE_CASE))
            .firstOrNull()?.trim().orEmpty()

        val attempts: List<suspend () -> List<LrcLibEntry>> = listOfNotNull(
            { lrcLib.search(cleanArtist, cleanTitle) },
            if (primaryArtist.isNotBlank() && primaryArtist != cleanArtist) {
                { lrcLib.search(primaryArtist, cleanTitle) }
            } else null,
            { lrcLib.searchByQuery("$cleanArtist $cleanTitle") },
        )

        // If *every* request failed outright (rather than answering "nothing found"), say so —
        // the caller shows "you're offline" instead of the misleading "this song has no lyrics".
        var failures = 0
        var lastFailure: Throwable? = null
        for (attempt in attempts) {
            val results = runCatching { attempt() }.getOrElse { failures++; lastFailure = it; emptyList() }
            results.firstOrNull { !it.syncedLyrics.isNullOrBlank() }?.let {
                return@safeCall LyricsResponse(it.syncedLyrics, it.plainLyrics)
            }
            results.firstOrNull { !it.plainLyrics.isNullOrBlank() }?.let {
                return@safeCall LyricsResponse(null, it.plainLyrics)
            }
        }

        val directResult = runCatching { lrcLib.get(cleanArtist, cleanTitle) }
        if (directResult.isFailure && directResult.exceptionOrNull() is java.io.IOException && failures == attempts.size) {
            throw directResult.exceptionOrNull() ?: lastFailure!!
        }
        val direct = directResult.getOrNull()
        if (direct != null && (!direct.syncedLyrics.isNullOrBlank() || !direct.plainLyrics.isNullOrBlank())) {
            return@safeCall LyricsResponse(direct.syncedLyrics, direct.plainLyrics)
        }

        LyricsResponse(null, null)
    }

    private fun ItunesSearchItem.toSong(): Song {
        val cover = artworkUrl100.replace("100x100bb", "200x200bb")
        val coverXl = artworkUrl100.replace("100x100bb", "600x600bb")
        val id = if (trackId != 0L) trackId else collectionId
        return Song(
            id = id.toString(),
            title = trackName,
            artist = artistName,
            artistId = artistId,
            album = collectionName,
            cover = cover,
            coverXl = coverXl,
            duration = (trackTimeMillis / 1000).toInt(),
            genre = primaryGenreName,
        )
    }

    private fun ItunesRssEntry.toSong(): Song {
        val artUrl = images.lastOrNull()?.label.orEmpty()
        val cover = artUrl.replace("170x170bb", "200x200bb")
        val coverXl = artUrl.replace("170x170bb", "600x600bb")
        return Song(
            id = id.attributes.imId,
            title = name.label,
            artist = artist.label,
            album = collection.name.label.ifBlank { "Single" },
            cover = cover,
            coverXl = coverXl,
            genre = category.attributes.label,
        )
    }

    private fun normalizeForLyrics(text: String): String {
        if (text.isBlank()) return text
        var t = Regex("""\(.*?\)""").replace(text, "")
        t = Regex("""\[.*?]""").replace(t, "")
        t = Regex("""\b(feat|ft)\.?\s.*""", RegexOption.IGNORE_CASE).replace(t, "")
        return t.trim()
    }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching { block() }
    }
}

fun YtTrack.toSong() = Song(
    id = videoId,
    title = withoutArtistPrefix(cleanVideoTitle(title), cleanChannelName(artist)),
    artist = cleanChannelName(artist),
    cover = thumbnail,
    coverXl = thumbnail,
    duration = durationSeconds,
    videoId = videoId,
)

/** Things that chart on the iTunes Store but aren't songs anyone wants recommended. */
private val NOVELTY = Regex("""\b(ringtone|ring tone|alarm|notification sound)\b""", RegexOption.IGNORE_CASE)

/** Clutter that video titles carry and song titles don't. */
private val TITLE_TAIL = Regex(
    """\s*[-–—:]?\s*[(\[]?\s*\b(official\s+)?(full\s+)?(music\s+video|video\s+song|lyric(al)?\s+video|video|audio|m/?v|visuali[sz]er|4k)\b\s*[)\]]?\s*$""",
    RegexOption.IGNORE_CASE,
)
private val BRACKETED_CLUTTER = Regex(
    """\s*[(\[][^()\[\]]*\b(official|lyric|lyrical|video|audio|visuali[sz]er|full song|hd|4k)\b[^()\[\]]*[)\]]""",
    RegexOption.IGNORE_CASE,
)

/** "Kesariya || Official Music Video (4K)" -> "Kesariya". Never empties a title — "Video Games" stays "Video Games". */
internal fun cleanVideoTitle(raw: String): String {
    var t = raw.split(" || ", " | ", "｜").first().ifBlank { raw }
    t = BRACKETED_CLUTTER.replace(t, "")
    repeat(2) {
        val m = TITLE_TAIL.find(t)
        if (m != null && m.range.first > 0) t = t.substring(0, m.range.first)
    }
    return t.trim().trimEnd('-', '–', '—', ':', '|').trim().ifBlank { raw.trim() }
}

/** "Taylor Swift - Anti-Hero" by Taylor Swift -> "Anti-Hero": the artist is already shown under the title. */
internal fun withoutArtistPrefix(title: String, artist: String): String {
    if (artist.isBlank()) return title
    val m = Regex("""^\s*${Regex.escape(artist)}\s*[-–—:|]\s*(.+)$""", RegexOption.IGNORE_CASE).find(title) ?: return title
    return m.groupValues[1].trim().ifBlank { title }
}

/** YouTube's auto-generated artist channels: "Arijit Singh - Topic" -> "Arijit Singh"; "ArijitSinghVEVO" -> "ArijitSingh". */
internal fun cleanChannelName(raw: String): String = raw
    .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""VEVO$"""), "")
    .trim()
    .ifBlank { raw }
