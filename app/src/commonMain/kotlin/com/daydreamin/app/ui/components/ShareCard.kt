package com.daydreamin.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.Playlist
import com.daydreamin.app.data.share.ShareLinks
import com.daydreamin.app.data.taste.Recap
import com.daydreamin.app.platform.forOffscreenDrawing
import com.daydreamin.app.platform.platformContext
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.screens.recap.DAY_LETTERS
import com.daydreamin.app.ui.screens.recap.MONTH_LETTERS
import com.daydreamin.app.ui.screens.recap.drawBars
import com.daydreamin.app.ui.screens.recap.drawClock
import com.daydreamin.app.ui.screens.recap.drawYearGrid
import com.daydreamin.app.ui.theme.ArtworkColors
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BrandPink
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.artworkModel
import com.daydreamin.app.ui.theme.artworkZoom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private const val W = 1080f

/** Something to share. Asked for from anywhere; [ShareHost] opens the share screen for it. */
sealed interface ShareItem {
    data class OfSong(val song: Song) : ShareItem
    data class OfPlaylist(val playlist: Playlist) : ShareItem
    data class OfRecap(val recap: Recap, val startOn: CardStyle? = null) : ShareItem
}

object Share {
    internal var pending by mutableStateOf<ShareItem?>(null)

    fun song(song: Song) { pending = ShareItem.OfSong(song) }
    fun playlist(playlist: Playlist) { pending = ShareItem.OfPlaylist(playlist) }
    fun recap(recap: Recap, startOn: CardStyle? = null) { pending = ShareItem.OfRecap(recap, startOn) }
}

enum class CardFormat(val label: String, val height: Float) { STORY("Story", 1920f), SQUARE("Square", 1080f) }

enum class CardStyle(val label: String) {
    AURA("Aura"), POSTER("Poster"), VINYL("Vinyl"), PLAYER("Player"), TICKET("Ticket"), LYRICS("Lyrics"),
    RECAP_SUMMARY("Summary"), RECAP_SONGS("Top songs"), RECAP_ARTISTS("Top artists"), RECAP_MONTHS("Months"),
    RECAP_CLOCK("Clock"), RECAP_CALENDAR("Calendar"), RECAP_TYPE("Listener"),
}

/** The colours a card can wear. */
enum class CardTint(val label: String, val swatch: Color) {
    ARTWORK("From the artwork", Color.Transparent),
    MIDNIGHT("Midnight", BrandViolet),
    SUNSET("Sunset", Color(0xFFFF6A3D)),
    OCEAN("Ocean", Color(0xFF1FA2FF)),
    FOREST("Forest", Color(0xFF2ECC71)),
    PAPER("Paper", Color(0xFFF3EEE3)),
}

internal class CardColors(val one: Color, val two: Color, val base: Color, val ink: Color, val light: Boolean = false) {
    val soft get() = ink.copy(alpha = 0.72f)
    val faint get() = ink.copy(alpha = 0.16f)
}

private fun colorsFor(tint: CardTint, art: ArtworkLight) = when (tint) {
    CardTint.ARTWORK -> CardColors(art.key, art.fill, Color(0xFF09090E), Color.White)
    CardTint.MIDNIGHT -> CardColors(BrandViolet, BrandPink, Color(0xFF08080F), Color.White)
    CardTint.SUNSET -> CardColors(Color(0xFFFF6A3D), Color(0xFFFFB347), Color(0xFF180810), Color.White)
    CardTint.OCEAN -> CardColors(Color(0xFF1FA2FF), Color(0xFF12D8FA), Color(0xFF04121D), Color.White)
    CardTint.FOREST -> CardColors(Color(0xFF2ECC71), Color(0xFFA8E063), Color(0xFF05130D), Color.White)
    CardTint.PAPER -> CardColors(Color(0xFFE8B4A0), Color(0xFFB7C9E2), Color(0xFFF3EEE3), Color(0xFF17151C), light = true)
}

/** What's fixed about a card while its look is being chosen: pictures already fetched and cut to size. */
class CardAssets internal constructor(
    internal val covers: Map<String, ImageBitmap>,
    internal val lead: List<ImageBitmap>,
    internal val backdrop: ImageBitmap?,
    internal val light: ArtworkLight,
    internal val logo: ImageBitmap?,
)

data class CardSpec(val item: ShareItem, val style: CardStyle, val format: CardFormat, val tint: CardTint, val lyric: List<String> = emptyList())

