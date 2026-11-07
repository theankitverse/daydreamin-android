package com.daydreamin.app.data.library

import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.IOException

/** A playlist as another service has it. */
data class RemotePlaylist(val name: String, val author: String?, val coverUrl: String?, val songs: List<Song>)

@Serializable private data class DzCreator(val name: String = "")
@Serializable private data class DzArtist(val name: String = "")
@Serializable private data class DzAlbum(val title: String = "", val cover_xl: String? = null, val cover_big: String? = null)
@Serializable private data class DzTrack(val title: String = "", val duration: Int = 0, val artist: DzArtist = DzArtist(), val album: DzAlbum = DzAlbum())
@Serializable private data class DzTracks(val data: List<DzTrack> = emptyList())
@Serializable private data class DzError(val message: String = "")
@Serializable
private data class DzPlaylist(
    val title: String = "",
    val nb_tracks: Int = 0,
    val picture_xl: String? = null,
    val creator: DzCreator = DzCreator(),
    val tracks: DzTracks = DzTracks(),
    val error: DzError? = null,
)

/** Public playlists through Deezer's open API: no sign-in, names and covers only (audio comes from YouTube as always). */
object DeezerPlaylists {
    private const val MAX_SONGS = 1_000
    private val json = Json { ignoreUnknownKeys = true }

    private fun get(url: String): okhttp3.Response = NetworkModule.http.newCall(Request.Builder().url(url).build()).execute()

    /** Where a short link leads: following it ends on the playlist's page. */
    suspend fun resolveShortLink(url: String): PlaylistLink? = withContext(Dispatchers.IO) {
        get(url).use { PlaylistLink.parse(it.request.url.toString()) as? PlaylistLink.Deezer }
    }

    suspend fun fetch(id: String): RemotePlaylist = withContext(Dispatchers.IO) {
        val first = get("https://api.deezer.com/playlist/$id").use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            json.decodeFromString<DzPlaylist>(r.body!!.string())
        }
        first.error?.let { throw PlaylistFileException("Deezer can't find that playlist. It may be private.") }
        val tracks = first.tracks.data.toMutableList()
        while (tracks.size < minOf(first.nb_tracks, MAX_SONGS)) {
            val page = get("https://api.deezer.com/playlist/$id/tracks?index=${tracks.size}&limit=100").use { r ->
                json.decodeFromString<DzTracks>(r.body!!.string()).data
            }
            if (page.isEmpty()) break
            tracks += page
        }
        RemotePlaylist(
            name = first.title.ifBlank { "Deezer playlist" },
            author = first.creator.name.ifBlank { "Deezer" },
            coverUrl = first.picture_xl,
            songs = tracks.filter { it.title.isNotBlank() }.map {
                PlaylistFile.importedSong(it.title, it.artist.name, it.album.title, it.album.cover_xl ?: it.album.cover_big.orEmpty(), it.duration)
            }.distinctBy { it.playId },
        )
    }
}
