package com.daydreamin.app.ui.screens.intro

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow

/** Same color as the system launch screen (res/values/colors.xml splash_background), so the handoff is invisible. */
private val LaunchBackground = Color(0xFF0A0A12)
private val Violet = Color(0xFF9B6BFF)
private val Pink = Color(0xFFF09AD8)
private const val WORD = "DAYDREAMIN"

private const val LETTER_STAGGER_MS = 38L
private const val LETTER_MS = 500
/** The wordmark has fully arrived by here — the earliest the intro may leave. */
private const val INTRO_MIN_MS = LETTER_STAGGER_MS * (WORD.length - 1) + LETTER_MS

private val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * The launch icon as the system drew it, and where. Captured as one value, in one state write,
 * so the bitmap and its position are always observed together — never a frame where one has
 * arrived and the other hasn't (that split is what used to show the logo and the wordmark
 * arriving out of sync, or the wordmark skipping straight to its end state).
 */
data class LaunchIcon(val bitmap: ImageBitmap, val bounds: IntRect)

/**
 * The launch screen, continued. The system launch screen can only show a still icon, so at the
 * moment it hands over, this draws the very same icon in the very same place ([icon]) and brings
 * the wordmark in beneath it: letters rise out of a soft blur one after another in a
 * violet-to-pink ramp, and a single glint of light passes across the word.
 *
 * It leaves once the wordmark has arrived *and* Home's songs are ready ([isReady]) — or after
 * [maxWaitMs] from process start regardless, so a slow network never traps you here — by fading
 * away as the logo drifts forward, revealing Home (already composed underneath).
 */
@Composable
fun LaunchIntro(
    icon: LaunchIcon?,
    isReady: () -> Boolean,
    processStartAtMs: Long,
    maxWaitMs: Long,
    onFinished: () -> Unit,
) {
    val letters = remember { List(WORD.length) { Animatable(0f) } }
    val glint = remember { Animatable(-0.3f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Start on the handoff itself: before it, the system launch screen still covers all this.
        // (Fallback in case the system screen never reports — the intro still plays.) Gated on the
        // single [icon] value so the logo and the wordmark always start on the same frame.
        withTimeoutOrNull(700) { snapshotFlow { icon }.first { it != null } }
        val start = SystemClock.elapsedRealtime()
        coroutineScope {
            letters.forEachIndexed { i, a ->
                launch {
                    delay(LETTER_STAGGER_MS * i)
                    a.animateTo(1f, tween(LETTER_MS, easing = EaseOutQuint))
                }
            }
            launch {
                delay(360)
                glint.animateTo(1.3f, tween(800, easing = LinearEasing))
            }
            // Leave when the word is in and Home is ready (or the cap passes).
            launch {
                while (true) {
                    val shownFor = SystemClock.elapsedRealtime() - start
                    val sinceProcess = SystemClock.elapsedRealtime() - processStartAtMs
                    if (shownFor >= INTRO_MIN_MS && (isReady() || sinceProcess >= maxWaitMs)) break
                    delay(32)
                }
                exit.animateTo(1f, tween(380, easing = EaseOutQuint))
                onFinished()
            }
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(LaunchBackground),
    ) {
        // Where the launch screen had the icon; a centered 240dp box if it didn't tell us.
        val fallbackPx = with(density) { 240.dp.roundToPx() }
        val bounds = icon?.bounds ?: IntRect(
            left = (constraints.maxWidth - fallbackPx) / 2,
            top = (constraints.maxHeight - fallbackPx) / 2,
            right = (constraints.maxWidth + fallbackPx) / 2,
            bottom = (constraints.maxHeight + fallbackPx) / 2,
        )
        if (icon != null) {
            Image(
                bitmap = icon.bitmap,
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(bounds.left, bounds.top) }
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .graphicsLayer {
                        // Drifts gently toward you as it leaves.
                        val s = 1f + 0.06f * exit.value
                        scaleX = s; scaleY = s
                    },
            )
        }

        // The wordmark sits just under the icon's visible artwork (which fills roughly the middle
        // two-thirds of the icon's box).
        val wordTop = bounds.top + (bounds.height * 0.80f).toInt()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, wordTop) }
                .graphicsLayer {
                    translationY = -8.dp.toPx() * exit.value
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // One glint of light sweeping across the letters (only where there's ink).
                    val x = size.width * glint.value
                    val w = size.width * 0.22f
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.85f),
                            1f to Color.Transparent,
                            start = Offset(x - w, 0f),
                            end = Offset(x + w, size.height),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        ) {
            WORD.forEachIndexed { i, ch ->
                val t = letters[i]
                Text(
                    ch.toString(),
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = lerp(Violet, Pink, i / (WORD.length - 1f)),
                    ),
                    modifier = Modifier.graphicsLayer {
                        val p = t.value
                        alpha = p
                        translationY = (1f - p) * 14.dp.toPx()
                        val s = 0.92f + 0.08f * p
                        scaleX = s; scaleY = s
                        // Letters come into focus, not just into view (blur needs Android 12+).
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val r = (1f - p) * 10.dp.toPx()
                            renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                        }
                    },
                )
            }
        }
    }
}
