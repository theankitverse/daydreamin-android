package com.daydreamin.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.share.ShareLinks
import com.daydreamin.app.data.taste.TasteProfile
import com.daydreamin.app.platform.BackHandler
import com.daydreamin.app.platform.Log
import com.daydreamin.app.platform.canCopyImage
import com.daydreamin.app.platform.canSaveImage
import com.daydreamin.app.platform.copyImage
import com.daydreamin.app.platform.encodePng
import com.daydreamin.app.platform.hasSystemShare
import com.daydreamin.app.platform.saveImage
import com.daydreamin.app.platform.shareImage
import com.daydreamin.app.platform.shareTargets
import com.daydreamin.app.resources.Res
import com.daydreamin.app.resources.splash_logo
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BrandPink
import com.daydreamin.app.ui.theme.BrandViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

private const val PREVIEW_SCALE = 0.6f
private const val WIDE_LAYOUT_DP = 720

/** Goes once at the root of the app's window: the share screen, when something is being shared. */
@Composable
fun ShareHost() {
    val item = Share.pending ?: return
    ShareScreen(item, onClose = { Share.pending = null })
}

private val Title = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White, textAlign = TextAlign.Center)
private val Caption = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
private val Chip = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

/** The lyric lines worth putting on a card, two at a time, and which pair to start on (the chorus). */
private fun lyricChoices(raw: String): Pair<List<List<String>>, Int> {
    val lines = raw.lines().map { it.replace(Regex("""\[[0-9:.]+]"""), "").trim() }.filter { it.length >= 6 }
    val pairs = lines.distinct().chunked(2)
    val chorus = lines.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    return pairs to pairs.indexOfFirst { chorus in it }.coerceAtLeast(0)
}

