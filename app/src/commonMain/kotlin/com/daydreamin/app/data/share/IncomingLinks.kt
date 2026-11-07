package com.daydreamin.app.data.share

import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.repository.toSong
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import com.daydreamin.app.platform.Log
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Plays the song a tapped link points at, once the app is ready for it (setup done, player up). */
object IncomingLinks {
    /** Brings up Now Playing; each platform shows it its own way. */
    var showPlayer: () -> Unit = {}

    fun open(text: String?): Boolean {
        val link = text?.let(ShareLinks::parse) ?: return false
        if (link == SharedLink.App) return true
        AppGraph.appScope.launch {
            AppGraph.prefs.onboarded.first { it }
            Toaster.show("Opening the song…")
            val song = runCatching { find(link) }.onFailure { Log.w("Links", "couldn't open a link: ${it.message}") }.getOrNull()
            if (song == null) { Toaster.show("Couldn't find that song"); return@launch }
            PlayerController.awaitReady()
            withContext(Dispatchers.Main) {
                PlayerController.playSong(song)
                showPlayer()
            }
        }
        return true
    }

    private suspend fun find(link: SharedLink): Song? = when (link) {
        is SharedLink.Video ->
            if (link.title != null && link.artist != null) {
                val cover = "https://i.ytimg.com/vi/${link.id}/hqdefault.jpg"
                Song(id = link.id, title = link.title, artist = link.artist, cover = cover, coverXl = cover, videoId = link.id)
            } else YouTubeExtractorService.resolveVideoId(link.id).track?.toSong()
        is SharedLink.Search -> YouTubeExtractorService.search(link.query, limit = 1).firstOrNull()?.toSong()
        SharedLink.App -> null
    }
}