/** The looks on offer for [item], [lyrics] being whether a lyric card can be made. */
fun cardStyles(item: ShareItem, lyrics: Boolean = false): List<CardStyle> = when (item) {
    is ShareItem.OfSong -> listOf(CardStyle.AURA, CardStyle.POSTER, CardStyle.VINYL, CardStyle.PLAYER, CardStyle.TICKET) + if (lyrics) listOf(CardStyle.LYRICS) else emptyList()
    is ShareItem.OfPlaylist -> listOf(CardStyle.AURA, CardStyle.POSTER, CardStyle.VINYL, CardStyle.TICKET)
    is ShareItem.OfRecap -> buildList {
        val r = item.recap
        add(CardStyle.RECAP_SUMMARY)
        if (r.topSongs.isNotEmpty()) add(CardStyle.RECAP_SONGS)
        if (r.topArtists.isNotEmpty()) add(CardStyle.RECAP_ARTISTS)
        r.time?.let { t ->
            if (t.activeMonths >= com.daydreamin.app.ui.screens.recap.MIN_CHART_MONTHS) add(CardStyle.RECAP_MONTHS)
            add(CardStyle.RECAP_CLOCK)
            if (r.year != null && t.daysListened >= 3) add(CardStyle.RECAP_CALENDAR)
        }
        add(CardStyle.RECAP_TYPE)
    }
}

internal fun shareText(item: ShareItem, link: String): String = when (item) {
    is ShareItem.OfSong -> "${item.song.title} by ${item.song.artist}\nListen on Daydreamin: $link"
    is ShareItem.OfPlaylist -> "My playlist “${item.playlist.name}”: ${item.playlist.songs.take(5).joinToString { it.title }}\nMade with Daydreamin: $link"
    is ShareItem.OfRecap -> "${item.recap.title}: ${"%,d".format(item.recap.minutes)} minutes, and I'm ${item.recap.type.title}.\nMade with Daydreamin: $link"
}

internal fun fileName(item: ShareItem): String {
    val name = when (item) {
        is ShareItem.OfSong -> item.song.title
        is ShareItem.OfPlaylist -> item.playlist.name
        is ShareItem.OfRecap -> item.recap.title
    }
    return "Daydreamin - " + name.replace(Regex("""[\\/:*?"<>|]"""), "").take(60).trim() + ".png"
}

private suspend fun loadArt(url: String): Painter? {
    if (url.isBlank()) return null
    val request = ImageRequest.Builder(platformContext).data(artworkModel(url)).forOffscreenDrawing().build()
    val result = SingletonImageLoader.get(platformContext).execute(request)
    return (result as? SuccessResult)?.image?.asPainter(platformContext)
}

/** A square copy of [painter], cropped from the middle and zoomed past YouTube's letterbox bars. */
private fun raster(painter: Painter, side: Int, zoom: Float = 1f): ImageBitmap? {
    val source = painter.intrinsicSize
    val fits = source.width > 0f && source.height > 0f
    val bitmap = ImageBitmap(side, side)
    val extent = side.toFloat()
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(extent, extent)) {
        if (!fits) with(painter) { draw(Size(extent, extent)) }
        else {
            val fit = extent / minOf(source.width, source.height) * zoom
            val w = source.width * fit
            val h = source.height * fit
            translate(left = (extent - w) / 2f, top = (extent - h) / 2f) { with(painter) { draw(Size(w, h)) } }
        }
    }
    return bitmap
}

/**
 * A soft blur of the picture. There's no blur to draw with on every Android version, so it's made
 * by shrinking the picture to a few pixels and averaging many slightly shifted enlargements of them.
 */
private fun smear(image: ImageBitmap): ImageBitmap {
    val tiny = ImageBitmap(16, 16)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(tiny), Size(16f, 16f)) {
        drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset.Zero, IntSize(16, 16), filterQuality = FilterQuality.Medium)
    }
    val side = 128
    val bleed = 48
    val soft = ImageBitmap(side, side)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(soft), Size(side.toFloat(), side.toFloat())) {
        var layers = 0
        for (y in -3..3) for (x in -3..3) {
            layers++
            drawImage(
                tiny, IntOffset.Zero, IntSize(16, 16), IntOffset(x * 12 - bleed, y * 12 - bleed), IntSize(side + bleed * 2, side + bleed * 2),
                alpha = 1f / layers, filterQuality = FilterQuality.Medium,
            )
        }
    }
    return soft
}

/** Fetches what [item]'s cards need. Runs on the main thread: painters aren't safe to draw elsewhere. */
suspend fun loadCardAssets(item: ShareItem, logo: Painter): CardAssets {
    val lead = when (item) {
        is ShareItem.OfSong -> listOf(item.song.artworkUrl.ifBlank { item.song.cover })
        is ShareItem.OfPlaylist -> item.playlist.coverUrl?.let(::listOf) ?: item.playlist.songs.map { it.artworkUrl.ifBlank { it.cover } }.filter { it.isNotBlank() }.distinct().take(4)
        is ShareItem.OfRecap -> item.recap.topSongs.map { it.cover }.filter { it.isNotBlank() }.distinct().take(4)
    }
    val others = (item as? ShareItem.OfRecap)?.recap?.let { r -> r.topSongs.take(5).map { it.cover } + r.topArtists.take(5).map { it.cover } }.orEmpty()
    val side = if (item is ShareItem.OfRecap) 360 else 760
    val covers = (lead + others).filter { it.isNotBlank() }.distinct().mapNotNull { url ->
        runCatching { loadArt(url)?.let { raster(it, side, artworkZoom(url)) } }.getOrNull()?.let { url to it }
    }.toMap()
    val light = lead.firstOrNull()?.let { runCatching { ArtworkColors.extract(it) }.getOrNull() } ?: ArtworkLight(BrandViolet, BrandPink)
    val leadImages = lead.mapNotNull { covers[it] }
    return CardAssets(covers, leadImages, leadImages.firstOrNull()?.let(::smear), light, raster(logo, 192))
}