@Composable
private fun ShareScreen(item: ShareItem, onClose: () -> Unit) {
    BackHandler { onClose() }
    val logo = painterResource(Res.drawable.splash_logo)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val drawing = remember { Mutex() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var assets by remember(item) { mutableStateOf<CardAssets?>(null) }
    var link by remember(item) { mutableStateOf(ShareLinks.PAGE) }
    var lyrics by remember(item) { mutableStateOf<List<List<String>>>(emptyList()) }
    var lyricIndex by remember(item) { mutableIntStateOf(0) }
    var tint by remember(item) { mutableStateOf(CardTint.ARTWORK) }
    var format by remember(item) { mutableStateOf(CardFormat.STORY) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var noteId by remember { mutableIntStateOf(0) }

    fun say(text: String) { note = text; noteId++ }
    LaunchedEffect(noteId) { if (note != null) { delay(2_400); note = null } }

    LaunchedEffect(item) {
        assets = loadCardAssets(item, logo)
    }
    LaunchedEffect(item) {
        val song = (item as? ShareItem.OfSong)?.song ?: return@LaunchedEffect
        val known = runCatching { TasteProfile.snapshot().values.firstOrNull { it.song.playId == song.playId }?.videoId }.getOrNull()
        link = ShareLinks.forSong(song, known)
        val found = AppGraph.repository.lyrics(song.artist, song.title).getOrNull()
        val (choices, start) = lyricChoices(found?.plainLyrics?.takeIf { it.isNotBlank() } ?: found?.syncedLyrics.orEmpty())
        lyrics = choices
        lyricIndex = start
    }

    val styles = remember(item, lyrics.isNotEmpty()) { cardStyles(item, lyrics.isNotEmpty()) }
    val startPage = (item as? ShareItem.OfRecap)?.startOn?.let(styles::indexOf)?.coerceAtLeast(0) ?: 0
    val pager = rememberPagerState(initialPage = startPage) { styles.size }
    fun specFor(style: CardStyle) = CardSpec(item, style, format, tint, lyrics.getOrNull(lyricIndex).orEmpty())
    val current = styles[pager.currentPage.coerceAtMost(styles.lastIndex)]

    /** Draws the card at full size, then hands the picture on. */
    fun withPicture(block: suspend (ByteArray) -> Unit) {
        val ready = assets
        if (busy || ready == null) return
        busy = true
        scope.launch {
            runCatching {
                val png = withContext(Dispatchers.Default) { drawing.withLock { renderCard(specFor(current), ready, measurer).encodePng() } }
                block(png)
            }.onFailure {
                Log.w("Share", "couldn't make the card: ${it.message}")
                say("Couldn't make the picture")
            }
            busy = false
        }
    }
    val message = shareText(item, link)
    val name = fileName(item)
    val targets = remember { shareTargets() }
    val actions = buildList {
        targets.forEach { t ->
            add(ShareAction(t.label, image = t.icon) {
                // Instagram takes no text with a picture, so the link goes on the clipboard for a link sticker.
                if (t.id == "com.instagram.android") clipboard.setText(AnnotatedString(link))
                withPicture { shareImage(it, name, message, t.id) }
            })
        }
        add(ShareAction("Copy link", Icons.Rounded.Link) { clipboard.setText(AnnotatedString(message)); say("Link copied") })
        if (canCopyImage) add(ShareAction("Copy image", Icons.Rounded.ContentCopy) { withPicture { copyImage(it); say("Image copied") } })
        if (canSaveImage) add(ShareAction("Save", Icons.Rounded.Download) { withPicture { png -> say(saveImage(png, name)?.let { "Saved to $it" } ?: "Couldn't save the picture") } })
        if (hasSystemShare) add(ShareAction("More", Icons.Rounded.MoreHoriz) { withPicture { shareImage(it, name, message) } })
    }

    val light = assets?.light ?: ArtworkLight(BrandViolet, BrandPink)
    val glowOne by animateColorAsState(if (tint == CardTint.ARTWORK) light.key else tint.swatch, tween(500), label = "shareGlowOne")
    val glowTwo by animateColorAsState(if (tint == CardTint.ARTWORK) light.fill else tint.swatch, tween(500), label = "shareGlowTwo")

    val heading = when (item) {
        is ShareItem.OfSong -> "Share song"
        is ShareItem.OfPlaylist -> "Share playlist"
        is ShareItem.OfRecap -> "Share your year"
    }
    val preview: @Composable (Modifier) -> Unit = { modifier ->
        CardCarousel(pager, styles, modifier, aspect = if (item is ShareItem.OfRecap) 1080f / 1920f else 1080f / format.height) { style, shape ->
            val spec = specFor(style)
            CardPreview(spec, assets, measurer, drawing, shape)
        }
    }
    val controls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StylePicker(styles, pager.currentPage) { page -> scope.launch { pager.animateScrollToPage(page) } }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                TintPicker(tint, light, Modifier.weight(1f)) { tint = it }
                if (current == CardStyle.LYRICS && lyrics.size > 1) {
                    SmallPill(Icons.Rounded.Refresh, "Next line") { lyricIndex = (lyricIndex + 1) % lyrics.size }
                } else if (item !is ShareItem.OfRecap) FormatPicker(format) { format = it }
            }
            ActionRow(actions)
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF08080D)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { }.background(
                Brush.radialGradient(listOf(glowOne.copy(alpha = 0.5f), Color.Transparent), center = Offset(0f, 0f), radius = 1400f),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, glowTwo.copy(alpha = 0.22f)))))
        val wide = maxWidth.value >= WIDE_LAYOUT_DP
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Round(Icons.Rounded.Close, "Close", onClose)
                Text(heading, style = Title, modifier = Modifier.weight(1f))
                Spacer(Modifier.size(42.dp))
            }
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    preview(Modifier.weight(1.1f).fillMaxHeight())
                    Column(Modifier.weight(1f).widthIn(max = 460.dp)) { controls() }
                }
            } else {
                preview(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
                Box(Modifier.padding(bottom = 14.dp)) { controls() }
            }
        }
        AnimatedVisibility(note != null || busy, Modifier.align(Alignment.TopCenter).padding(top = 64.dp).safeDrawingPadding(), enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
            Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Making your picture" else note.orEmpty(), style = Chip.copy(color = Color.Black))
            }
        }
    }
}

