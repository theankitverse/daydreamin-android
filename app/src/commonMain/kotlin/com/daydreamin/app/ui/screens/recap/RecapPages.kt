package com.daydreamin.app.ui.screens.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.data.taste.Recap
import com.daydreamin.app.data.taste.RecapArtist
import com.daydreamin.app.data.taste.RecapSong
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.Share
import com.daydreamin.app.ui.components.SongActions
import com.daydreamin.app.ui.components.hourName
import com.daydreamin.app.ui.components.pressable
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Huge = TextStyle(fontWeight = FontWeight.Black, fontSize = 92.sp, lineHeight = 96.sp, letterSpacing = (-3).sp, color = Color.White, textAlign = TextAlign.Center)
private val Big = TextStyle(fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 45.sp, letterSpacing = (-1).sp, color = Color.White, textAlign = TextAlign.Center)
private val Heading = Big.copy(fontSize = 31.sp, lineHeight = 36.sp)
private val Line = TextStyle(fontSize = 18.sp, lineHeight = 25.sp, color = Color.White.copy(alpha = 0.82f), textAlign = TextAlign.Center)
private val Eyebrow = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.5.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
private val Row1 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White)
private val Row2 = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = 0.68f))

private fun plays(n: Int) = "%,d %s".format(n, if (n == 1) "play" else "plays")
private fun monthName(index: Int) = java.time.Month.of(index + 1).getDisplayName(DateTextStyle.FULL, Locale.ENGLISH)

private fun minutesLine(minutes: Long): String = when {
    minutes >= 1_440 -> "That's ${minutes / 1_440} full ${if (minutes / 1_440 == 1L) "day" else "days"} of music, or about ${"%,d".format(minutes / 150)} movies."
    minutes >= 300 -> "That's ${minutes / 60} hours, or about ${minutes / 150} movies."
    minutes >= 60 -> "That's ${minutes / 60} ${if (minutes / 60 == 1L) "hour" else "hours"} of music."
    else -> "And this is only the start."
}

private fun partOfDay(hour: Int) = when (hour) {
    in 5..11 -> "You start the day with music"
    in 12..16 -> "Your afternoons have a soundtrack"
    in 17..21 -> "Evenings are when you press play"
    else -> "You come alive after dark"
}

/** Slides up into place, [order] steps after the page appears. */
@Composable
private fun Rise(order: Int = 0, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(120L + order * 140L); shown = true }
    val t by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 260f), label = "recapRise")
    Box(Modifier.graphicsLayer { alpha = t.coerceIn(0f, 1f); translationY = (1f - t) * 60f }) { content() }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 26.dp).padding(top = 84.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun reveal(): Float {
    val value = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(260); value.animateTo(1f, tween(1_300)) }
    return value.value
}

@Composable
internal fun RecapPageContent(
    page: RecapPage, recap: Recap, choices: List<Int?>, chosen: Int?, onChoose: (Int?) -> Unit,
    onGuessed: () -> Unit, onPlay: (Int) -> Unit, onDetails: () -> Unit,
) {
    when (page) {
        RecapPage.INTRO -> Intro(recap, choices, chosen, onChoose)
        RecapPage.MINUTES -> Minutes(recap)
        RecapPage.MONTHS -> Months(recap)
        RecapPage.CLOCK -> Clock(recap)
        RecapPage.CALENDAR -> Calendar(recap)
        RecapPage.FIRST_SONG -> FirstSong(recap)
        RecapPage.TOP_ARTIST -> TopArtist(recap)
        RecapPage.ARTISTS -> Artists(recap)
        RecapPage.GUESS -> Guess(recap, onGuessed)
        RecapPage.SONGS -> Songs(recap, onPlay)
        RecapPage.ON_REPEAT -> OnRepeat(recap)
        RecapPage.DISCOVERIES -> Discoveries(recap)
        RecapPage.TYPE -> ListenerPage(recap)
        RecapPage.SUMMARY -> Summary(recap, onPlay, onDetails)
    }
}

