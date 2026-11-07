package com.daydreamin.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.Playlist
import com.daydreamin.app.data.taste.Recap
import com.daydreamin.app.platform.Log
import com.daydreamin.app.platform.encodePng
import com.daydreamin.app.platform.forOffscreenDrawing
import com.daydreamin.app.platform.platformContext
import com.daydreamin.app.platform.shareImage
import com.daydreamin.app.resources.Res
import com.daydreamin.app.resources.splash_logo
import com.daydreamin.app.ui.theme.ArtworkColors
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BrandPink
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.artworkModel
import com.daydreamin.app.ui.theme.artworkZoom
import org.jetbrains.compose.resources.painterResource

private const val APP_LINK = "https://github.com/theankitverse/daydreamin-android"
// Portrait, the shape of a Story, and fine in a chat.
private const val W = 1080f
private const val H = 1920f

/** Something to share as a card. Asked for from anywhere; [ShareHost] makes the picture and sends it. */
sealed interface ShareItem {
    data class OfSong(val song: Song) : ShareItem
    data class OfPlaylist(val playlist: Playlist) : ShareItem
    data class OfRecap(val recap: Recap) : ShareItem
}

object Share {
    internal var pending by mutableStateOf<ShareItem?>(null)

    fun song(song: Song) { pending = ShareItem.OfSong(song) }
    fun playlist(playlist: Playlist) { pending = ShareItem.OfPlaylist(playlist) }
    fun recap(recap: Recap) { pending = ShareItem.OfRecap(recap) }
}

/** The link that goes with a song: its YouTube Music page, or a search for it there. */
internal fun songLink(song: Song): String =
    song.videoId?.takeIf { it.isNotBlank() }?.let { "https://music.youtube.com/watch?v=$it" }
        ?: "https://music.youtube.com/search?q=" + java.net.URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")

internal fun shareText(item: ShareItem): String = when (item) {
    is ShareItem.OfSong -> "${item.song.title} by ${item.song.artist}\n${songLink(item.song)}\n\nListening on Daydreamin: $APP_LINK"
    is ShareItem.OfPlaylist -> "My playlist “${item.playlist.name}”: ${item.playlist.songs.take(5).joinToString { it.title }}\n\nMade with Daydreamin: $APP_LINK"
    is ShareItem.OfRecap -> "${item.recap.title}: ${item.recap.minutes} minutes, and I'm ${item.recap.type.title}.\n\nMade with Daydreamin: $APP_LINK"
}

/** Goes once at the root of the app's window: turns [Share] requests into a card and shares it. */
@Composable
fun ShareHost() {
    val measurer = rememberTextMeasurer()
    val logo = painterResource(Res.drawable.splash_logo)
    val item = Share.pending
    LaunchedEffect(item) {
        if (item == null) return@LaunchedEffect
        runCatching {
            val bitmap = renderCard(item, measurer, logo)
            shareImage(bitmap.encodePng(), fileName(item), shareText(item))
        }.onFailure {
            Log.w("Share", "couldn't make the card: ${it.message}")
            Toaster.show("Couldn't make the picture")
        }
        Share.pending = null
    }
}

private fun fileName(item: ShareItem): String {
    val name = when (item) {
        is ShareItem.OfSong -> item.song.title
        is ShareItem.OfPlaylist -> item.playlist.name
        is ShareItem.OfRecap -> item.recap.title
    }
    return "Daydreamin - " + name.replace(Regex("""[\\/:*?"<>|]"""), "").take(60).trim() + ".png"
}

private suspend fun loadArt(url: String?): Painter? {
    if (url.isNullOrBlank()) return null
    val request = ImageRequest.Builder(platformContext).data(artworkModel(url)).forOffscreenDrawing().build()
    val result = SingletonImageLoader.get(platformContext).execute(request)
    return (result as? SuccessResult)?.image?.asPainter(platformContext)
}

