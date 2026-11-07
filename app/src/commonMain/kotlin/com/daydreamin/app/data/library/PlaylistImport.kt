package com.daydreamin.app.data.library

import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.Playlist
import com.daydreamin.app.data.repository.toSong
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import com.daydreamin.app.platform.Log
import com.daydreamin.app.platform.PickedFile
import com.daydreamin.app.platform.displayName
import com.daydreamin.app.platform.readText
import com.daydreamin.app.ui.components.Toaster
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Brings playlists in from other places: a file from another app, or a link to a public playlist.
 * One way only: the app can read those playlists, not change them, so "Refresh" pulls in what's
 * new there and nothing goes back. Songs get their audio when they're played.
 */
object PlaylistImport {
    /** What marks a playlist that came from a file, so importing the file again updates it. Not a link: it has nothing to refresh from. */
    private const val FILE_SOURCE = "imported:"
    private const val FILE_AUTHOR = "Imported"

    /** Reads [file] and adds its playlists; says how it went in a toast either way. */
    fun import(file: PickedFile) {
        AppGraph.appScope.launch {
            try {
                val parsed = PlaylistFile.parse(file.displayName(), file.readText())
                val saved = parsed.map { p -> save(p.name, p.songs, FILE_SOURCE + p.name.lowercase(), FILE_AUTHOR, null) }
                Toaster.show(summary(saved.map { it.first }, parsed.sumOf { it.unreadable }, updated = saved.all { it.second }))
                saved.forEach { fillArtwork(it.first.id) }
            } catch (e: PlaylistFileException) {
                Toaster.show(e.message.orEmpty())
            } catch (e: Exception) {
                Log.w("PlaylistImport", "import failed: ${e.message}")
                Toaster.show("Couldn't read that file")
            }
        }
    }

    /**
     * Imports the public playlist at [link]; the message to show. A link already in the library
     * refreshes that playlist. Throws [PlaylistFileException] with what to tell the person.
     */
    suspend fun importLink(link: PlaylistLink): String {
        val (remote, url) = fetch(link)
        val (playlist, updated) = save(remote.name, remote.songs, url, remote.author, remote.coverUrl)
        AppGraph.appScope.launch { fillArtwork(playlist.id) }
        return summary(listOf(playlist), 0, updated)
    }

    /** Whether [playlist] came from a link that can be read again, and what to call the place. */
    fun refreshSource(playlist: Playlist): String? = when (playlist.sourceUrl?.let(PlaylistLink::parse)) {
        is PlaylistLink.YouTube -> "YouTube"
        is PlaylistLink.Deezer -> "Deezer"
        else -> null
    }

    /** Pulls in what's changed at [playlist]'s source. */
    fun refresh(playlist: Playlist) {
        val link = playlist.sourceUrl?.let(PlaylistLink::parse) ?: return
        AppGraph.appScope.launch {
            Toaster.show("Refreshing “${playlist.name}”…")
            try {
                val (remote, url) = fetch(link)
                val (saved) = save(remote.name, remote.songs, url, remote.author, remote.coverUrl)
                val before = playlist.songs.mapTo(HashSet()) { it.playId }
                val now = saved.songs.mapTo(HashSet()) { it.playId }
                val added = (now - before).size
                val removed = (before - now).size
                Toaster.show(if (added == 0 && removed == 0) "“${saved.name}” is up to date" else "“${saved.name}”: $added added, $removed removed")
                fillArtwork(saved.id)
            } catch (e: PlaylistFileException) {
                Toaster.show(e.message.orEmpty())
            } catch (e: Exception) {
                Log.w("PlaylistImport", "refresh failed: ${e.message}")
                Toaster.show("Couldn't refresh. Check your connection.")
            }
        }
    }

