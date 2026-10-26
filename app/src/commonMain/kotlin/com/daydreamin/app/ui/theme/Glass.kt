package com.daydreamin.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * The one "liquid glass" recipe used everywhere in the app — a dark frosted tint over
 * whatever's blurred behind it, tuned to read against [BgBase] instead of white/light UIs
 * (which is what most off-the-shelf Haze materials assume).
 */
/**
 * What glass becomes where Haze can't blur (below Android 12L): a thin black tint alone lets the
 * content behind show straight through — titles read through the mini player and tab bar — so
 * without the blur the glass turns nearly solid instead.
 */
val GlassFallback = HazeTint(BgBase.copy(alpha = 0.94f))

@Composable
fun daydreamGlassStyle(tintAlpha: Float = 0.45f, blurRadiusDp: Int = 24): HazeStyle = HazeStyle(
    backgroundColor = BgBase,
    tints = listOf(HazeTint(Color.Black.copy(alpha = tintAlpha))),
    blurRadius = blurRadiusDp.dp,
    noiseFactor = 0.08f,
    fallbackTint = GlassFallback,
)
