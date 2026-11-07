package com.daydreamin.app.ui.screens.recap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.data.taste.ListenEntry
import com.daydreamin.app.data.taste.ListeningLog
import com.daydreamin.app.data.taste.PlayStat
import com.daydreamin.app.data.taste.Recap
import com.daydreamin.app.data.taste.TasteProfile
import com.daydreamin.app.data.taste.buildRecap
import com.daydreamin.app.data.taste.recapYears
import com.daydreamin.app.platform.BackHandler
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.CardStyle
import com.daydreamin.app.ui.components.Share
import com.daydreamin.app.ui.components.Toaster
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.theme.ArtworkColors
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BrandPink
import com.daydreamin.app.ui.theme.BrandViolet
import java.time.LocalDate

private const val PAGE_MS = 7_500
/** Fewer months than this and a "loudest month" says very little. */
internal const val MIN_CHART_MONTHS = 3

/** Asked for from Home or Profile; [RecapHost] shows it over everything. */
object RecapLauncher {
    internal var open by mutableStateOf(false)
    fun show() { open = true }

    /** December, and the first days of the new year: when Home offers it. */
    val inSeason: Boolean
        get() = LocalDate.now().let { it.monthValue == 12 || (it.monthValue == 1 && it.dayOfMonth <= 7) }

    /** The year a recap made now looks back on. */
    val year: Int get() = LocalDate.now().let { if (it.monthValue == 1) it.year - 1 else it.year }
}

/** The invitation on Home, in season. */
@Composable
fun RecapBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(BrandViolet, BrandPink)))
            .pressable(onClick = RecapLauncher::show)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Your ${RecapLauncher.year} in Music", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White))
            Text("Your top songs, habits and listener type", style = TextStyle(fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f)))
        }
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

private class RecapData(val listens: List<ListenEntry>, val stats: Collection<PlayStat>)

/** Goes once at the root of the app's window. */
@Composable
fun RecapHost() {
    if (!RecapLauncher.open) return
    val data by produceState<RecapData?>(null) { value = RecapData(ListeningLog.entries(), TasteProfile.snapshot().values) }
    val loaded = data ?: return
    // Each year that has enough to say, then everything so far.
    val choices = remember(loaded) { (recapYears(loaded.listens) + RecapLauncher.year).distinct().sortedDescending().map<Int, Int?> { it } + null }
    var chosen by remember { mutableStateOf<Int?>(RecapLauncher.year) }
    val recap = remember(loaded, chosen) { buildRecap(chosen, loaded.listens, loaded.stats) }
    LaunchedEffect(recap) {
        if (recap == null) { Toaster.show("Play a few more songs first"); RecapLauncher.open = false }
    }
    recap?.let { r ->
        // Before a year has enough dated listens, it and "all time" are the same recap: no choice to offer.
        val offered = if (choices.size > 2 || r.year != null && r.title != "Your Daydreamin so far") choices else emptyList()
        androidx.compose.runtime.key(chosen) { RecapStory(r, offered, chosen, onChoose = { chosen = it }, onClose = { RecapLauncher.open = false }) }
    }
}

internal enum class RecapPage(val card: CardStyle, val one: Color? = null, val two: Color? = null) {
    INTRO(CardStyle.RECAP_SUMMARY, BrandViolet, BrandPink),
    MINUTES(CardStyle.RECAP_SUMMARY, Color(0xFFFF4D6D), Color(0xFF7A2BFF)),
    MONTHS(CardStyle.RECAP_MONTHS, Color(0xFF00C2A8), Color(0xFF2453FF)),
    CLOCK(CardStyle.RECAP_CLOCK, Color(0xFF3D2BFF), Color(0xFFFF8A3D)),
    CALENDAR(CardStyle.RECAP_CALENDAR, Color(0xFF14B86E), Color(0xFF0E7C86)),
    FIRST_SONG(CardStyle.RECAP_SONGS),
    TOP_ARTIST(CardStyle.RECAP_ARTISTS),
    ARTISTS(CardStyle.RECAP_ARTISTS, Color(0xFFFF7A18), Color(0xFFAF002D)),
    GUESS(CardStyle.RECAP_SONGS, Color(0xFFFFB800), Color(0xFFFF3D77)),
    SONGS(CardStyle.RECAP_SONGS),
    ON_REPEAT(CardStyle.RECAP_SONGS, Color(0xFFFF2E93), Color(0xFFFF8A00)),
    DISCOVERIES(CardStyle.RECAP_ARTISTS, Color(0xFF12D8FA), Color(0xFF1FA2FF)),
    TYPE(CardStyle.RECAP_TYPE, BrandPink, BrandViolet),
    SUMMARY(CardStyle.RECAP_SUMMARY),
}

private fun pagesOf(recap: Recap): List<RecapPage> = buildList {
    val time = recap.time
    add(RecapPage.INTRO); add(RecapPage.MINUTES)
    if (time != null) {
        if (time.activeMonths >= MIN_CHART_MONTHS) add(RecapPage.MONTHS)
        add(RecapPage.CLOCK)
        if (recap.year != null && time.daysListened >= 3) add(RecapPage.CALENDAR)
        if (time.firstSong != null) add(RecapPage.FIRST_SONG)
    }
    if (recap.topArtists.isNotEmpty()) add(RecapPage.TOP_ARTIST)
    if (recap.topArtists.size >= 2) add(RecapPage.ARTISTS)
    if (recap.topSongs.size >= 3) add(RecapPage.GUESS)
    if (recap.topSongs.isNotEmpty()) add(RecapPage.SONGS)
    if (time?.onRepeat != null) add(RecapPage.ON_REPEAT)
    if (!time?.newArtists.isNullOrEmpty()) add(RecapPage.DISCOVERIES)
    add(RecapPage.TYPE); add(RecapPage.SUMMARY)
}

