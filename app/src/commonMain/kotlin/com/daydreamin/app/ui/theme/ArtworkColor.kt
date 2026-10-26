package com.daydreamin.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

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
    private val cache = object : LinkedHashMap<String, LongArray>(160, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LongArray>?) = size > 160
    }

    fun cached(url: String?): ArtworkLight? = url?.let { synchronized(cache) { cache[it] } }?.toLight()

    suspend fun extract(url: String): ArtworkLight? {
        cached(url)?.let { return it }
        val swatches = artworkSwatches(artworkModel(url)) ?: return null
        val key = swatches.key
        // Fill: the first other swatch whose hue is genuinely different; otherwise the key
        // light rotated a little around the wheel, so there's always some depth.
        val fill = swatches.fillCandidates.firstOrNull { hueDistance(it, key) > 28f } ?: rotateHue(key, 32f)
        val pair = longArrayOf(tune(key, 0.58f, 0.88f).toLong(), tune(fill, 0.46f, 0.74f).toLong())
        synchronized(cache) { cache[url] = pair }
        return pair.toLight()
    }

    private fun LongArray.toLight() = ArtworkLight(Color(this[0].toInt()), Color(this[1].toInt()))

    private fun tune(rgb: Int, minValue: Float, maxValue: Float): Int {
        val hsv = rgbToHsv(rgb)
        // Near-grey artwork keeps its greyness (a forced hue would be a lie) but still glows.
        hsv[1] = if (hsv[1] < 0.12f) hsv[1] else hsv[1].coerceIn(0.38f, 0.82f)
        hsv[2] = hsv[2].coerceIn(minValue, maxValue)
        return hsvToRgb(hsv)
    }

    private fun hue(rgb: Int) = rgbToHsv(rgb)

    private fun hueDistance(a: Int, b: Int): Float {
        val ha = hue(a); val hb = hue(b)
        if (ha[1] < 0.12f || hb[1] < 0.12f) return 0f // greys have no meaningful hue
        val d = kotlin.math.abs(ha[0] - hb[0])
        return minOf(d, 360f - d)
    }

    private fun rotateHue(rgb: Int, degrees: Float): Int {
        val hsv = hue(rgb)
        hsv[0] = (hsv[0] + degrees) % 360f
        return hsvToRgb(hsv)
    }
}

/**
 * An artwork's palette as ARGB ints: its most vivid colour ([key]), then other distinct swatches
 * in order of preference for the fill light. Read per platform (Android: Palette).
 */
class ArtworkSwatches(val key: Int, val fillCandidates: List<Int>)

/** Loads [model] small and reads its swatches; null if it can't be loaded. */
expect suspend fun artworkSwatches(model: String): ArtworkSwatches?

/** ARGB → [hue 0–360, saturation 0–1, value 0–1], as android.graphics.Color.colorToHSV. */
internal fun rgbToHsv(argb: Int): FloatArray {
    val r = (argb shr 16 and 0xFF) / 255f; val g = (argb shr 8 and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return floatArrayOf(h, if (max == 0f) 0f else d / max, max)
}

/** [hue, saturation, value] → opaque ARGB, as android.graphics.Color.HSVToColor. */
internal fun hsvToRgb(hsv: FloatArray): Int {
    val h = ((hsv[0] % 360f) + 360f) % 360f; val s = hsv[1].coerceIn(0f, 1f); val v = hsv[2].coerceIn(0f, 1f)
    val c = v * s; val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1)); val m = v - c
    val (r, g, b) = when ((h / 60f).toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
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
    // Not keyed on url: while a new artwork's colors are being worked out, keep glowing with the
    // previous ones rather than dipping to the fallback and back.
    var target by remember { mutableStateOf(ArtworkColors.cached(url) ?: fallback) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) { target = fallback; return@LaunchedEffect }
        runCatching { ArtworkColors.extract(url) }.getOrNull()?.let { target = it }
    }
    val key by animateColorAsState(target.key, tween(Motion.colorDriftMs), label = "keyLight")
    val fill by animateColorAsState(target.fill, tween(Motion.colorDriftMs), label = "fillLight")
    return ArtworkLight(key, fill)
}

/** Just the key light — for small surfaces (the mini player) where one tone is enough. */
@Composable
fun rememberArtworkColor(url: String?, fallback: Color): Color =
    rememberArtworkLight(url, ArtworkLight(fallback, fallback)).key