/** The year's covers, circling it. */
@Composable
private fun CoverOrbit(covers: List<String>, size: Int = 330, cover: Int = 70, center: @Composable () -> Unit) {
    val spin by rememberInfiniteTransition(label = "recapOrbit").animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart), label = "recapOrbitAngle")
    val radius = with(LocalDensity.current) { ((size - cover) / 2f).dp.toPx() }
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        covers.forEachIndexed { i, url ->
            val offset = 2f * PI.toFloat() * i / covers.size
            Box(Modifier.offset { IntOffset((cos(spin + offset) * radius).roundToInt(), (sin(spin + offset) * radius).roundToInt()) }) {
                Artwork(url = url, shape = RoundedCornerShape(16.dp), modifier = Modifier.size(cover.dp))
            }
        }
        center()
    }
}

@Composable
private fun Intro(recap: Recap, choices: List<Int?>, chosen: Int?, onChoose: (Int?) -> Unit) = Page {
    val covers = remember(recap) { (recap.topSongs.map { it.cover } + recap.topArtists.map { it.cover }).filter { it.isNotBlank() }.distinct().take(6) }
    Rise {
        CoverOrbit(covers) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(recap.year?.toString().takeIf { recap.title.contains(it ?: "-") } ?: "So far", style = Huge.copy(fontSize = if (recap.title.any(Char::isDigit)) 54.sp else 44.sp, lineHeight = 58.sp, letterSpacing = (-2).sp))
                Text("IN MUSIC", style = Eyebrow)
            }
        }
    }
    Spacer(Modifier.height(26.dp))
    Rise(1) { Text(recap.title, style = Big.copy(fontSize = 32.sp, lineHeight = 38.sp)) }
    Spacer(Modifier.height(12.dp))
    Rise(2) { Text("%,d plays, %,d songs, %,d artists. Let's look back.".format(recap.plays, recap.songs, recap.artists), style = Line) }
    if (choices.isNotEmpty()) {
        Spacer(Modifier.height(22.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { year ->
                val on = year == chosen
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White else Color.White.copy(alpha = 0.14f)).pressable(onClick = { onChoose(year) }).padding(horizontal = 15.dp, vertical = 8.dp)) {
                    Text(year?.toString() ?: "All time", style = Row1.copy(fontSize = 14.sp, color = if (on) Color.Black else Color.White))
                }
            }
        }
    }
}

@Composable
private fun CountUp(target: Long, style: TextStyle = Huge) {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) { value.animateTo(target.toFloat(), tween(1_500)) }
    Text("%,d".format(value.value.toLong()), style = style)
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.1f)).padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Row1.copy(fontWeight = FontWeight.Black, fontSize = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = Row2.copy(fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 2)
    }
}

