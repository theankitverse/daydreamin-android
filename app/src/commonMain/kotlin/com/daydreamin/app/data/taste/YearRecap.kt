package com.daydreamin.app.data.taste

import com.daydreamin.app.data.model.Song
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RecapSong(
    val title: String,
    val artist: String,
    val cover: String,
    val plays: Int,
    val minutes: Long = 0,
    val playId: String = "",
    val videoId: String? = null,
) {
    /** Enough of a song to play it again. */
    fun toSong() = Song(id = playId.ifBlank { videoId.orEmpty() }, title = title, artist = artist, cover = cover, coverXl = cover, videoId = videoId)
}

data class RecapArtist(val name: String, val cover: String, val plays: Int, val minutes: Long = 0)

/** A kind of listener, for the recap's last pages. */
enum class ListenerType(val title: String, val line: String) {
    NIGHT_OWL("The Night Owl", "Your best listening happens after dark."),
    EARLY_BIRD("The Early Bird", "Music first, then the rest of the day."),
    REPEAT_ROMANTIC("The Repeat Romantic", "When you love a song, you really love it."),
    SUPERFAN("The Superfan", "One artist has your heart, and you're not hiding it."),
    WEEKENDER("The Weekender", "Saturdays and Sundays belong to your speakers."),
    REGULAR("The Regular", "Rain or shine, the music is on. Every single day."),
    EXPLORER("The Explorer", "Always somewhere new. Your ears don't stay put."),
    ALL_ROUNDER("The All-Rounder", "A bit of everything, and all of it good."),
}

/** The day you played one song more than any other song on any other day. */
data class RepeatDay(val song: RecapSong, val date: LocalDate, val plays: Int)

/** Everything that needs dates, so it's only there when enough dated listens are. */
data class RecapTime(
    /** Twelve months, January first. */
    val minutesByMonth: List<Long>,
    /** Twenty-four hours, midnight first. */
    val minutesByHour: List<Long>,
    /** Seven days, Monday first. */
    val minutesByWeekday: List<Long>,
    val minutesByDay: Map<LocalDate, Long>,
    val longestStreak: Int,
    /** Null when the log began partway through: the first song logged wasn't really the first. */
    val firstSong: RecapSong?,
    val onRepeat: RepeatDay?,
    /** Artists first heard well after the log began, the most played first. */
    val newArtists: List<RecapArtist>,
) {
    val daysListened: Int get() = minutesByDay.size
    val biggestDay: Map.Entry<LocalDate, Long>? get() = minutesByDay.maxByOrNull { it.value }
    /** Which month was loudest, 0 for January. */
    val peakMonth: Int get() = minutesByMonth.indices.maxBy { minutesByMonth[it] }
    val peakHour: Int get() = minutesByHour.indices.maxBy { minutesByHour[it] }
    val peakWeekday: DayOfWeek get() = DayOfWeek.of(minutesByWeekday.indices.maxBy { minutesByWeekday[it] } + 1)
    val activeMonths: Int get() = minutesByMonth.count { it > 0 }
}