    private suspend fun fetch(link: PlaylistLink): Pair<RemotePlaylist, String> = when (link) {
        is PlaylistLink.YouTube -> {
            val whole = try {
                YouTubeExtractorService.fetchWholePlaylist(link.url)
            } catch (e: java.io.IOException) {
                throw e
            } catch (e: Exception) {
                Log.w("PlaylistImport", "couldn't read ${link.url}: ${e.message}")
                throw PlaylistFileException("YouTube can't find that playlist. It may be private.")
            }
            if (whole.tracks.isEmpty()) throw PlaylistFileException("That playlist is empty.")
            RemotePlaylist(whole.name, whole.author, null, whole.tracks.map { it.toSong() }.distinctBy { it.playId }) to link.url
        }
        is PlaylistLink.Deezer -> DeezerPlaylists.fetch(link.id) to link.url
        is PlaylistLink.DeezerShort -> {
            val full = DeezerPlaylists.resolveShortLink(link.url) as? PlaylistLink.Deezer ?: throw PlaylistFileException("That Deezer link isn't a playlist.")
            DeezerPlaylists.fetch(full.id) to full.url
        }
        PlaylistLink.Spotify, is PlaylistLink.Closed -> throw PlaylistFileException("That service doesn't let other apps read its playlists.")
    }

    /** Adds the playlist, or replaces the songs of the one from the same [source]. The playlist, and whether it was already here. */
    private suspend fun save(name: String, songs: List<Song>, source: String, author: String?, coverUrl: String?): Pair<Playlist, Boolean> {
        val all = AppGraph.prefs.playlists.first()
        val existing = all.firstOrNull { it.sourceUrl == source }
        if (existing != null) {
            // Keeps a cover already found, and the name if you changed it.
            val covers = existing.songs.associate { it.playId to it.cover }
            val updated = existing.copy(songs = songs.map { s -> if (s.cover.isBlank()) s.copy(cover = covers[s.playId].orEmpty()) else s }, coverUrl = coverUrl ?: existing.coverUrl)
            AppGraph.prefs.savePlaylists(all.map { if (it.id == existing.id) updated else it })
            return updated to true
        }
        val taken = all.mapTo(HashSet()) { it.name.lowercase() }
        val added = Playlist(UUID.randomUUID().toString(), uniqueName(name, taken), songs, sourceUrl = source, author = author, coverUrl = coverUrl)
        AppGraph.prefs.savePlaylists(all + added)
        return added to false
    }

    private fun uniqueName(name: String, taken: MutableSet<String>): String {
        var candidate = name
        var n = 2
        while (candidate.lowercase() in taken) candidate = "$name ($n)".also { n++ }
        taken += candidate.lowercase()
        return candidate
    }

    internal fun summary(playlists: List<Playlist>, unreadable: Int, updated: Boolean): String {
        val songs = playlists.sumOf { it.songs.size }
        val what = if (playlists.size == 1) "“${playlists.first().name}”" else "${playlists.size} playlists"
        val skipped = if (unreadable > 0) ", $unreadable skipped" else ""
        return "${if (updated) "Updated" else "Imported"} $what: $songs ${if (songs == 1) "song" else "songs"}$skipped"
    }

    /** Covers for songs that came without one, a few at a time, saved as they arrive. Gives up quietly offline. */
    private suspend fun fillArtwork(playlistId: String) {
        val missing = AppGraph.prefs.playlists.first().firstOrNull { it.id == playlistId }?.songs.orEmpty().filter { it.cover.isBlank() }
        var failures = 0
        val found = HashMap<String, String>()
        for ((index, song) in missing.withIndex()) {
            if (failures >= 3) break
            val result = AppGraph.repository.search("${song.artist} ${song.title}")
            if (result.isFailure) failures++
            result.getOrNull()?.firstOrNull()?.artworkUrl?.takeIf { it.isNotBlank() }?.let { found[song.playId] = it }
            if (found.size >= 10 || index == missing.lastIndex) { saveCovers(playlistId, found); found.clear() }
            delay(400) // iTunes allows about 20 searches a minute per address before it starts refusing
        }
        saveCovers(playlistId, found)
    }

    private suspend fun saveCovers(playlistId: String, covers: Map<String, String>) {
        if (covers.isEmpty()) return
        val all = AppGraph.prefs.playlists.first()
        AppGraph.prefs.savePlaylists(all.map { p ->
            if (p.id != playlistId) p
            else p.copy(songs = p.songs.map { s -> covers[s.playId]?.let { s.copy(cover = it) } ?: s })
        })
    }
}
