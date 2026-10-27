package com.daydreamin.app.desktop.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.daydreamin.app.desktop.Backdrop
import com.daydreamin.app.desktop.DesktopSettings
import com.daydreamin.app.ui.components.ArtworkBackdrop
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BrandPink
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.glass
import com.daydreamin.app.ui.theme.rememberArtworkLight
import kotlinx.coroutines.delay
import kotlin.random.Random

/** With nothing playing, the window glows with the brand's own colours. */
private val BrandLight = ArtworkLight(BrandViolet, BrandPink)

/**
 * What the glass panels sit on: the playing song's artwork as soft light filling the window — the
 * phone app's backdrop, at window size. Drawn once and left alone (no live blur: the cover is
 * decoded at a few pixels and upscaled, which is already a soft wash), so it costs nothing while
 * the window is still. [vivid]: the full-window lyrics, where the colours come up bright, as in
 * Apple Music. Settings can make it the brand's colours or plain dark instead.
 */
@Composable
fun WindowBackdrop(modifier: Modifier = Modifier, vivid: Boolean = false) {
    val shared = LocalShared.current
    val url by remember { derivedStateOf { shared.meta.value.currentSong?.let { it.cover.ifBlank { it.artworkUrl } } } }
    val style = if (vivid) Backdrop.ARTWORK else DesktopSettings.backdrop
    val light = rememberArtworkLight(url.takeIf { style == Backdrop.ARTWORK }, BrandLight)
    // Into and out of the lyrics, the colours swell and settle rather than switching.
    val bright by animateFloatAsState(if (vivid) 1f else 0f, tween(500), label = "backdrop")
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (style == Backdrop.ARTWORK) ArtworkBackdrop(url, Modifier.fillMaxSize().alpha(0.55f + 0.45f * bright), blur = 0.dp, tiny = true)
        if (style != Backdrop.PLAIN) Box(
            Modifier.fillMaxSize().drawBehind {
                // Key light from the top left, fill from the bottom right, then darkened so text stays crisp.
                drawRect(Brush.radialGradient(listOf(light.key.copy(alpha = 0.38f + 0.2f * bright), Color.Transparent), center = Offset(size.width * 0.12f, 0f), radius = size.maxDimension * 0.75f))
                drawRect(Brush.radialGradient(listOf(light.fill.copy(alpha = 0.26f + 0.2f * bright), Color.Transparent), center = Offset(size.width, size.height), radius = size.maxDimension * 0.7f))
                val dim = 1f - 0.55f * bright
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f * dim), Color.Black.copy(alpha = 0.55f * dim), Color.Black.copy(alpha = 0.72f * dim + 0.1f * bright))))
            },
        ) else Box(Modifier.fillMaxSize().background(Color(0xFF07070A)))
    }
}

/** A dark see-through pane with the shared glass edge and sheen — the desktop's panels. */
fun Modifier.desktopGlass(shape: Shape, tint: Color = Color(0xFF09090E).copy(alpha = 0.66f), level: Glass = Glass.Clear): Modifier =
    glass(shape, level, tint = tint)

/**
 * The "now playing" bars for rows and tiles. The phone's bars animate every frame; on a desktop
 * that keeps the whole window redrawing at 60 fps for as long as music plays. These step about
 * nine times a second instead — still lively, a fraction of the work.
 */
@Composable
fun PlayingBars(color: Color, modifier: Modifier = Modifier, playing: Boolean = true) {
    var heights by remember { mutableStateOf(floatArrayOf(0.55f, 0.9f, 0.4f)) }
    LaunchedEffect(playing) {
        if (!playing) { heights = floatArrayOf(0.55f, 0.9f, 0.4f); return@LaunchedEffect }
        while (true) {
            heights = FloatArray(3) { 0.2f + Random.nextFloat() * 0.8f }
            delay(110)
        }
    }
    Canvas(modifier.size(14.dp)) {
        val barWidth = size.width / 5
        heights.forEachIndexed { i, h ->
            val x = i * barWidth * 2 + barWidth / 2
            drawLine(
                color = if (playing) color else color.copy(alpha = color.alpha * 0.7f),
                start = Offset(x, size.height),
                end = Offset(x, size.height - size.height * h),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