data class Recap(
    /** "Your 2026 in Music", or "Your Daydreamin so far" until there's a year's worth of dated listens. */
    val title: String,
    val minutes: Long,
    val plays: Int,
    val songs: Int,
    val artists: Int,
    /** Up to ten of each: the story shows five, the full list all of them. */
    val topSongs: List<RecapSong>,
    val topArtists: List<RecapArtist>,
    val type: ListenerType,
    val year: Int? = null,
    val time: RecapTime? = null,
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

/** Fewer dated listens than this and the clock, calendar and months would only be noise. */
private const val MIN_DATED_LISTENS = 20
private const val TOP_COUNT = 10
/** The log needs to have run this long before anyone can be called new. */
private const val DISCOVERY_LOG_DAYS = 60L
private const val DISCOVERY_GRACE_DAYS = 30L

/** The years with a real recap of their own, newest first. */
fun recapYears(listens: List<ListenEntry>, zone: ZoneId = ZoneId.systemDefault()): List<Int> =
    listens.groupingBy { Instant.ofEpochMilli(it.atMs).atZone(zone).year }.eachCount()
        .filterValues { it >= FULL_YEAR_LISTENS }.keys.sortedDescending()

/**
 * Builds the recap of [year] (null: everything so far): from the dated [listens] when there are
 * enough of them, otherwise from the all-time play counts in [stats] (the listening log only
 * starts when the app gets it). Null when there's too little to say anything yet.
 */
fun buildRecap(year: Int?, listens: List<ListenEntry>, stats: Collection<PlayStat>, zone: ZoneId = ZoneId.systemDefault()): Recap? {
    val inScope = if (year == null) listens else listens.filter { Instant.ofEpochMilli(it.atMs).atZone(zone).year == year }
    val videoIds = stats.associate { it.song.playId to (it.videoId ?: it.song.videoId) }
    val time = recapTime(inScope, listens, videoIds, zone)
    return if (year != null && inScope.size >= FULL_YEAR_LISTENS) fromListens("Your $year in Music", year, inScope, time, videoIds)
    else fromStats("Your Daydreamin so far", stats, time, year)
}

private val videoIdShape = Regex("^[A-Za-z0-9_-]{11}$")
private fun videoIdOf(playId: String, known: Map<String, String?>) = known[playId] ?: playId.takeIf { videoIdShape.matches(it) }

private fun recapSong(list: List<ListenEntry>, known: Map<String, String?>): RecapSong {
    val latest = list.maxBy { it.atMs }
    return RecapSong(latest.title, latest.artist, latest.cover, list.size, list.sumOf { it.listenedMs } / 60_000, latest.playId, videoIdOf(latest.playId, known))
}

private fun recapTime(listens: List<ListenEntry>, everything: List<ListenEntry>, known: Map<String, String?>, zone: ZoneId): RecapTime? {
    if (listens.size < MIN_DATED_LISTENS) return null
    val dated = listens.sortedBy { it.atMs }.map { it to Instant.ofEpochMilli(it.atMs).atZone(zone) }
    val months = LongArray(12)
    val hours = LongArray(24)
    val weekdays = LongArray(7)
    val days = sortedMapOf<LocalDate, Long>()
    for ((entry, at) in dated) {
        months[at.monthValue - 1] += entry.listenedMs
        hours[at.hour] += entry.listenedMs
        weekdays[at.dayOfWeek.value - 1] += entry.listenedMs
        days[at.toLocalDate()] = (days[at.toLocalDate()] ?: 0L) + entry.listenedMs
    }
    var streak = 0
    var best = 0
    var previous: LocalDate? = null
    for (day in days.keys) {
        streak = if (previous?.plusDays(1) == day) streak + 1 else 1
        best = maxOf(best, streak)
        previous = day
    }
    val repeat = dated.groupBy { (entry, at) -> entry.playId to at.toLocalDate() }.maxByOrNull { it.value.size }
        ?.takeIf { it.value.size >= 3 }
        ?.let { (key, list) -> RepeatDay(recapSong(listens.filter { it.playId == key.first }, known), key.second, list.size) }
    return RecapTime(
        minutesByMonth = months.map { it / 60_000 },
        minutesByHour = hours.map { it / 60_000 },
        minutesByWeekday = weekdays.map { it / 60_000 },
        minutesByDay = days.mapValues { it.value / 60_000 },
        longestStreak = best,
        firstSong = dated.first().takeIf { (first, at) -> at.monthValue <= 2 || everything.any { it.atMs < first.atMs } }
            ?.first?.let { first -> recapSong(listens.filter { it.playId == first.playId }, known) },
        onRepeat = repeat,
        newArtists = newArtists(listens, everything, zone),
    )
}

private fun newArtists(listens: List<ListenEntry>, everything: List<ListenEntry>, zone: ZoneId): List<RecapArtist> {
    val logBegan = everything.minOfOrNull { it.atMs } ?: return emptyList()
    val logEnded = everything.maxOf { it.atMs }
    val day = 86_400_000L
    if (logEnded - logBegan < DISCOVERY_LOG_DAYS * day) return emptyList()
    val firstHeard = HashMap<String, Long>()
    for (e in everything) recapArtist(e.artist)?.let { name -> firstHeard.merge(name, e.atMs, ::minOf) }
    return listens.mapNotNull { e -> recapArtist(e.artist)?.let { it to e } }
        .groupBy({ it.first }, { it.second })
        .filter { (name, _) -> (firstHeard[name] ?: logBegan) >= logBegan + DISCOVERY_GRACE_DAYS * day }
        .map { (name, list) -> RecapArtist(name, list.last().cover, list.size, list.sumOf { it.listenedMs } / 60_000) }
        .sortedByDescending { it.plays }
}

private fun fromListens(title: String, year: Int, listens: List<ListenEntry>, time: RecapTime?, known: Map<String, String?>): Recap? {
    if (listens.size < MIN_PLAYS) return null
    val perSong = listens.groupBy { it.playId }.values.map { recapSong(it, known) }
    return assemble(title, listens.sumOf { it.listenedMs } / 60_000, perSong, time, year)
}

private fun fromStats(title: String, stats: Collection<PlayStat>, time: RecapTime?, year: Int?): Recap? {
    val played = stats.filter { it.plays > 0 && !it.mostlySkipped }
    val plays = played.sumOf { it.plays }
    if (plays < MIN_PLAYS) return null
    fun lengthMs(s: PlayStat) = (s.song.duration * 1000L).takeIf { it > 0 } ?: TYPICAL_SONG_MS
    val perSong = played.map {
        RecapSong(it.song.title, it.song.artist, it.song.cover.ifBlank { it.song.artworkUrl }, it.plays, it.plays * lengthMs(it) / 60_000, it.song.playId, it.videoId ?: it.song.videoId)
    }
    return assemble(title, played.sumOf { s -> s.plays * lengthMs(s) } / 60_000, perSong, time, year)
}

private fun assemble(title: String, minutes: Long, perSong: List<RecapSong>, time: RecapTime?, year: Int?): Recap {
    val plays = perSong.sumOf { it.plays }
    val songs = perSong.sortedByDescending { it.plays }
    val artists = perSong
        .mapNotNull { s -> recapArtist(s.artist)?.let { it to s } }
        .groupBy({ it.first }, { it.second })
        .map { (name, list) -> RecapArtist(name, list.maxBy { it.plays }.cover, list.sumOf { it.plays }, list.sumOf { it.minutes }) }
        .sortedByDescending { it.plays }
    return Recap(title, minutes, plays, perSong.size, artists.size, songs.take(TOP_COUNT), artists.take(TOP_COUNT), listenerType(plays, songs, artists, time), year, time)
}

private fun listenerType(plays: Int, songs: List<RecapSong>, artists: List<RecapArtist>, time: RecapTime?): ListenerType {
    fun shareOf(hours: Iterable<Int>): Float {
        val total = time?.minutesByHour?.sum()?.takeIf { it > 0 } ?: return 0f
        return hours.sumOf { time.minutesByHour[it] }.toFloat() / total
    }
    val weekend = time?.minutesByWeekday?.let { w -> w.sum().takeIf { it > 0 }?.let { (w[5] + w[6]).toFloat() / it } } ?: 0f
    val spanDays = time?.minutesByDay?.keys?.let { d -> java.time.temporal.ChronoUnit.DAYS.between(d.first(), d.last()) + 1 } ?: 0L
    return when {
        shareOf((22..23) + (0..3)) >= 0.35f -> ListenerType.NIGHT_OWL
        shareOf(5..8) >= 0.3f -> ListenerType.EARLY_BIRD
        songs.first().plays >= plays * 0.15f && songs.first().plays >= 5 -> ListenerType.REPEAT_ROMANTIC
        artists.isNotEmpty() && artists.first().plays >= plays * 0.3f -> ListenerType.SUPERFAN
        weekend >= 0.5f -> ListenerType.WEEKENDER
        time != null && spanDays >= 30 && time.daysListened >= spanDays * 0.7f -> ListenerType.REGULAR
        artists.size >= plays * 0.4f -> ListenerType.EXPLORER
        else -> ListenerType.ALL_ROUNDER
    }
}
