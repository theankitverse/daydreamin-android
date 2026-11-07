package com.daydreamin.app.ui.screens.recap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.data.taste.ListeningLog
import com.daydreamin.app.data.taste.Recap
import com.daydreamin.app.data.taste.TasteProfile
import com.daydreamin.app.data.taste.buildRecap
import com.daydreamin.app.platform.BackHandler
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.Share
import com.daydreamin.app.ui.components.Toaster
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.theme.BrandPink
import com.daydreamin.app.ui.theme.BrandViolet
import kotlinx.coroutines.delay
import java.time.LocalDate

private const val PAGE_MS = 6_500

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
            Text("Your top songs, artists and minutes", style = TextStyle(fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f)))
        }
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

/** Goes once at the root of the app's window. */
@Composable
fun RecapHost() {
    if (!RecapLauncher.open) return
    val recap by produceState<Recap?>(null) {
        value = buildRecap(RecapLauncher.year, ListeningLog.entries(), TasteProfile.snapshot().values)
        if (value == null) { Toaster.show("Play a few more songs first"); RecapLauncher.open = false }
    }
    recap?.let { RecapStory(it, onClose = { RecapLauncher.open = false }) }
}

/** Your year as a story: tap the right side for the next page, the left to go back. */
@Composable
fun RecapStory(recap: Recap, onClose: () -> Unit) {
    val pages = remember(recap) {
        buildList {
            add(RecapPage.INTRO); add(RecapPage.MINUTES)
            if (recap.topSongs.isNotEmpty()) add(RecapPage.SONGS)
            if (recap.topArtists.isNotEmpty()) add(RecapPage.ARTISTS)
            add(RecapPage.TYPE); add(RecapPage.SUMMARY)
        }
    }
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(index) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(PAGE_MS, easing = LinearEasing))
        if (index < pages.lastIndex) index++
    }
    BackHandler { onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        val page = pages[index]
        val hue by animateFloatAsState(index.toFloat() / pages.size, tween(900), label = "recapLight")
        Box(
            Modifier
                .fillMaxHeight()
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(0.dp))
                .drawBehind {
                    drawRect(Color(0xFF08080D))
                    drawRect(Brush.radialGradient(listOf(BrandViolet.copy(alpha = 0.75f), Color.Transparent), center = Offset(size.width * (0.1f + hue), 0f), radius = size.maxDimension * 0.8f))
                    drawRect(Brush.radialGradient(listOf(BrandPink.copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width * (1f - hue), size.height), radius = size.maxDimension * 0.7f))
                }
                .pointerInput(pages.size) {
                    detectTapGestures { at ->
                        if (at.x < size.width * 0.3f) index = (index - 1).coerceAtLeast(0)
                        else if (index < pages.lastIndex) index++
                        else onClose()
                    }
                }
                .safeDrawingPadding(),
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { (fadeIn(tween(320, delayMillis = 80)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 260f), initialScale = 0.94f)) togetherWith fadeOut(tween(160)) },
                label = "recapPage",
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(top = 76.dp, bottom = 64.dp),
            ) { shown -> PageContent(shown, recap) }

            // The bars along the top, one per page, and the way out.
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pages.indices.forEach { i ->
                        Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
                            val filled = when { i < index -> 1f; i == index -> progress.value; else -> 0f }
                            Box(Modifier.fillMaxWidth(filled).fillMaxHeight().background(Color.White))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    RoundButton(Icons.Rounded.Close, "Close", onClose)
                    RoundButton(Icons.Rounded.Share, "Share") { Share.recap(recap) }
                }
            }
        }
    }
}

private enum class RecapPage { INTRO, MINUTES, SONGS, ARTISTS, TYPE, SUMMARY }

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

private val Big = TextStyle(fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1).sp, color = Color.White, textAlign = TextAlign.Center)
private val Line = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
private val Row1 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White)
private val Row2 = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = 0.65f))

@Composable
private fun PageContent(page: RecapPage, recap: Recap) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (page) {
            RecapPage.INTRO -> {
                Text(recap.title, style = Big)
                Spacer(Modifier.height(16.dp))
                Text("${recap.plays} plays, ${recap.songs} songs, ${recap.artists} artists. Let's look back.", style = Line)
            }
            RecapPage.MINUTES -> {
                Text("You listened for", style = Line)
                Spacer(Modifier.height(10.dp))
                CountUp(recap.minutes)
                Text("minutes", style = Line)
                Spacer(Modifier.height(18.dp))
                Text(minutesLine(recap.minutes), style = Line)
            }
            RecapPage.SONGS -> {
                Text("Your top songs", style = Big.copy(fontSize = 34.sp, lineHeight = 40.sp))
                Spacer(Modifier.height(22.dp))
                recap.topSongs.forEachIndexed { i, s -> RankRow(i, s.cover, s.title, "${s.artist} · ${plays(s.plays)}", i) }
            }
            RecapPage.ARTISTS -> {
                Text("Your top artists", style = Big.copy(fontSize = 34.sp, lineHeight = 40.sp))
                Spacer(Modifier.height(22.dp))
                recap.topArtists.forEachIndexed { i, a -> RankRow(i, a.cover, a.name, plays(a.plays), i) }
            }
            RecapPage.TYPE -> {
                Text("You are", style = Line)
                Spacer(Modifier.height(10.dp))
                Text(recap.type.title, style = Big)
                Spacer(Modifier.height(16.dp))
                Text(recap.type.line, style = Line)
            }
            RecapPage.SUMMARY -> {
                Text(recap.title, style = Big.copy(fontSize = 32.sp, lineHeight = 38.sp))
                Spacer(Modifier.height(20.dp))
                recap.topSongs.firstOrNull()?.let { Artwork(url = it.cover, shape = RoundedCornerShape(18.dp), modifier = Modifier.size(200.dp)) }
                Spacer(Modifier.height(20.dp))
                Text("%,d minutes".format(recap.minutes), style = Big.copy(fontSize = 30.sp, lineHeight = 36.sp))
                Text(recap.type.title, style = Line)
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White).pressable(onClick = { Share.recap(recap) }).padding(horizontal = 26.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share", style = Row1.copy(color = Color.Black))
                }
            }
        }
    }
}

private fun plays(n: Int) = "$n ${if (n == 1) "play" else "plays"}"

private fun minutesLine(minutes: Long): String = when {
    minutes >= 1_440 -> "That's ${minutes / 1_440} full ${if (minutes / 1_440 == 1L) "day" else "days"} of music."
    minutes >= 60 -> "That's ${minutes / 60} hours of music."
    else -> "And this is only the start."
}

@Composable
private fun CountUp(target: Long) {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) { value.animateTo(target.toFloat(), tween(1_400)) }
    Text("%,d".format(value.value.toLong()), style = Big.copy(fontSize = 72.sp, lineHeight = 80.sp))
}

/** A place in a top five; the rows arrive one after another. */
@Composable
private fun RankRow(rank: Int, cover: String, title: String, subtitle: String, order: Int) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(120L + order * 130L); shown = true }
    val t by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "recapRow")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp).graphicsLayer { alpha = t; translationY = (1f - t) * 40f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${rank + 1}", style = Row1.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(30.dp))
        Artwork(url = cover, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(56.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = Row1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Row2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