/** Draws a card, at [scale] of its full 1080-wide size: the preview and the picture are the same drawing. */
fun renderCard(spec: CardSpec, assets: CardAssets, measurer: TextMeasurer, scale: Float = 1f): ImageBitmap {
    val height = if (spec.item is ShareItem.OfRecap) CardFormat.STORY.height else spec.format.height
    val bitmap = ImageBitmap((W * scale).toInt(), (height * scale).toInt())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(W * scale, height * scale)) {
        scale(scale, scale, pivot = Offset.Zero) {
            val pen = Pen(this, measurer, colorsFor(spec.tint, assets.light), assets, height, spec.format == CardFormat.SQUARE && spec.item !is ShareItem.OfRecap)
            when (val item = spec.item) {
                is ShareItem.OfRecap -> pen.recap(item.recap, spec.style)
                else -> pen.subject(subjectOf(item), spec)
            }
        }
    }
    return bitmap
}

/** The song or playlist on a card, in the words a card needs. */
private class Subject(val title: String, val by: String, val kicker: String, val seconds: Int, val seed: Int, val rightNow: String)

private fun subjectOf(item: ShareItem): Subject {
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
    return when (item) {
        is ShareItem.OfSong -> item.song.let { Subject(it.title, it.artist, "ON REPEAT", lengthOf(it), (it.title + it.artist).hashCode(), today) }
        is ShareItem.OfPlaylist -> item.playlist.let { p ->
            val n = p.songs.size
            Subject(p.name, "$n ${if (n == 1) "song" else "songs"}", "MY PLAYLIST", p.songs.sumOf { it.duration }, p.name.hashCode(), today)
        }
        is ShareItem.OfRecap -> Subject("", "", "", 0, 0, today)
    }
}

/** A song from a link doesn't know its length until it's playing. */
private fun lengthOf(song: Song): Int {
    if (song.duration > 0) return song.duration
    val playing = PlayerController.meta.value.currentSong?.playId == song.playId
    return if (playing) (PlayerController.progress.value.durationMs / 1000).toInt().coerceAtLeast(0) else 0
}

private fun clock(seconds: Int) = if (seconds >= 3_600) "%d:%02d:%02d".format(seconds / 3_600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)

/** Everything a card is drawn with; the layouts below just say where things go. */
private class Pen(val d: DrawScope, val m: TextMeasurer, val c: CardColors, val a: CardAssets, val h: Float, val square: Boolean) {

    fun text(
        value: String, x: Float, y: Float, width: Float, size: Int, weight: FontWeight = FontWeight.Normal, color: Color = c.ink,
        align: TextAlign = TextAlign.Start, lines: Int = 1, smallest: Int = size, tracking: Float = 0f,
    ): Float {
        fun layout(px: Int): TextLayoutResult = m.measure(
            AnnotatedString(value),
            TextStyle(color = color, fontSize = px.sp, fontWeight = weight, textAlign = align, lineHeight = (px * 1.12f).sp, letterSpacing = tracking.sp),
            overflow = TextOverflow.Ellipsis, maxLines = lines, constraints = Constraints.fixedWidth(width.toInt()), density = Density(1f),
        )
        var px = size
        var result = layout(px)
        while (result.multiParagraph.didExceedMaxLines && px > smallest) { px = maxOf(smallest, px - 6); result = layout(px) }
        d.drawText(result, topLeft = Offset(x, y))
        return y + result.size.height
    }

    fun centered(value: String, y: Float, size: Int, weight: FontWeight = FontWeight.Normal, color: Color = c.ink, lines: Int = 1, smallest: Int = size, tracking: Float = 0f) =
        text(value, 80f, y, W - 160f, size, weight, color, TextAlign.Center, lines, smallest, tracking)

    /** Fills a box with [image], cropped to fit. */
    fun image(image: ImageBitmap, x: Float, y: Float, w: Float, hh: Float, quality: FilterQuality = FilterQuality.Medium) {
        val ratio = w / hh
        val sw = if (ratio >= 1f) image.width else (image.height * ratio).toInt()
        val sh = if (ratio >= 1f) (image.width / ratio).toInt() else image.height
        d.drawImage(
            image, IntOffset((image.width - sw) / 2, (image.height - sh) / 2), IntSize(sw, sh),
            IntOffset(x.toInt(), y.toInt()), IntSize(w.toInt(), hh.toInt()), filterQuality = quality,
        )
    }