@Composable
private fun Minutes(recap: Recap) = Page {
    Rise { Text("You listened for", style = Line) }
    Spacer(Modifier.height(8.dp))
    Rise(1) { CountUp(recap.minutes, Huge.copy(fontSize = if (recap.minutes >= 100_000) 68.sp else 84.sp)) }
    Rise(2) { Text("minutes", style = Line.copy(fontSize = 22.sp)) }
    Spacer(Modifier.height(18.dp))
    Rise(3) { Text(minutesLine(recap.minutes), style = Line) }
    Spacer(Modifier.height(26.dp))
    Rise(4) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("%,d".format(recap.plays), "plays", Modifier.weight(1f))
            Stat("%,d".format(recap.songs), "different songs", Modifier.weight(1f))
            Stat("%,d".format(recap.artists), "artists", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MonthChart(recap: Recap, height: Int = 230) {
    val time = recap.time ?: return
    val measurer = rememberTextMeasurer()
    val t = reveal()
    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
        drawBars(measurer, time.minutesByMonth, MONTH_LETTERS, time.peakMonth, size, Offset.Zero, Color.White.copy(alpha = 0.32f), Color.White, Color.White.copy(alpha = 0.7f), 13.dp.toPx(), t)
    }
}

@Composable
private fun Months(recap: Recap) = Page {
    val time = recap.time ?: return@Page
    Rise { Text("${monthName(time.peakMonth)} was your loudest month", style = Heading) }
    Spacer(Modifier.height(26.dp))
    Rise(1) { MonthChart(recap) }
    Spacer(Modifier.height(20.dp))
    Rise(2) { Text("%,d minutes in ${monthName(time.peakMonth)} alone.".format(time.minutesByMonth[time.peakMonth]), style = Line) }
}

@Composable
private fun DayDial(recap: Recap, side: Int = 280) {
    val time = recap.time ?: return
    val measurer = rememberTextMeasurer()
    val t = reveal()
    Box(Modifier.size(side.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawClock(measurer, time.minutesByHour, Offset(size.width / 2f, size.height / 2f), size.width * 0.44f, Color.White.copy(alpha = 0.32f), Color.White, Color.White.copy(alpha = 0.7f), 12.dp.toPx(), t)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PEAK", style = Eyebrow.copy(fontSize = 10.sp))
            Text(hourName(time.peakHour), style = Heading)
        }
    }
}

@Composable
private fun Clock(recap: Recap) = Page {
    val time = recap.time ?: return@Page
    Rise { Text(partOfDay(time.peakHour), style = Heading) }
    Spacer(Modifier.height(18.dp))
    Rise(1) { DayDial(recap) }
    Spacer(Modifier.height(16.dp))
    Rise(2) { Text("${time.peakWeekday.getDisplayName(DateTextStyle.FULL, Locale.ENGLISH)} is your favourite day to listen.", style = Line) }
}

@Composable
private fun YearDots(recap: Recap) {
    val time = recap.time ?: return
    val year = recap.year ?: return
    val measurer = rememberTextMeasurer()
    val t = reveal()
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.35f)) {
        drawYearGrid(measurer, year, time.minutesByDay, size, Offset.Zero, Color.White, Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.7f), 11.dp.toPx(), t)
    }
}

@Composable
private fun Calendar(recap: Recap) = Page {
    val time = recap.time ?: return@Page
    Rise { Text("You showed up on ${time.daysListened} days", style = Heading) }
    Spacer(Modifier.height(20.dp))
    Rise(1) { YearDots(recap) }
    Spacer(Modifier.height(16.dp))
    Rise(2) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${time.longestStreak}", "days in a row", Modifier.weight(1f))
            Stat("%,d".format(time.biggestDay?.value ?: 0), "minutes on your best day", Modifier.weight(1f))
            Stat(time.biggestDay?.key?.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)).orEmpty(), "your best day", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpinningCover(url: String, side: Int) {
    val turn by rememberInfiniteTransition(label = "recapVinyl").animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "recapVinylAngle")
    Box(Modifier.size(side.dp).graphicsLayer { rotationZ = turn }.clip(CircleShape).background(Color(0xFF0B0B0F)), contentAlignment = Alignment.Center) {
        Artwork(url = url, shape = CircleShape, modifier = Modifier.size((side * 0.62f).dp))
        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF0B0B0F)))
    }
}

@Composable
private fun FirstSong(recap: Recap) = Page {
    val song = recap.time?.firstSong ?: return@Page
    Rise { Text(if (recap.year != null) "IT ALL STARTED WITH" else "WHERE IT ALL BEGAN", style = Eyebrow) }
    Spacer(Modifier.height(22.dp))
    Rise(1) { SpinningCover(song.cover, 240) }
    Spacer(Modifier.height(24.dp))
    Rise(2) { Text(song.title, style = Big.copy(fontSize = 30.sp, lineHeight = 35.sp), maxLines = 3, overflow = TextOverflow.Ellipsis) }
    Spacer(Modifier.height(6.dp))
    Rise(3) { Text(song.artist, style = Line) }
    Spacer(Modifier.height(16.dp))
    Rise(4) { Text(if (song.plays > 1) "The first song you really listened to. ${"%,d".format(song.plays)} plays later, here we are." else "The first song you really listened to.", style = Line.copy(fontSize = 15.sp)) }
}