/**
 * Your year as a story. Tap the right side for the next page and the left to go back; hold to
 * pause. On a computer the arrow keys, Space and Esc do the same.
 */
@Composable
fun RecapStory(recap: Recap, choices: List<Int?>, chosen: Int?, onChoose: (Int?) -> Unit, onClose: () -> Unit) {
    val pages = remember(recap) { pagesOf(recap) }
    var index by remember { mutableIntStateOf(0) }
    var held by remember { mutableStateOf(false) }
    var guessed by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(false) }
    val page = pages[index]
    // The guessing game waits for your answer.
    val waiting = page == RecapPage.GUESS && !guessed
    val progress = remember { Animatable(0f) }
    var running by remember { mutableIntStateOf(-1) }
    LaunchedEffect(index, held, waiting, details) {
        if (running != index) { running = index; progress.snapTo(0f) }
        if (held || waiting || details) return@LaunchedEffect
        progress.animateTo(1f, tween(((1f - progress.value) * PAGE_MS).toInt(), easing = LinearEasing))
        if (index < pages.lastIndex) index++
    }
    BackHandler { if (details) details = false else onClose() }

    val light by produceState(ArtworkLight(BrandViolet, BrandPink), recap) {
        recap.topSongs.firstOrNull()?.cover?.takeIf { it.isNotBlank() }?.let { url -> runCatching { ArtworkColors.extract(url) }.getOrNull()?.let { value = it } }
    }
    val one by animateColorAsState(page.one ?: light.key, tween(900), label = "recapOne")
    val two by animateColorAsState(page.two ?: light.fill, tween(900), label = "recapTwo")
    val drift by rememberInfiniteTransition(label = "recapDrift").animateFloat(0f, 1f, infiniteRepeatable(tween(11_000, easing = LinearEasing), RepeatMode.Reverse), label = "recapDriftValue")

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun back() { index = (index - 1).coerceAtLeast(0) }
    fun forward() { if (index < pages.lastIndex) index++ }

    fun playTop(start: Int = 0) {
        PlayerController.playFromList(recap.topSongs.map { it.toSong() }, start)
        sound = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } }
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> { forward(); true }
                    Key.DirectionLeft -> { back(); true }
                    Key.Spacebar -> { held = !held; true }
                    Key.Escape -> { if (details) details = false else onClose(); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .drawBehind {
                    drawRect(Color(0xFF08080D))
                    drawRect(Brush.radialGradient(listOf(one.copy(alpha = 0.8f), Color.Transparent), center = Offset(size.width * (0.05f + 0.5f * drift), size.height * 0.04f), radius = size.maxDimension * 0.78f))
                    drawRect(Brush.radialGradient(listOf(two.copy(alpha = 0.6f), Color.Transparent), center = Offset(size.width * (0.95f - 0.5f * drift), size.height * 0.98f), radius = size.maxDimension * 0.7f))
                }
                .pointerInput(pages.size) {
                    detectTapGestures(
                        onPress = { held = true; tryAwaitRelease(); held = false },
                        onTap = { at -> if (at.x < size.width * 0.3f) back() else forward() },
                    )
                },
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { (fadeIn(tween(320, delayMillis = 80)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 260f), initialScale = 0.95f)) togetherWith fadeOut(tween(160)) },
                label = "recapPage",
                modifier = Modifier.fillMaxSize(),
            ) { shown ->
                RecapPageContent(
                    shown, recap, choices, chosen, onChoose,
                    onGuessed = { guessed = true },
                    onPlay = ::playTop,
                    onDetails = { details = true },
                )
            }

            // The bars along the top, one per page, and the buttons.
            Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    pages.indices.forEach { i ->
                        Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
                            val filled = when { i < index -> 1f; i == index -> progress.value; else -> 0f }
                            Box(Modifier.fillMaxWidth(filled).fillMaxHeight().background(Color.White))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundButton(Icons.Rounded.Close, "Close", onClose)
                    Box(Modifier.weight(1f))
                    if (recap.topSongs.isNotEmpty()) RoundButton(if (sound) Icons.Rounded.MusicNote else Icons.Rounded.MusicOff, if (sound) "Pause your soundtrack" else "Play your soundtrack") {
                        if (sound) { PlayerController.togglePlayPauseIfPlaying(); sound = false } else playTop()
                    }
                    val style = page.card.takeIf { it != CardStyle.RECAP_CALENDAR || recap.year != null } ?: CardStyle.RECAP_SUMMARY
                    RoundButton(Icons.Rounded.Share, "Share this page") { Share.recap(recap, style) }
                }
            }
        }
        AnimatedVisibility(details, enter = slideInVertically { it / 5 } + fadeIn(), exit = slideOutVertically { it / 5 } + fadeOut()) {
            RecapDetails(recap, onPlay = ::playTop, onClose = { details = false })
        }
    }
}

@Composable
internal fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
