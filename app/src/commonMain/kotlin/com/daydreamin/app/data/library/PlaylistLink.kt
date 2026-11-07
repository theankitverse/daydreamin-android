package com.daydreamin.app.data.library

/** What a pasted link points at. */
sealed interface PlaylistLink {
    /** [url] is the playlist's one true address, whatever form was pasted. */
    data class YouTube(val url: String) : PlaylistLink
    data class Deezer(val id: String) : PlaylistLink {
        val url get() = "https://www.deezer.com/playlist/$id"
    }
    /** A deezer.page.link / link.deezer.com short link: where it leads is only known by following it. */
    data class DeezerShort(val url: String) : PlaylistLink
    /** Spotify lets small apps read nothing: its playlists come in through Exportify. */
    data object Spotify : PlaylistLink
    /** A service with no way in for an app like this one. */
    data class Closed(val service: String) : PlaylistLink

    companion object {
        private val youTubeHost = Regex("""^https?://(?:(?:www|m|music)\.)?(?:youtube\.com|youtu\.be)/""", RegexOption.IGNORE_CASE)
        private val listParam = Regex("""[?&]list=([A-Za-z0-9_-]+)""")
        private val deezerPlaylist = Regex("""^https?://(?:www\.)?deezer\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?playlist/(\d+)""", RegexOption.IGNORE_CASE)
        private val deezerShort = Regex("""^https?://(?:link\.deezer\.com|deezer\.page\.link|dzr\.page\.link)/\S+""", RegexOption.IGNORE_CASE)
        private val spotify = Regex("""^(?:https?://(?:open\.spotify\.com|spotify\.link)/|spotify:playlist:)""", RegexOption.IGNORE_CASE)
        private val closed = listOf("music.apple.com" to "Apple Music", "tidal.com" to "TIDAL", "music.amazon." to "Amazon Music", "soundcloud.com" to "SoundCloud")

        /** The link in [text] (apps often share "Listen to this: https://…"), or null if there isn't one we know. */
        fun parse(text: String): PlaylistLink? {
            val link = Regex("""(?:https?://|spotify:)\S+""").find(text)?.value?.trimEnd('.', ',', ')') ?: return null
            if (spotify.containsMatchIn(link)) return Spotify
            if (youTubeHost.containsMatchIn(link)) {
                val list = listParam.find(link)?.groupValues?.get(1) ?: return null
                return YouTube("https://www.youtube.com/playlist?list=$list")
            }
            deezerPlaylist.find(link)?.let { return Deezer(it.groupValues[1]) }
            if (deezerShort.containsMatchIn(link)) return DeezerShort(link)
            return closed.firstOrNull { it.first in link.lowercase() }?.let { Closed(it.second) }
        }
    }
}