@Composable
private fun TopArtist(recap: Recap) {
    val artist = recap.topArtists.firstOrNull() ?: return
    val zoom by rememberInfiniteTransition(label = "recapZoom").animateFloat(1f, 1.12f, infiniteRepeatable(tween(9_000), RepeatMode.Reverse), label = "recapZoomValue")
    Box(Modifier.fillMaxSize()) {
        Artwork(url = artist.cover, shape = RoundedCornerShape(0.dp), edge = false, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; alpha = 0.6f })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.3f), 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))))
        Column(Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(horizontal = 26.dp).padding(bottom = 60.dp)) {
            Rise { Text("YOUR NUMBER ONE ARTIST", style = Eyebrow) }
            Spacer(Modifier.height(10.dp))
            Rise(1) { Text(artist.name, style = Big.copy(fontSize = 50.sp, lineHeight = 54.sp, textAlign = TextAlign.Start), maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(14.dp))
            val share = if (recap.plays > 0) (artist.plays * 100 / recap.plays).coerceAtLeast(1) else 0
            Rise(2) { Text("${plays(artist.plays)}, %,d minutes. That's $share%% of everything you played.".format(artist.minutes), style = Line.copy(textAlign = TextAlign.Start)) }
        }
    }
}

/** A place in a top list. */
@Composable
private fun RankRow(rank: Int, cover: String, title: String, subtitle: String, round: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = if (round) CircleShape else RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = Row1.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(34.dp))
        Artwork(url = cover, shape = shape, modifier = Modifier.size(56.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = Row1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Row2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Artists(recap: Recap) = Page {
    Rise { Text("Your top artists", style = Heading) }
    Spacer(Modifier.height(20.dp))
    recap.topArtists.take(5).forEachIndexed { i, a -> Rise(i + 1) { RankRow(i + 1, a.cover, a.name, "${plays(a.plays)} · %,d min".format(a.minutes), round = true) } }
}

@Composable
private fun Guess(recap: Recap, onGuessed: () -> Unit) = Page {
    val winner = recap.topSongs.first()
    val options = remember(recap) { recap.topSongs.take(3).shuffled(java.util.Random(recap.plays.toLong())) }
    var picked by remember { mutableStateOf<RecapSong?>(null) }
    Rise { Text(if (picked == null) "Guess your number one song" else if (picked == winner) "You know yourself" else "So close", style = Heading) }
    Spacer(Modifier.height(8.dp))
    Rise(1) {
        Text(
            when (picked) {
                null -> "Tap the one you played most."
                else -> "“${winner.title}” took the top spot with ${plays(winner.plays)}."
            },
            style = Line,
        )
    }
    Spacer(Modifier.height(18.dp))
    options.forEachIndexed { i, song ->
        Rise(i + 2) {
            val answered = picked != null
            val right = song == winner
            val edge = when { answered && right -> Color(0xFF5CFF9D); answered && song == picked -> Color(0xFFFF6B7A); else -> Color.Transparent }
            val dim by animateFloatAsState(if (answered && !right) 0.45f else 1f, tween(300), label = "recapGuessDim")
            Row(
                Modifier.padding(vertical = 5.dp).fillMaxWidth().graphicsLayer { alpha = dim }.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f))
                    .border(2.dp, edge, RoundedCornerShape(16.dp))
                    .pressable(onClick = { if (picked == null) { picked = song; onGuessed() } }).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(url = song.cover, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(60.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(song.title, style = Row1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = Row2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (answered && right) Icon(Icons.Rounded.Check, contentDescription = "The right answer", tint = edge, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun Songs(recap: Recap, onPlay: (Int) -> Unit) = Page {
    val first = recap.topSongs.first()
    Rise { Text("YOUR TOP SONGS", style = Eyebrow) }
    Spacer(Modifier.height(14.dp))
    Rise(1) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Artwork(url = first.cover, shape = RoundedCornerShape(20.dp), modifier = Modifier.size(180.dp).pressable(onClick = { onPlay(0) }))
            Box(Modifier.padding(8.dp).size(38.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(22.dp))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Rise(2) { Text(first.title, style = Big.copy(fontSize = 25.sp, lineHeight = 30.sp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
    Rise(2) { Text("${first.artist} · ${plays(first.plays)}", style = Line.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    Spacer(Modifier.height(12.dp))
    recap.topSongs.drop(1).take(4).forEachIndexed { i, s ->
        Rise(i + 3) { RankRow(i + 2, s.cover, s.title, "${s.artist} · ${plays(s.plays)}", onClick = { onPlay(i + 1) }) }
    }
}

@Composable
private fun OnRepeat(recap: Recap) = Page {
    val repeat = recap.time?.onRepeat ?: return@Page
    val pulse by rememberInfiniteTransition(label = "recapPulse").animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "recapPulseValue")
    Rise { Text("ONE DAY, ONE SONG", style = Eyebrow) }
    Spacer(Modifier.height(24.dp))
    Rise(1) {
        Box(contentAlignment = Alignment.Center) {
            listOf(290 to 0.08f, 250 to 0.14f).forEach { (side, alpha) ->
                Box(Modifier.size(side.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape).background(Color.White.copy(alpha = alpha)))
            }
            Artwork(url = repeat.song.cover, shape = CircleShape, modifier = Modifier.size(200.dp))
        }
    }
    Spacer(Modifier.height(24.dp))
    Rise(2) { Text("${repeat.plays} times", style = Huge.copy(fontSize = 56.sp, lineHeight = 60.sp)) }
    Spacer(Modifier.height(8.dp))
    Rise(3) { Text("You played “${repeat.song.title}” ${repeat.plays} times on ${repeat.date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))}. We don't know what happened that day, but we hope it was good.", style = Line) }
}

@Composable
private fun Discoveries(recap: Recap) = Page {
    val found = recap.time?.newArtists.orEmpty()
    Rise { Text("${found.size} new ${if (found.size == 1) "artist" else "artists"} found you", style = Heading) }
    Spacer(Modifier.height(8.dp))
    Rise(1) { Text("Your favourites among them:", style = Line) }
    Spacer(Modifier.height(16.dp))
    found.take(5).forEachIndexed { i, a -> Rise(i + 2) { RankRow(i + 1, a.cover, a.name, plays(a.plays), round = true) } }
}

@Composable
private fun ListenerPage(recap: Recap) = Page {
    val spin by rememberInfiniteTransition(label = "recapBadge").animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(2_400), RepeatMode.Reverse), label = "recapBadgeValue")
    Rise { Text("YOU ARE", style = Eyebrow) }
    Spacer(Modifier.height(20.dp))
    Rise(1) {
        Box(contentAlignment = Alignment.Center) {
            listOf(250 to 0.07f, 200 to 0.11f, 150 to 0.17f).forEachIndexed { i, (side, alpha) ->
                Box(Modifier.size(side.dp).graphicsLayer { val s = 1f + (spin - 1f) * (i + 1) / 2f; scaleX = s; scaleY = s }.clip(CircleShape).background(Color.White.copy(alpha = alpha)))
            }
            Artwork(url = recap.topSongs.firstOrNull()?.cover, shape = CircleShape, modifier = Modifier.size(104.dp))
        }
    }
    Spacer(Modifier.height(22.dp))
    Rise(2) { Text(recap.type.title, style = Big) }
    Spacer(Modifier.height(12.dp))
    Rise(3) { Text(recap.type.line, style = Line) }
    recap.time?.let { time ->
        Spacer(Modifier.height(22.dp))
        Rise(4) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(hourName(time.peakHour), "peak hour", Modifier.weight(1f))
                Stat(time.peakWeekday.getDisplayName(DateTextStyle.SHORT, Locale.ENGLISH), "favourite day", Modifier.weight(1f))
                Stat("${time.longestStreak}", "day streak", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, modifier: Modifier = Modifier, solid: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(if (solid) Color.White else Color.White.copy(alpha = 0.15f)).pressable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val ink = if (solid) Color.Black else Color.White
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = Row1.copy(fontSize = 14.sp, color = ink), maxLines = 1)
    }
}

@Composable
private fun Summary(recap: Recap, onPlay: (Int) -> Unit, onDetails: () -> Unit) = Page {
    var saved by remember { mutableStateOf(false) }
    Rise { Text(recap.title, style = Heading) }
    Spacer(Modifier.height(16.dp))
    Rise(1) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.1f)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(url = recap.topSongs.firstOrNull()?.cover, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(92.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("%,d".format(recap.minutes), style = Big.copy(fontSize = 34.sp, lineHeight = 38.sp, textAlign = TextAlign.Start))
                    Text("minutes listened", style = Row2)
                    Spacer(Modifier.height(4.dp))
                    Text(recap.type.title, style = Row1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TopThree("TOP ARTISTS", recap.topArtists.take(3).map { it.name }, Modifier.weight(1f))
                TopThree("TOP SONGS", recap.topSongs.take(3).map { it.title }, Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Rise(2) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(Icons.Rounded.Share, "Share your year", Modifier.fillMaxWidth(), solid = true) { Share.recap(recap) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(Icons.Rounded.PlayArrow, "Play top songs", Modifier.weight(1f)) { onPlay(0) }
                Action(if (saved) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.PlaylistAdd, if (saved) "Saved" else "Save playlist", Modifier.weight(1f)) {
                    if (!saved) {
                        SongActions.savePlaylist(recap.title.removePrefix("Your ").let { "My $it" }, recap.topSongs.map { it.toSong() }, "recap:${recap.year ?: "all"}", author = "Daydreamin")
                        saved = true
                    }
                }
            }
            Action(Icons.Rounded.BarChart, "See all your stats", Modifier.fillMaxWidth(), onClick = onDetails)
        }
    }
}

@Composable
private fun TopThree(label: String, names: List<String>, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = Eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.5.sp, textAlign = TextAlign.Start))
        Spacer(Modifier.height(4.dp))
        names.forEachIndexed { i, name -> Text("${i + 1}  $name", style = Row1.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** Everything at once, to scroll through at your own pace. */
@Composable
internal fun RecapDetails(recap: Recap, onPlay: (Int) -> Unit, onClose: () -> Unit) {
    val time = recap.time
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B11)).safeDrawingPadding()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 68.dp, bottom = 32.dp)) {
            item { Text(recap.title, style = Big.copy(fontSize = 30.sp, lineHeight = 35.sp, textAlign = TextAlign.Start)) }
            item {
                Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("%,d".format(recap.minutes), "minutes", Modifier.weight(1f))
                        Stat("%,d".format(recap.minutes / 60), "hours", Modifier.weight(1f))
                        Stat("%,d".format(recap.plays), "plays", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("%,d".format(recap.songs), "songs", Modifier.weight(1f))
                        Stat("%,d".format(recap.artists), "artists", Modifier.weight(1f))
                        Stat(if (recap.plays > 0) "${recap.minutes * 60 / recap.plays / 60}m" else "0m", "average play", Modifier.weight(1f))
                    }
                    if (time != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("${time.daysListened}", "days listened", Modifier.weight(1f))
                        Stat("${time.longestStreak}", "longest streak", Modifier.weight(1f))
                        Stat("${time.newArtists.size}", "new artists", Modifier.weight(1f))
                    }
                }
            }
            if (time != null) {
                if (time.activeMonths >= MIN_CHART_MONTHS) item { Section("Month by month"); MonthChart(recap, 190) }
                item { Section("Your day"); Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DayDial(recap, 260) } }
                item {
                    Section("Your week")
                    val measurer = rememberTextMeasurer()
                    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                        drawBars(measurer, time.minutesByWeekday, DAY_LETTERS, time.peakWeekday.value - 1, size, Offset.Zero, Color.White.copy(alpha = 0.32f), Color.White, Color.White.copy(alpha = 0.7f), 13.dp.toPx())
                    }
                }
                if (recap.year != null) item { Section("Your year, day by day"); YearDots(recap) }
            }
            item { Section("Top ${recap.topSongs.size} songs") }
            itemsIndexed(recap.topSongs) { i, s -> RankRow(i + 1, s.cover, s.title, "${s.artist} · ${plays(s.plays)}", onClick = { onPlay(i) }) }
            item { Section("Top ${recap.topArtists.size} artists") }
            itemsIndexed(recap.topArtists) { i, a -> RankRow(i + 1, a.cover, a.name, "${plays(a.plays)} · %,d min".format(a.minutes), round = true) }
        }
        Box(Modifier.padding(12.dp)) { RoundButton(Icons.Rounded.Close, "Close", onClose) }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = Row1.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
}
