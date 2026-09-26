package com.daydreamin.app.data.repository

import com.daydreamin.app.data.model.LyricsResponse
import com.daydreamin.app.data.model.Song
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

    suspend fun chart(): Result<List<Song>> = safeCall {
        itunes.chart().feed.entry.map { it.toSong() }
    }

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
    title = title,
    artist = artist,
    cover = thumbnail,
    coverXl = thumbnail,
    duration = durationSeconds,
    videoId = videoId,
)