private suspend fun renderCard(item: ShareItem, measurer: TextMeasurer, logo: Painter): ImageBitmap {
    val covers = when (item) {
        is ShareItem.OfSong -> listOf(item.song.cover.ifBlank { item.song.artworkUrl })
        is ShareItem.OfPlaylist -> item.playlist.coverUrl?.let(::listOf) ?: item.playlist.songs.map { it.cover.ifBlank { it.artworkUrl } }.filter { it.isNotBlank() }.distinct().take(4)
        is ShareItem.OfRecap -> item.recap.topSongs.map { it.cover }.filter { it.isNotBlank() }.distinct().take(4)
    }
    val art = covers.map { it to loadArt(it) }.filter { it.second != null }
    val light = covers.firstOrNull()?.let { runCatching { ArtworkColors.extract(it) }.getOrNull() } ?: ArtworkLight(BrandViolet, BrandPink)

    val bitmap = ImageBitmap(W.toInt(), H.toInt())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(W, H)) {
        drawRect(Color.Black)
        drawRect(Brush.radialGradient(listOf(light.key.copy(alpha = 0.85f), Color.Transparent), center = Offset(W * 0.15f, 0f), radius = H * 0.85f))
        drawRect(Brush.radialGradient(listOf(light.fill.copy(alpha = 0.65f), Color.Transparent), center = Offset(W, H), radius = H * 0.75f))
        when (item) {
            is ShareItem.OfSong -> {
                drawCover(art, top = 330f)
                text(measurer, item.song.title, 1190f, 76, FontWeight.Bold, Color.White, maxLines = 2)
                text(measurer, item.song.artist, 1400f, 46, FontWeight.Normal, Color.White.copy(alpha = 0.75f))
            }
            is ShareItem.OfPlaylist -> {
                drawCover(art, top = 330f)
                text(measurer, item.playlist.name, 1190f, 76, FontWeight.Bold, Color.White, maxLines = 2)
                val count = item.playlist.songs.size
                text(measurer, "$count ${if (count == 1) "song" else "songs"}", 1400f, 46, FontWeight.Normal, Color.White.copy(alpha = 0.75f))
            }
            is ShareItem.OfRecap -> drawRecap(item.recap, art, measurer)
        }
        // Made with Daydreamin, at the foot.
        translate(left = W / 2f - 215f, top = H - 190f) { with(logo) { draw(Size(84f, 84f)) } }
        drawText(
            measurer.measure(AnnotatedString("Made with Daydreamin"), TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 34.sp, fontWeight = FontWeight.SemiBold), density = Density(1f)),
            topLeft = Offset(W / 2f - 115f, H - 170f),
        )
    }
    return bitmap
}

private fun DrawScope.drawRecap(recap: Recap, art: List<Pair<String, Painter?>>, measurer: TextMeasurer) {
    text(measurer, recap.title, 170f, 66, FontWeight.Bold, Color.White, maxLines = 2)
    drawCover(art, top = 370f, side = 560f)
    text(measurer, "%,d".format(recap.minutes), 980f, 120, FontWeight.Black, Color.White)
    text(measurer, "minutes listened", 1125f, 40, FontWeight.Normal, Color.White.copy(alpha = 0.75f))
    text(measurer, recap.type.title, 1215f, 58, FontWeight.Bold, Color.White)
    recap.topArtists.firstOrNull()?.let { text(measurer, "Top artist: ${it.name}", 1320f, 42, FontWeight.Medium, Color.White.copy(alpha = 0.85f)) }
    recap.topSongs.firstOrNull()?.let { text(measurer, "Top song: ${it.title}", 1385f, 42, FontWeight.Medium, Color.White.copy(alpha = 0.85f)) }
}

private fun DrawScope.text(measurer: TextMeasurer, value: String, top: Float, size: Int, weight: FontWeight, color: Color, maxLines: Int = 1) {
    val layout = measurer.measure(
        AnnotatedString(value),
        TextStyle(color = color, fontSize = size.sp, fontWeight = weight, textAlign = TextAlign.Center, lineHeight = (size * 1.15f).sp),
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        constraints = Constraints.fixedWidth((W - 160f).toInt()),
        density = Density(1f),
    )
    drawText(layout, topLeft = Offset(80f, top))
}

/** One cover, or up to four as a grid, in a rounded square. */
private fun DrawScope.drawCover(art: List<Pair<String, Painter?>>, top: Float, side: Float = 780f) {
    val left = (W - side) / 2f
    val frame = Path().apply { addRoundRect(RoundRect(left, top, left + side, top + side, CornerRadius(44f))) }
    clipPath(frame) {
        drawRect(Color.White.copy(alpha = 0.08f), Offset(left, top), Size(side, side))
        if (art.size >= 4) {
            val half = side / 2f
            art.take(4).forEachIndexed { i, a -> drawCrop(a, left + half * (i % 2), top + half * (i / 2), half) }
        } else art.firstOrNull()?.let { drawCrop(it, left, top, side) }
    }
}

/** Fills a square with the picture, cropped from the middle, and zoomed past YouTube's letterbox bars. */
private fun DrawScope.drawCrop(art: Pair<String, Painter?>, x: Float, y: Float, side: Float) {
    val painter = art.second ?: return
    val source = painter.intrinsicSize
    if (source.width <= 0f || source.height <= 0f) return
    val scale = side / minOf(source.width, source.height) * artworkZoom(art.first)
    val w = source.width * scale
    val h = source.height * scale
    clipPath(Path().apply { addRect(androidx.compose.ui.geometry.Rect(x, y, x + side, y + side)) }) {
        translate(left = x + (side - w) / 2f, top = y + (side - h) / 2f) { with(painter) { draw(Size(w, h)) } }
    }
}