private class ShareAction(val label: String, val icon: ImageVector? = null, val image: ImageBitmap? = null, val onClick: () -> Unit)

@Composable
private fun Round(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** The cards side by side: swipe between them, the one in the middle a little larger. */
@Composable
private fun CardCarousel(pager: PagerState, styles: List<CardStyle>, modifier: Modifier, aspect: Float, card: @Composable (CardStyle, RoundedCornerShape) -> Unit) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val fitted = minOf(maxHeight * aspect, maxWidth - 72.dp)
        val shape = RoundedCornerShape(20.dp)
        HorizontalPager(
            state = pager,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = (maxWidth - fitted) / 2),
            pageSpacing = 10.dp,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) { page ->
            val distance = kotlin.math.abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().aspectRatio(aspect).graphicsLayer {
                    val s = 1f - 0.1f * distance
                    scaleX = s; scaleY = s
                    alpha = 1f - 0.45f * distance
                },
            ) { card(styles[page], shape) }
        }
    }
}

@Composable
private fun CardPreview(spec: CardSpec, assets: CardAssets?, measurer: TextMeasurer, drawing: Mutex, shape: RoundedCornerShape) {
    var picture by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(spec, assets) {
        val ready = assets ?: return@LaunchedEffect
        picture = withContext(Dispatchers.Default) { drawing.withLock { renderCard(spec, ready, measurer, PREVIEW_SCALE) } }
    }
    val shown by animateFloatAsState(if (picture != null) 1f else 0f, tween(260), label = "sharePreview")
    Box(Modifier.fillMaxSize().shadow(18.dp, shape).clip(shape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        picture?.let { Image(it, contentDescription = spec.style.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown }) }
            ?: CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp)
    }
}

@Composable
private fun StylePicker(styles: List<CardStyle>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        styles.forEachIndexed { i, style ->
            val on = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White else Color.White.copy(alpha = 0.1f)).pressable(onClick = { onPick(i) }).padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(style.label, style = Chip.copy(color = if (on) Color.Black else Color.White)) }
        }
    }
}

@Composable
private fun TintPicker(selected: CardTint, light: ArtworkLight, modifier: Modifier, onPick: (CardTint) -> Unit) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        CardTint.entries.forEach { tint ->
            val fill = if (tint == CardTint.ARTWORK) Brush.linearGradient(listOf(light.key, light.fill)) else Brush.linearGradient(listOf(tint.swatch, tint.swatch))
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .border(2.dp, if (tint == selected) Color.White else Color.White.copy(alpha = 0.18f), CircleShape)
                    .padding(4.dp).clip(CircleShape).background(fill).pressable(onClick = { onPick(tint) }),
            )
        }
    }
}

@Composable
private fun FormatPicker(selected: CardFormat, onPick: (CardFormat) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(3.dp)) {
        CardFormat.entries.forEach { format ->
            val on = format == selected
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White.copy(alpha = 0.24f) else Color.Transparent).pressable(onClick = { onPick(format) }).padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(format.label, style = Chip.copy(fontSize = 13.sp, color = Color.White)) }
        }
    }
}

@Composable
private fun SmallPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)).pressable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = Chip.copy(fontSize = 13.sp, color = Color.White))
    }
}

@Composable
private fun ActionRow(actions: List<ShareAction>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        actions.forEach { action ->
            Column(Modifier.width(78.dp).clip(RoundedCornerShape(14.dp)).pressable(onClick = action.onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    when {
                        action.image != null -> Image(action.image, contentDescription = null, modifier = Modifier.size(52.dp).clip(CircleShape))
                        action.icon != null -> Icon(action.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        else -> Icon(Icons.Rounded.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(action.label, style = Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
