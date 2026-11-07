package com.daydreamin.app.data.share

import com.daydreamin.app.data.model.Song
import java.net.URLDecoder
import java.net.URLEncoder

/** What a shared link asks for. */
sealed interface SharedLink {
    /** The sender's [title] and [artist] travel along: YouTube's own are often "Artist - Song (Official Video)". */
    data class Video(val id: String, val title: String? = null, val artist: String? = null) : SharedLink
    data class Search(val query: String) : SharedLink
    /** Just the app: a link shared with a playlist or a recap. */
    data object App : SharedLink
}

/**
 * Daydreamin's own links. Tapped on a device with the app they open it; anywhere else a small page
 * at [PAGE] sends you to the download. Android lets the app claim them because the site publishes
 * the app's signing fingerprint (.well-known/assetlinks.json; the site's source is in share-site/).
 */
object ShareLinks {
    const val PAGE = "https://theankitverse.github.io/s/"
    private const val MAX_QUERY = 200
    private const val MAX_NAME = 120

    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")
    private val ourLink = Regex("""(?:https://theankitverse\.github\.io/s/?|daydreamin://[a-z]*/?)(?:\?[^\s]*)?""")

    fun forSong(song: Song, knownVideoId: String? = null): String {
        val id = (song.videoId ?: knownVideoId)?.takeIf { videoId.matches(it) }
        fun encoded(text: String) = URLEncoder.encode(text, "UTF-8")
        return if (id != null) "$PAGE?v=$id&t=${encoded(song.title.take(MAX_NAME))}&a=${encoded(song.artist.take(MAX_NAME))}"
        else PAGE + "?q=" + encoded("${song.artist} ${song.title}".take(MAX_QUERY))
    }

    /** The first of our links in [text] (a message may have words around it). Anything else is null. */
    fun parse(text: String): SharedLink? {
        val link = ourLink.find(text)?.value ?: return null
        val params = link.substringAfter('?', "").split('&').mapNotNull { pair ->
            val (key, value) = pair.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            key to (runCatching { URLDecoder.decode(value, "UTF-8") }.getOrNull() ?: return@mapNotNull null)
        }.toMap()
        fun name(key: String) = params[key]?.trim()?.take(MAX_NAME)?.takeIf { it.isNotEmpty() }
        params["v"]?.takeIf { videoId.matches(it) }?.let { return SharedLink.Video(it, name("t"), name("a")) }
        params["q"]?.trim()?.takeIf { it.isNotEmpty() }?.let { return SharedLink.Search(it.take(MAX_QUERY)) }
        return SharedLink.App
    }
}
