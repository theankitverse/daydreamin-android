package com.daydreamin.app.ui.theme

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pulls one representative color out of a piece of artwork for the ambient glows. Raw palette
 * colors are unusable as-is on an AMOLED-black UI — a near-black swatch disappears, a neon one
 * shouts — so the result is tuned into a band that always reads as a soft, lit glow.
 */
object ArtworkColors {
    private val cache = LruCache<String, Int>(160)

    fun cached(url: String?): Color? = url?.let { cache.get(it) }?.let { Color(it) }

    suspend fun extract(context: Context, url: String): Color? {
        cache.get(url)?.let { return Color(it) }
        val request = ImageRequest.Builder(context)
            .data(artworkModel(url))
            .size(96)
            .allowHardware(false) // Palette needs to read pixels
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
        val rgb = withContext(Dispatchers.Default) {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            (palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch ?: palette.dominantSwatch)?.rgb
        } ?: return null
        val tuned = tuneForGlow(rgb)
        cache.put(url, tuned)
        return Color(tuned)
    }

    private fun tuneForGlow(rgb: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        // Near-grey artwork keeps its greyness (a forced hue would be a lie) but still glows.
        hsv[1] = if (hsv[1] < 0.12f) hsv[1] else hsv[1].coerceIn(0.38f, 0.82f)
        hsv[2] = hsv[2].coerceIn(0.58f, 0.88f)
        return android.graphics.Color.HSVToColor(hsv)
    }
}

/**
 * Sharper, correctly-framed artwork URLs. YouTube hands out tiny (168×94) `sqp` thumbnails; the
 * plain `hqdefault.jpg` of the same video is 480×360 — see [artworkZoom] for its letterboxing.
 * YouTube Music's googleusercontent art is square and can be requested at any size.
 */
fun artworkModel(url: String): String {
    ytVideoId(url)?.let { return "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
    if ("googleusercontent.com" in url) return url.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")
    return url
}

/**
 * `hqdefault.jpg` is 4:3 with a 16:9 video letterboxed inside it (black bars top and bottom).
 * Cropped into a square and scaled by 4/3, the bars fall outside the frame and what's left is
 * the centre of the actual video frame — square "album art" out of a video thumbnail. Slightly
 * past 4/3 so the soft edge of the letterbox (and wider-than-16:9 videos) never peeks in.
 */
fun artworkZoom(url: String?): Float = if (url != null && ytVideoId(url) != null) 1.38f else 1f

private val ytThumb = Regex("i\\.ytimg\\.com/vi(?:_webp)?/([A-Za-z0-9_-]{6,})/")
private fun ytVideoId(url: String): String? = ytThumb.find(url)?.groupValues?.get(1)

/** The artwork's glow color, drifting smoothly whenever [url] changes; [fallback] until known. */
@Composable
fun rememberArtworkColor(url: String?, fallback: Color): Color {
    val context = LocalContext.current
    // Not keyed on url: while a new artwork's color is being worked out, keep glowing with the
    // previous one rather than dipping to the fallback and back.
    var target by remember { mutableStateOf(ArtworkColors.cached(url) ?: fallback) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) { target = fallback; return@LaunchedEffect }
        runCatching { ArtworkColors.extract(context, url) }.getOrNull()?.let { target = it }
    }
    val animated by animateColorAsState(target, tween(Motion.colorDriftMs), label = "artworkColor")
    return animated
}