    fun rounded(x: Float, y: Float, w: Float, hh: Float, r: Float, draw: () -> Unit) {
        d.clipPath(Path().apply { addRoundRect(RoundRect(x, y, x + w, y + hh, CornerRadius(r))) }) { draw() }
    }

    /** One cover, or four as a grid. */
    fun cover(x: Float, y: Float, w: Float, hh: Float) {
        d.drawRect(c.faint, Offset(x, y), Size(w, hh))
        if (a.lead.size >= 4) a.lead.take(4).forEachIndexed { i, img -> image(img, x + w / 2f * (i % 2), y + hh / 2f * (i / 2), w / 2f, hh / 2f) }
        else a.lead.firstOrNull()?.let { image(it, x, y, w, hh) }
    }

    /** Paints the whole card. (The scope's own size is the bitmap's, which differs from the card's in a preview.) */
    fun fill(color: Color) = d.drawRect(color, Offset.Zero, Size(W, h))
    fun fill(brush: Brush) = d.drawRect(brush, Offset.Zero, Size(W, h))

    fun wash(dim: Float = 0.5f, smeared: Boolean = true) {
        fill(c.base)
        if (smeared && !c.light) a.backdrop?.let {
            val side = maxOf(W, h) + 120f
            image(it, (W - side) / 2f, (h - side) / 2f, side, side, FilterQuality.High)
            fill(Color.Black.copy(alpha = dim))
        }
        fill(Brush.radialGradient(listOf(c.one.copy(alpha = 0.7f), Color.Transparent), center = Offset(W * 0.12f, 0f), radius = h * 0.8f))
        fill(Brush.radialGradient(listOf(c.two.copy(alpha = 0.55f), Color.Transparent), center = Offset(W, h), radius = h * 0.7f))
    }

    /** The app's mark and name. */
    fun brand(y: Float, x: Float? = null, color: Color = c.ink) {
        val mark = 64f
        val wordWidth = 248f
        val left = x ?: ((W - mark - 18f - wordWidth) / 2f)
        a.logo?.let { image(it, left, y, mark, mark) }
        text("Daydreamin", left + mark + 18f, y + 6f, 400f, 42, FontWeight.Bold, color)
    }

