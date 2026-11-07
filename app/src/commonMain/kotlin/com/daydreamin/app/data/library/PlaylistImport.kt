package com.daydreamin.app.data.library

import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.prefs.Playlist
import com.daydreamin.app.platform.Log
import com.daydreamin.app.platform.PickedFile
import com.daydreamin.app.platform.displayName
import com.daydreamin.app.platform.readText
import com.daydreamin.app.ui.components.Toaster
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/** Brings a playlist file from another app into the library. Songs get their audio when played. */
object PlaylistImport {
    /** Reads [file] and adds its playlists; says how it went in a toast either way. */
    fun import(file: PickedFile) {
        AppGraph.appScope.launch {
            try {
                val parsed = PlaylistFile.parse(file.displayName(), file.readText())
                val existing = AppGraph.prefs.playlists.first()
                val taken = existing.mapTo(HashSet()) { it.name.lowercase() }
                val added = parsed.map { p -> Playlist(UUID.randomUUID().toString(), uniqueName(p.name, taken), p.songs) }
                AppGraph.prefs.savePlaylists(existing + added)
                Toaster.show(summary(added, parsed.sumOf { it.unreadable }))
                added.forEach { fillArtwork(it.id) }
            } catch (e: PlaylistFileException) {
                Toaster.show(e.message.orEmpty())
            } catch (e: Exception) {
                Log.w("PlaylistImport", "import failed: ${e.message}")
                Toaster.show("Couldn't read that file")
            }
        }
    }

    private fun uniqueName(name: String, taken: MutableSet<String>): String {
        var candidate = name
        var n = 2
        while (candidate.lowercase() in taken) candidate = "$name ($n)".also { n++ }
        taken += candidate.lowercase()
        return candidate
    }

    internal fun summary(added: List<Playlist>, unreadable: Int): String {
        val songs = added.sumOf { it.songs.size }
        val what = if (added.size == 1) "“${added.first().name}”" else "${added.size} playlists"
        val skipped = if (unreadable > 0) ", $unreadable skipped" else ""
        return "Imported $what: $songs ${if (songs == 1) "song" else "songs"}$skipped"
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
