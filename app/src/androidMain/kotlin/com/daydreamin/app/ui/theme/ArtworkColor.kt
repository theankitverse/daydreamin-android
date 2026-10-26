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
 * Light cast by a piece of artwork: a [key] light (its most vivid color) and a [fill] light (a
 * second, distinct color from the same art). Two tones read as real light from a real object;
 * a single hue reads as a colored gradient.
 */
data class ArtworkLight(val key: Color, val fill: Color)

/**
 * Pulls lighting colors out of artwork for the ambient glows. Raw palette colors are unusable
 * as-is on an AMOLED-black UI — a near-black swatch disappears, a neon one shouts — so both are
 * tuned into a band that always reads as soft light.
 */
object ArtworkColors {
    private val cache = LruCache<String, LongArray>(160)

    fun cached(url: String?): ArtworkLight? = url?.let { cache.get(it) }?.toLight()

    suspend fun extract(context: Context, url: String): ArtworkLight? {
        cache.get(url)?.let { return it.toLight() }
        val request = ImageRequest.Builder(context)
            .data(artworkModel(url))
            .size(96)
            .allowHardware(false) // Palette needs to read pixels
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
        val pair = withContext(Dispatchers.Default) {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val key = (palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch ?: palette.dominantSwatch)?.rgb ?: return@withContext null
            // Fill: the first other swatch whose hue is genuinely different; otherwise the key
            // light rotated a little around the wheel, so there's always some depth.
            val fill = listOfNotNull(palette.darkVibrantSwatch, palette.mutedSwatch, palette.lightMutedSwatch, palette.dominantSwatch, palette.lightVibrantSwatch)
                .map { it.rgb }
                .firstOrNull { hueDistance(it, key) > 28f }
                ?: rotateHue(key, 32f)
            longArrayOf(tune(key, 0.58f, 0.88f).toLong(), tune(fill, 0.46f, 0.74f).toLong())
        } ?: return null
        cache.put(url, pair)
        return pair.toLight()
    }

    private fun LongArray.toLight() = ArtworkLight(Color(this[0].toInt()), Color(this[1].toInt()))

    private fun tune(rgb: Int, minValue: Float, maxValue: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        // Near-grey artwork keeps its greyness (a forced hue would be a lie) but still glows.
        hsv[1] = if (hsv[1] < 0.12f) hsv[1] else hsv[1].coerceIn(0.38f, 0.82f)
        hsv[2] = hsv[2].coerceIn(minValue, maxValue)
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun hue(rgb: Int) = FloatArray(3).also { android.graphics.Color.colorToHSV(rgb, it) }

    private fun hueDistance(a: Int, b: Int): Float {
        val ha = hue(a); val hb = hue(b)
        if (ha[1] < 0.12f || hb[1] < 0.12f) return 0f // greys have no meaningful hue
        val d = kotlin.math.abs(ha[0] - hb[0])
        return minOf(d, 360f - d)
    }

    private fun rotateHue(rgb: Int, degrees: Float): Int {
        val hsv = hue(rgb)
        hsv[0] = (hsv[0] + degrees) % 360f
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

/** The artwork's light, drifting smoothly whenever [url] changes; [fallback] until known. */
@Composable
fun rememberArtworkLight(url: String?, fallback: ArtworkLight): ArtworkLight {
    val context = LocalContext.current
    // Not keyed on url: while a new artwork's colors are being worked out, keep glowing with the
    // previous ones rather than dipping to the fallback and back.
    var target by remember { mutableStateOf(ArtworkColors.cached(url) ?: fallback) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) { target = fallback; return@LaunchedEffect }
        runCatching { ArtworkColors.extract(context, url) }.getOrNull()?.let { target = it }
    }
    val key by animateColorAsState(target.key, tween(Motion.colorDriftMs), label = "keyLight")
    val fill by animateColorAsState(target.fill, tween(Motion.colorDriftMs), label = "fillLight")
    return ArtworkLight(key, fill)
}

/** Just the key light — for small surfaces (the mini player) where one tone is enough. */
@Composable
fun rememberArtworkColor(url: String?, fallback: Color): Color =
    rememberArtworkLight(url, ArtworkLight(fallback, fallback)).key