    fun soundwave(x: Float, y: Float, w: Float, hh: Float, seed: Int, color: Color) {
        val bars = 44
        val slot = w / bars
        val random = java.util.Random(seed.toLong())
        repeat(bars) { i ->
            val shape = 0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin(i * 0.42f + seed % 7))
            val height = hh * shape * (0.45f + 0.55f * random.nextFloat())
            d.drawRoundRect(color, Offset(x + i * slot + slot * 0.2f, y + (hh - height) / 2f), Size(slot * 0.6f, height), CornerRadius(slot * 0.3f))
        }
    }

    fun subject(s: Subject, spec: CardSpec) = when (spec.style) {
        CardStyle.POSTER -> poster(s)
        CardStyle.VINYL -> vinyl(s)
        CardStyle.PLAYER -> player(s)
        CardStyle.TICKET -> ticket(s)
        CardStyle.LYRICS -> lyrics(s, spec.lyric)
        else -> aura(s)
    }

    private fun aura(s: Subject) = with(d) {
        wash(0.42f)
        val side = if (square) 540f else 780f
        val left = (W - side) / 2f
        val top = if (square) 90f else 300f
        drawCircle(Brush.radialGradient(listOf(c.one.copy(alpha = 0.55f), Color.Transparent), center = Offset(W / 2f, top + side / 2f), radius = side * 0.95f), side * 0.95f, Offset(W / 2f, top + side / 2f))
        rounded(left, top, side, side, 48f) { cover(left, top, side, side) }
        if (square) {
            val next = centered(s.title, top + side + 34f, 58, FontWeight.Bold, smallest = 38)
            centered(s.by, next + 6f, 36, color = c.soft)
            brand(h - 116f)
        } else {
            centered(s.kicker, 200f, 34, FontWeight.SemiBold, c.soft, tracking = 8f)
            val next = centered(s.title, top + side + 70f, 84, FontWeight.Bold, lines = 2, smallest = 52)
            centered(s.by, next + 14f, 46, color = c.soft)
            soundwave(170f, h - 400f, W - 340f, 110f, s.seed, c.ink.copy(alpha = 0.8f))
            brand(h - 190f)
        }
    }

    private fun poster(s: Subject) = with(d) {
        fill(c.base)
        fill(Brush.linearGradient(listOf(c.one.copy(alpha = if (c.light) 0.35f else 0.8f), c.two.copy(alpha = if (c.light) 0.25f else 0.45f)), Offset.Zero, Offset(W, h)))
        if (square) {
            cover(0f, 0f, 620f, h)
            val x = 664f
            val width = W - x - 44f
            text(s.kicker, x, 84f, width, 26, FontWeight.Bold, c.soft, tracking = 5f)
            val next = text(s.title, x, 138f, width, 76, FontWeight.Black, lines = 6, smallest = 38)
            text(s.by, x, next + 22f, width, 36, color = c.soft, lines = 2)
            brand(h - 110f, x)
        } else {
            cover(0f, 0f, W, W)
            val x = 72f
            val width = W - 144f
            text(s.kicker, x, W + 56f, width, 32, FontWeight.Bold, c.soft, tracking = 7f)
            val next = text(s.title, x, W + 112f, width, 124, FontWeight.Black, lines = 3, smallest = 60, tracking = -2f)
            text(s.by, x, next + 20f, width, 50, FontWeight.Medium, c.soft)
            brand(h - 150f, x)
            if (s.seconds > 0) text(clock(s.seconds), x, h - 140f, width, 42, FontWeight.Medium, c.soft, TextAlign.End)
        }
    }

    private fun disc(center: Offset, r: Float) = with(d) {
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), center = center + Offset(0f, 26f), radius = r * 1.12f), r * 1.12f, center + Offset(0f, 26f))
        drawCircle(Color(0xFF0B0B0F), r, center)
        var ring = r * 0.47f
        while (ring < r * 0.985f) { drawCircle(Color.White.copy(alpha = 0.055f), ring, center, style = Stroke(1.6f)); ring += 9f }
        drawCircle(
            Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.17f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.13f), Color.Transparent, Color.Transparent), center),
            r, center,
        )
        val label = r * 0.44f
        clipPath(Path().apply { addOval(Rect(center, label)) }) { cover(center.x - label, center.y - label, label * 2f, label * 2f) }
        drawCircle(Color.White.copy(alpha = 0.22f), label, center, style = Stroke(3f))
        drawCircle(c.base, r * 0.035f, center)
    }

    private fun vinyl(s: Subject) {
        wash(0.55f)
        if (square) {
            disc(Offset(W / 2f, 390f), 320f)
            val next = centered(s.title, 738f, 58, FontWeight.Bold, smallest = 38)
            centered(s.by, next + 6f, 36, color = c.soft)
            brand(h - 116f)
        } else {
            disc(Offset(W / 2f, 730f), 470f)
            val next = centered(s.title, 1270f, 84, FontWeight.Bold, lines = 2, smallest = 52)
            centered(s.by, next + 14f, 46, color = c.soft)
            brand(h - 190f)
        }
    }

    private fun controls(cx: Float, cy: Float, size: Float, color: Color) = with(d) {
        drawCircle(color, size, Offset(cx, cy))
        val knock = if (c.light) Color.White else Color.Black
        drawRoundRect(knock, Offset(cx - size * 0.3f, cy - size * 0.38f), Size(size * 0.2f, size * 0.76f), CornerRadius(4f))
        drawRoundRect(knock, Offset(cx + size * 0.1f, cy - size * 0.38f), Size(size * 0.2f, size * 0.76f), CornerRadius(4f))
        for (side in listOf(-1f, 1f)) {
            val x = cx + side * size * 2.5f
            val t = size * 0.5f
            drawPath(Path().apply { moveTo(x + side * t, cy); lineTo(x - side * t * 0.5f, cy - t); lineTo(x - side * t * 0.5f, cy + t); close() }, color)
            drawRoundRect(color, Offset(x + side * t - 4f, cy - t), Size(8f, t * 2f), CornerRadius(4f))
        }
    }

    private fun panel(x: Float, y: Float, w: Float, hh: Float) = with(d) {
        drawRoundRect(c.ink.copy(alpha = if (c.light) 0.06f else 0.1f), Offset(x, y), Size(w, hh), CornerRadius(56f))
        drawRoundRect(c.ink.copy(alpha = 0.18f), Offset(x, y), Size(w, hh), CornerRadius(56f), style = Stroke(2f))
    }

    private fun track(x: Float, y: Float, w: Float, s: Subject) = with(d) {
        val played = 0.38f
        drawRoundRect(c.faint, Offset(x, y), Size(w, 10f), CornerRadius(5f))
        drawRoundRect(c.ink, Offset(x, y), Size(w * played, 10f), CornerRadius(5f))
        drawCircle(c.ink, 14f, Offset(x + w * played, y + 5f))
        if (s.seconds > 0) {
            text(clock((s.seconds * played).toInt()), x, y + 28f, w, 30, color = c.soft)
            text(clock(s.seconds), x, y + 28f, w, 30, color = c.soft, align = TextAlign.End)
        }
    }

    private fun player(s: Subject) {
        wash(0.5f)
        if (square) {
            panel(70f, 90f, W - 140f, 760f)
            val art = 340f
            rounded(120f, 140f, art, art, 32f) { cover(120f, 140f, art, art) }
            val x = 500f
            val width = W - x - 120f
            val next = text(s.title, x, 160f, width, 56, FontWeight.Bold, lines = 3, smallest = 34)
            text(s.by, x, next + 12f, width, 34, color = c.soft, lines = 2)
            track(120f, 540f, W - 240f, s)
            controls(W / 2f, 730f, 62f, c.ink)
            brand(h - 150f)
        } else {
            panel(70f, 230f, W - 140f, 1400f)
            val art = W - 260f
            rounded(130f, 290f, art, art, 40f) { cover(130f, 290f, art, art) }
            val next = text(s.title, 130f, 1140f, art, 62, FontWeight.Bold, lines = 2, smallest = 42)
            text(s.by, 130f, next + 8f, art, 40, color = c.soft)
            track(130f, 1330f, art, s)
            controls(W / 2f, 1525f, 66f, c.ink)
            brand(h - 190f)
        }
    }

    private fun ticketShape(x: Float, y: Float, w: Float, hh: Float, tear: Float): Path {
        val body = Path().apply { addRoundRect(RoundRect(x, y, x + w, y + hh, CornerRadius(40f))) }
        val notches = Path().apply {
            addOval(Rect(Offset(x, tear), 34f))
            addOval(Rect(Offset(x + w, tear), 34f))
        }
        return Path().apply { op(body, notches, PathOperation.Difference) }
    }

    private fun barcode(x: Float, y: Float, w: Float, hh: Float, seed: Int, ink: Color) {
        val random = java.util.Random(seed.toLong())
        var at = x
        while (at < x + w) {
            val thick = 3f + random.nextInt(4) * 3f
            d.drawRect(ink, Offset(at, y), Size(minOf(thick, x + w - at), hh))
            at += thick + 3f + random.nextInt(3) * 3f
        }
    }

    private fun field(label: String, value: String, x: Float, y: Float, w: Float, ink: Color, size: Int = 36) {
        text(label, x, y, w, 22, FontWeight.Bold, ink.copy(alpha = 0.55f), tracking = 3f)
        text(value, x, y + 32f, w, size, FontWeight.SemiBold, ink)
    }

    private fun ticket(s: Subject) = with(d) {
        wash(0.6f)
        val ink = Color(0xFF18151C)
        val paper = if (c.light) Color.White else Color(0xFFF7F1E3)
        val code = "DD-" + "%06X".format(s.seed and 0xFFFFFF)
        if (square) {
            val x = 60f; val y = 70f; val w = W - 120f; val hh = 800f; val tear = y + 520f
            drawPath(ticketShape(x, y, w, hh, tear), paper)
            rounded(x + 44f, y + 44f, 400f, 400f, 24f) { cover(x + 44f, y + 44f, 400f, 400f) }
            val tx = x + 484f
            val tw = w - 484f - 44f
            text("ADMIT ONE LISTENER", tx, y + 50f, tw, 24, FontWeight.Bold, ink.copy(alpha = 0.6f), tracking = 3f)
            val next = text(s.title, tx, y + 96f, tw, 58, FontWeight.Black, ink, lines = 4, smallest = 34)
            text(s.by, tx, next + 12f, tw, 34, color = ink.copy(alpha = 0.7f), lines = 2)
            drawLine(ink.copy(alpha = 0.3f), Offset(x + 44f, tear), Offset(x + w - 44f, tear), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
            field("LENGTH", if (s.seconds > 0) clock(s.seconds) else "–", x + 44f, tear + 34f, 240f, ink)
            field("SHARED", s.rightNow, x + 290f, tear + 34f, 330f, ink)
            barcode(x + 44f, tear + 140f, w - 88f, 96f, s.seed, ink)
            brand(h - 150f)
        } else {
            val x = 90f; val y = 170f; val w = W - 180f; val hh = 1500f; val tear = y + 1140f
            drawPath(ticketShape(x, y, w, hh, tear), paper)
            val art = w - 100f
            rounded(x + 50f, y + 50f, art, art, 28f) { cover(x + 50f, y + 50f, art, art) }
            text("ADMIT ONE LISTENER", x + 50f, y + art + 74f, art, 26, FontWeight.Bold, ink.copy(alpha = 0.6f), tracking = 4f)
            val next = text(s.title, x + 50f, y + art + 112f, art, 58, FontWeight.Black, ink, smallest = 38)
            text(s.by, x + 50f, next + 4f, art, 36, color = ink.copy(alpha = 0.7f))
            drawLine(ink.copy(alpha = 0.3f), Offset(x + 50f, tear), Offset(x + w - 50f, tear), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
            field("LENGTH", if (s.seconds > 0) clock(s.seconds) else "–", x + 50f, tear + 40f, 230f, ink)
            field("SHARED", s.rightNow, x + 280f, tear + 40f, 320f, ink)
            field("NO.", code, x + 600f, tear + 40f, 240f, ink, 30)
            barcode(x + 50f, tear + 170f, art, 150f, s.seed, ink)
            brand(h - 170f)
        }
    }

    private fun lyrics(s: Subject, lines: List<String>) = with(d) {
        fill(c.base)
        fill(Brush.linearGradient(listOf(c.one.copy(alpha = if (c.light) 0.4f else 0.95f), c.two.copy(alpha = if (c.light) 0.3f else 0.7f)), Offset.Zero, Offset(W, h)))
        val x = 80f
        val width = W - 160f
        val top = if (square) 80f else 200f
        val art = if (square) 110f else 150f
        rounded(x, top, art, art, 20f) { cover(x, top, art, art) }
        val next = text(s.title, x + art + 28f, top + art * 0.12f, width - art - 28f, if (square) 38 else 44, FontWeight.Bold, smallest = 30)
        text(s.by, x + art + 28f, next + 4f, width - art - 28f, if (square) 30 else 36, color = c.soft)
        val blockTop = if (square) top + art + 60f else h * 0.32f
        text(lines.joinToString("\n"), x, blockTop, width, if (square) 70 else 112, FontWeight.Black, lines = if (square) 6 else 9, smallest = 40, tracking = -1f)
        brand(h - if (square) 116f else 190f, x)
    }

    // ---- Your Year in Music ----

    fun recap(r: Recap, style: CardStyle) = when (style) {
        CardStyle.RECAP_SONGS -> ranked("Top songs", r, r.topSongs.take(5).map { Row(it.cover, it.title, it.artist, "${it.plays}") }, round = false)
        CardStyle.RECAP_ARTISTS -> ranked("Top artists", r, r.topArtists.take(5).map { Row(it.cover, it.name, "${it.plays} ${if (it.plays == 1) "play" else "plays"}", "") }, round = true)
        CardStyle.RECAP_MONTHS -> months(r)
        CardStyle.RECAP_CLOCK -> clockCard(r)
        CardStyle.RECAP_CALENDAR -> calendar(r)
        CardStyle.RECAP_TYPE -> listener(r)
        else -> summary(r)
    }

    private fun header(r: Recap, heading: String): Float {
        wash(0.62f)
        centered(r.title.uppercase(), 130f, 30, FontWeight.Bold, c.soft, tracking = 5f)
        return centered(heading, 186f, 76, FontWeight.Black, lines = 2, smallest = 54)
    }

    private fun summary(r: Recap) = with(d) {
        wash(0.62f)
        centered(r.title, 120f, 60, FontWeight.Black, smallest = 42)
        val art = 300f
        a.lead.take(3).let { fan ->
            val order = when (fan.size) { 3 -> listOf(0 to -210f, 2 to 210f, 1 to 0f); 2 -> listOf(0 to -110f, 1 to 110f); else -> fan.indices.map { it to 0f } }
            order.forEach { (i, shift) ->
                val x = W / 2f - art / 2f + shift
                val y = 250f + if (shift == 0f) 0f else 34f
                rounded(x - 5f, y - 5f, art + 10f, art + 10f, 34f) { drawRect(c.base, Offset(x - 5f, y - 5f), Size(art + 10f, art + 10f)) }
                rounded(x, y, art, art, 30f) { image(fan[i], x, y, art, art) }
            }
        }
        val next = centered("%,d".format(r.minutes), 610f, 150, FontWeight.Black, tracking = -3f)
        centered("minutes listened", next - 6f, 40, color = c.soft)
        val listTop = 900f
        val column = (W - 200f) / 2f
        text("TOP ARTISTS", 80f, listTop, column, 28, FontWeight.Bold, c.soft, tracking = 4f)
        text("TOP SONGS", 120f + column, listTop, column, 28, FontWeight.Bold, c.soft, tracking = 4f)
        r.topArtists.take(5).forEachIndexed { i, artist -> text("${i + 1}  ${artist.name}", 80f, listTop + 54f + i * 66f, column, 38, FontWeight.SemiBold) }
        r.topSongs.take(5).forEachIndexed { i, song -> text("${i + 1}  ${song.title}", 120f + column, listTop + 54f + i * 66f, column, 38, FontWeight.SemiBold) }
        val pillY = 1340f
        drawRoundRect(c.ink.copy(alpha = 0.12f), Offset(150f, pillY), Size(W - 300f, 150f), CornerRadius(75f))
        centered(r.type.title, pillY + 36f, 62, FontWeight.Bold, smallest = 44)
        centered("${"%,d".format(r.plays)} plays  ·  ${"%,d".format(r.songs)} songs  ·  ${"%,d".format(r.artists)} artists", pillY + 200f, 38, color = c.soft)
        brand(h - 190f)
    }

    private class Row(val cover: String, val title: String, val subtitle: String, val count: String)

    private fun ranked(heading: String, r: Recap, rows: List<Row>, round: Boolean) = with(d) {
        var y = header(r, heading) + 90f
        val art = 200f
        rows.forEachIndexed { i, row ->
            text("${i + 1}", 70f, y + 60f, 80f, 64, FontWeight.Black, c.soft)
            val x = 160f
            val image = a.covers[row.cover]
            if (image != null) {
                if (round) clipPath(Path().apply { addOval(Rect(x, y, x + art, y + art)) }) { image(image, x, y, art, art) }
                else rounded(x, y, art, art, 26f) { image(image, x, y, art, art) }
            } else drawRoundRect(c.faint, Offset(x, y), Size(art, art), CornerRadius(26f))
            val tx = x + art + 34f
            val next = text(row.title, tx, y + 38f, W - tx - 70f, 50, FontWeight.Bold, smallest = 38, lines = 2)
            text(row.subtitle, tx, next + 8f, W - tx - 70f, 36, color = c.soft)
            y += art + 56f
        }
        brand(h - 170f)
    }

    private fun months(r: Recap) {
        val t = r.time ?: return
        val monthName = java.time.Month.of(t.peakMonth + 1).getDisplayName(DateTextStyle.FULL, Locale.ENGLISH)
        val top = header(r, "$monthName was your loudest month") + 60f
        d.drawBars(m, t.minutesByMonth, MONTH_LETTERS, t.peakMonth, Size(W - 140f, 900f), Offset(70f, top), c.ink.copy(alpha = 0.3f), c.ink, c.soft, 36f)
        centered("%,d minutes".format(t.minutesByMonth[t.peakMonth]), top + 960f, 72, FontWeight.Black)
        centered("in $monthName alone", top + 1050f, 40, color = c.soft)
        brand(h - 190f)
    }

    private fun clockCard(r: Recap) {
        val t = r.time ?: return
        val top = header(r, "Your day in music") + 40f
        val center = Offset(W / 2f, top + 460f)
        d.drawClock(m, t.minutesByHour, center, 410f, c.ink.copy(alpha = 0.32f), c.ink, c.soft, 32f)
        text("PEAK", 0f, center.y - 76f, W, 28, FontWeight.Bold, c.soft, TextAlign.Center, tracking = 5f)
        text(hourName(t.peakHour), 0f, center.y - 36f, W, 76, FontWeight.Black, align = TextAlign.Center)
        val below = top + 950f
        d.drawBars(m, t.minutesByWeekday, DAY_LETTERS, t.peakWeekday.value - 1, Size(W - 300f, 300f), Offset(150f, below), c.ink.copy(alpha = 0.3f), c.ink, c.soft, 30f)
        brand(h - 190f)
    }

    private fun calendar(r: Recap) {
        val t = r.time ?: return
        val year = r.year ?: return
        val top = header(r, "A year of days") + 60f
        d.drawYearGrid(m, year, t.minutesByDay, Size(W - 140f, 800f), Offset(70f, top), c.ink, c.ink.copy(alpha = 0.12f), c.soft, 30f)
        val y = top + 880f
        val third = (W - 120f) / 3f
        listOf("${t.daysListened}" to "days listened", "${t.longestStreak}" to "day streak", "${t.biggestDay?.value ?: 0}" to "best day, minutes").forEachIndexed { i, (big, small) ->
            text(big, 60f + third * i, y, third, 96, FontWeight.Black, align = TextAlign.Center)
            text(small, 60f + third * i, y + 116f, third, 32, color = c.soft, align = TextAlign.Center)
        }
        brand(h - 190f)
    }

    private fun listener(r: Recap) = with(d) {
        wash(0.62f)
        centered(r.title.uppercase(), 130f, 30, FontWeight.Bold, c.soft, tracking = 5f)
        val center = Offset(W / 2f, 660f)
        listOf(400f to 0.08f, 320f to 0.12f, 240f to 0.18f).forEach { (radius, alpha) -> drawCircle(c.ink.copy(alpha = alpha), radius, center) }
        a.lead.firstOrNull()?.let { art -> clipPath(Path().apply { addOval(Rect(center, 170f)) }) { image(art, center.x - 170f, center.y - 170f, 340f, 340f) } }
        centered("I'm", 1110f, 50, color = c.soft)
        val next = centered(r.type.title, 1176f, 104, FontWeight.Black, lines = 2, smallest = 66, tracking = -2f)
        centered(r.type.line, next + 30f, 44, color = c.soft, lines = 3)
        brand(h - 190f)
    }
}

internal fun hourName(hour: Int): String = when {
    hour == 0 -> "12am"
    hour < 12 -> "${hour}am"
    hour == 12 -> "12pm"
    else -> "${hour - 12}pm"
}
