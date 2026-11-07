package com.daydreamin.app.data.library

import com.daydreamin.app.data.model.Song

/** A playlist read from a file: its name, its songs, and how many lines couldn't be read as a song. */
data class ParsedPlaylist(val name: String, val songs: List<Song>, val unreadable: Int)

/** Not a playlist file we can read, with a message fit to show as-is. */
class PlaylistFileException(message: String) : Exception(message)

/**
 * Reads playlists exported from other apps. Only names go in: a song here has a title and an
 * artist, and the player finds its audio from those when it's played, as it does for chart songs.
 *
 * CSV: Exportify (Spotify), TuneMyMusic and anything with title and artist columns. M3U/M3U8 with
 * "#EXTINF" lines. Plain text with one "Artist - Title" per line. One file can hold several
 * playlists (TuneMyMusic's "Playlist name" column).
 */
object PlaylistFile {
    /** Far bigger than any playlist: past this it's the wrong file, and isn't worth holding in memory. */
    private const val MAX_BYTES = 20 * 1024 * 1024
    private const val NOT_A_PLAYLIST = "That isn't a playlist file. Use a CSV from Exportify or TuneMyMusic, or an M3U playlist."

    /** The text of a picked file; refuses one too big to be a playlist. */
    fun read(input: java.io.InputStream): String = input.use { stream ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BYTES) throw PlaylistFileException(NOT_A_PLAYLIST)
        }
        out.toString("UTF-8")
    }

    /** An app, a picture, a song: bytes that aren't text have zero bytes and invalid characters all through them. */
    private fun looksBinary(text: String): Boolean {
        val sample = text.take(8_192)
        return sample.any { it == '\u0000' } || sample.count { it == '�' } > sample.length / 50
    }

    fun parse(fileName: String, text: String): List<ParsedPlaylist> {
        val body = text.removePrefix("﻿")
        if (looksBinary(body)) throw PlaylistFileException(NOT_A_PLAYLIST)
        val fallbackName = fileName.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { "Imported playlist" }
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val playlists = when {
            extension in setOf("m3u", "m3u8") || body.trimStart().startsWith("#EXTM3U") -> listOf(parseM3u(fallbackName, body))
            extension == "csv" || looksLikeCsv(body) -> parseCsv(fallbackName, body)
            else -> listOf(parseLines(fallbackName, body))
        }
        if (playlists.all { it.songs.isEmpty() }) throw PlaylistFileException("No songs found in this file. Use a CSV from Exportify or TuneMyMusic, or an M3U playlist.")
        return playlists.filter { it.songs.isNotEmpty() }
    }

    private fun looksLikeCsv(text: String): Boolean {
        val first = text.lineSequence().firstOrNull().orEmpty().lowercase()
        return "," in first && ("track" in first || "title" in first || "song" in first)
    }

    // ---- CSV

    private fun parseCsv(fallbackName: String, text: String): List<ParsedPlaylist> {
        val rows = csvRows(text)
        val header = rows.firstOrNull()?.map { it.trim().lowercase() } ?: return emptyList()
        fun column(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val title = column("track name", "title", "song", "song name", "name", "track")
        val artist = column("artist name(s)", "artist names", "artist name", "artist", "artists")
        if (title < 0 || artist < 0) throw PlaylistFileException("This CSV has no song and artist columns.")
        val album = column("album name", "album")
        val cover = column("album image url", "image url", "artwork")
        val durationMs = column("track duration (ms)", "duration (ms)", "duration_ms")
        val playlist = column("playlist name", "playlist")

        val grouped = LinkedHashMap<String, MutableList<Song>>()
        var unreadable = 0
        for (row in rows.drop(1)) {
            if (row.all { it.isBlank() }) continue
            val t = row.getOrNull(title)?.trim().orEmpty()
            val a = row.getOrNull(artist)?.trim().orEmpty()
            if (t.isBlank() || a.isBlank()) { unreadable++; continue }
            val song = importedSong(
                title = t,
                // Exportify separates artists with semicolons or commas.
                artist = a.split(';').joinToString(", ") { it.trim() },
                album = album.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim().orEmpty(),
                cover = cover.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim().orEmpty(),
                durationSec = durationMs.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim()?.toLongOrNull()?.let { (it / 1000).toInt() } ?: 0,
            )
            val name = playlist.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim().orEmpty().ifBlank { fallbackName }
            grouped.getOrPut(name) { mutableListOf() } += song
        }
        return grouped.map { (name, songs) -> ParsedPlaylist(name, songs.distinctBy { it.playId }, unreadable) }
            .ifEmpty { listOf(ParsedPlaylist(fallbackName, emptyList(), unreadable)) }
    }

    /** Rows of fields, with quoted fields holding commas, line breaks and doubled quotes. */
    internal fun csvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.clear()
                    rows += row; row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }

    // ---- M3U and plain lists

    private fun parseM3u(name: String, text: String): ParsedPlaylist {
        val songs = mutableListOf<Song>()
        var unreadable = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (!line.startsWith("#EXTINF", ignoreCase = true)) continue
            val duration = line.substringAfter(':').substringBefore(',').trim().toIntOrNull() ?: 0
            val song = songFromLabel(line.substringAfter(',', ""), duration.coerceAtLeast(0))
            if (song != null) songs += song else unreadable++
        }
        return ParsedPlaylist(name, songs.distinctBy { it.playId }, unreadable)
    }

    private fun parseLines(name: String, text: String): ParsedPlaylist {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val songs = lines.mapNotNull { songFromLabel(it, 0) }
        return ParsedPlaylist(name, songs.distinctBy { it.playId }, lines.size - songs.size)
    }

    /** "Artist - Title". */
    private fun songFromLabel(label: String, durationSec: Int): Song? {
        val parts = label.split(" - ", limit = 2).map { it.trim() }
        if (parts.size < 2 || parts.any { it.isBlank() }) return null
        return importedSong(title = parts[1], artist = parts[0], durationSec = durationSec)
    }

    private fun importedSong(title: String, artist: String, album: String = "", cover: String = "", durationSec: Int = 0): Song =
        Song(
            // The same song imported twice is the same song (likes, downloads and plays are kept by id).
            id = "imported-" + "${artist.lowercase()}|${title.lowercase()}".hashCode().toUInt().toString(16),
            title = title,
            artist = artist,
            album = album.ifBlank { "Single" },
            cover = cover,
            duration = durationSec,
        )
}
