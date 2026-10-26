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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Same color as the system launch screen (res/values/colors.xml splash_background), so the handoff is invisible. */
private val LaunchBackground = Color(0xFF0A0A12)
private val Violet = Color(0xFF9B6BFF)
private val Pink = Color(0xFFF09AD8)
private const val WORD = "DAYDREAMIN"

private const val LETTER_STAGGER_MS = 38L
private const val LETTER_MS = 500
/** The last letter has landed. */
private const val LETTERS_MS = LETTER_STAGGER_MS * (WORD.length - 1) + LETTER_MS
/** The earliest the intro may leave: the whole word, readable, with the glint across it. */
private const val HOLD_MS = 1_050L
/** On a slow connection, stop waiting for Home's songs here — Home shows its own placeholders. */
private const val MAX_VISIBLE_MS = 1_800L
/** Launches with no system splash to hand over (a notification, a link) never report one gone. */
private const val SPLASH_HANDOFF_TIMEOUT_MS = 250L

private val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * The launch icon as the system drew it, and where. Captured as one value, in one state write,
 * so the bitmap and its position are always observed together.
 */
data class LaunchIcon(val bitmap: ImageBitmap, val bounds: IntRect)

/**
 * The launch screen, continued. The system launch screen can only show a still icon, so at the
 * moment it hands over, this draws the very same icon in the very same place ([icon]) and brings
 * the wordmark in beneath it: letters rise out of a soft blur one after another in a
 * violet-to-pink ramp, and a single glint of light passes across the word.
 *
 * Timing is measured from the moment it's actually visible ([splashGone]), not from when it was
 * composed. Composition happens while the system's own splash still covers the screen — on a
 * real phone that can be most of a second — and a clock started then plays the animation where
 * nobody can see it. Home isn't built until the letters have landed ([onMountContent]): building
 * it underneath from the first frame made those first frames 100–200ms each and held the system
 * splash on screen longer.
 */
@Composable
fun LaunchIntro(
    icon: LaunchIcon?,
    splashGone: () -> Boolean,
    isReady: () -> Boolean,
    onMountContent: () -> Unit,
    onFinished: () -> Unit,
) {
    val letters = remember { List(WORD.length) { Animatable(0f) } }
    val glint = remember { Animatable(-0.3f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Our first frame has to be drawn before the system will let its splash go.
        withFrameNanos { }
        withTimeoutOrNull(SPLASH_HANDOFF_TIMEOUT_MS) { snapshotFlow { splashGone() }.first { it } }
        // And one more, so the first frame anyone can see is the one the animation starts on.
        withFrameNanos { }
        val start = SystemClock.elapsedRealtime()
        coroutineScope {
            letters.forEachIndexed { i, a ->
                launch {
                    delay(LETTER_STAGGER_MS * i)
                    a.animateTo(1f, tween(LETTER_MS, easing = EaseOutQuint))
                }
            }
            launch {
                delay(300)
                glint.animateTo(1.3f, tween(750, easing = LinearEasing))
            }
            launch {
                delay(LETTERS_MS)
                onMountContent()
                // Two frames: the one that composes Home, and the one that draws it.
                withFrameNanos { }
                withFrameNanos { }
                while (true) {
                    val visibleFor = SystemClock.elapsedRealtime() - start
                    if (visibleFor >= HOLD_MS && (isReady() || visibleFor >= MAX_VISIBLE_MS)) break
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
        // Where the launch screen had the icon; the platform's default spot if it didn't tell us.
        val fallbackPx = with(density) { SPLASH_ICON_DP.dp.roundToPx() }
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

/** The system splash's icon box on Android 12+ (measured: 504px at 2.625x). */
const val SPLASH_ICON_DP = 192
