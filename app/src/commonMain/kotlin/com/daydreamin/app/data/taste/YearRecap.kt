package com.daydreamin.app.data.taste

import java.time.Instant
import java.time.ZoneId

data class RecapSong(val title: String, val artist: String, val cover: String, val plays: Int)
data class RecapArtist(val name: String, val cover: String, val plays: Int)

/** A kind of listener, for the recap's last page. */
enum class ListenerType(val title: String, val line: String) {
    NIGHT_OWL("The Night Owl", "Your best listening happens after dark."),
    REPEAT_ROMANTIC("The Repeat Romantic", "When you love a song, you really love it."),
    SUPERFAN("The Superfan", "One artist has your heart, and you're not hiding it."),
    EXPLORER("The Explorer", "Always somewhere new. Your ears don't stay put."),
    ALL_ROUNDER("The All-Rounder", "A bit of everything, and all of it good."),
}

data class Recap(
    /** "Your 2026 in Music", or "Your Daydreamin so far" until there's a year's worth of dated listens. */
    val title: String,
    val minutes: Long,
    val plays: Int,
    val songs: Int,
    val artists: Int,
    val topSongs: List<RecapSong>,
    val topArtists: List<RecapArtist>,
    val type: ListenerType,
)

/** What a recap needs before it's worth showing. */
private const val MIN_PLAYS = 5
/** Fewer dated listens than this in the year: all-time play counts tell the story better. */
private const val FULL_YEAR_LISTENS = 200
/** A song with no known length, in the all-time counts. */
private const val TYPICAL_SONG_MS = 210_000L

/** Record labels whose YouTube channels stand in for the artist on many songs: not anyone's favourite artist. */
private val LABEL_CHANNELS = setOf(
    "t-series", "tseries", "sony music india", "sony music", "tips official", "tips music", "zee music company", "saregama music",
    "saregama", "yrf", "yash raj films", "speed records", "eros now music", "shemaroo", "times music", "universal music india",
    "venus", "desi music factory", "white hill music", "lahari music", "aditya music", "vevo",
)

/** The artist to credit: the first name of a list, without YouTube's channel dressing. Null for a label's channel. */
internal fun recapArtist(raw: String): String? {
    val name = raw
        .split(Regex("""\s*(?:,|&|;|\bfeat\.?|\bft\.?|\bx\b|\bwith\b)\s*""", RegexOption.IGNORE_CASE)).first()
        .removeSuffix(" - Topic").removeSuffix("VEVO").trim()
    return name.takeIf { it.isNotBlank() && it != "Unknown" && it.lowercase() !in LABEL_CHANNELS }
}

/**
 * Builds the recap of [year]: from the dated [listens] when there are enough of them, otherwise
 * from the all-time play counts in [stats] (the listening log only starts when the app gets it).
 * Null when there's too little to say anything yet.
 */
fun buildRecap(year: Int, listens: List<ListenEntry>, stats: Collection<PlayStat>, zone: ZoneId = ZoneId.systemDefault()): Recap? {
    val thisYear = listens.filter { Instant.ofEpochMilli(it.atMs).atZone(zone).year == year }
    return if (thisYear.size >= FULL_YEAR_LISTENS) fromListens("Your $year in Music", thisYear, zone)
    else fromStats("Your Daydreamin so far", stats, thisYear, zone)
}

private fun fromListens(title: String, listens: List<ListenEntry>, zone: ZoneId): Recap? {
    if (listens.size < MIN_PLAYS) return null
    val perSong = listens.groupBy { it.playId }.values.map { list ->
        val latest = list.last()
        RecapSong(latest.title, latest.artist, latest.cover, list.size)
    }
    val night = listens.count { Instant.ofEpochMilli(it.atMs).atZone(zone).hour.let { h -> h >= 22 || h < 4 } }
    return assemble(title, listens.sumOf { it.listenedMs } / 60_000, perSong, nightShare = night.toFloat() / listens.size)
}

private fun fromStats(title: String, stats: Collection<PlayStat>, listens: List<ListenEntry>, zone: ZoneId): Recap? {
    val played = stats.filter { it.plays > 0 && !it.mostlySkipped }
    val plays = played.sumOf { it.plays }
    if (plays < MIN_PLAYS) return null
    val perSong = played.map { RecapSong(it.song.title, it.song.artist, it.song.cover.ifBlank { it.song.artworkUrl }, it.plays) }
    val ms = played.sumOf { s -> s.plays * (s.song.duration * 1000L).takeIf { it > 0 }.let { it ?: TYPICAL_SONG_MS } }
    val night = listens.count { Instant.ofEpochMilli(it.atMs).atZone(zone).hour.let { h -> h >= 22 || h < 4 } }
    return assemble(title, ms / 60_000, perSong, nightShare = if (listens.isEmpty()) 0f else night.toFloat() / listens.size)
}

private fun assemble(title: String, minutes: Long, perSong: List<RecapSong>, nightShare: Float): Recap {
    val plays = perSong.sumOf { it.plays }
    val songs = perSong.sortedByDescending { it.plays }
    val artists = perSong
        .mapNotNull { s -> recapArtist(s.artist)?.let { it to s } }
        .groupBy({ it.first }, { it.second })
        .map { (name, list) -> RecapArtist(name, list.maxBy { it.plays }.cover, list.sumOf { it.plays }) }
        .sortedByDescending { it.plays }
    val type = when {
        nightShare >= 0.35f -> ListenerType.NIGHT_OWL
        songs.first().plays >= plays * 0.15f && songs.first().plays >= 5 -> ListenerType.REPEAT_ROMANTIC
        artists.isNotEmpty() && artists.first().plays >= plays * 0.3f -> ListenerType.SUPERFAN
        artists.size >= plays * 0.4f -> ListenerType.EXPLORER
        else -> ListenerType.ALL_ROUNDER
    }
    return Recap(title, minutes, plays, perSong.size, artists.size, songs.take(5), artists.take(5), type)
}
