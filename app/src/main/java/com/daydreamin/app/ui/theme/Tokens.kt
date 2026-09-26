package com.daydreamin.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Daydreamin's design system — every screen pulls spacing, shape, motion and glass from here
 * instead of inventing its own numbers, which is what keeps the app feeling like one product.
 */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    /** Horizontal screen margin — content, section titles and carousels all align to it. */
    val gutter = 20.dp
    val l = 24.dp
    /** Gap between Home-style content sections. */
    val section = 36.dp
    /** Gap between a section title and its content. */
    val titleToContent = 14.dp
}

object Radius {
    /** Small artwork inside list rows. */
    val thumb = 8.dp
    /** Artwork cards, tiles, chips-in-grids. */
    val card = 14.dp
    /** Large featured panels, the mini player. */
    val panel = 24.dp

    val thumbShape = RoundedCornerShape(thumb)
    val cardShape = RoundedCornerShape(card)
    val panelShape = RoundedCornerShape(panel)
    val pill = RoundedCornerShape(percent = 50)
}

/** Every motion in the app is a spring — natural settle, interruptible, no fixed-duration "tweens" for touch feedback. */
object Motion {
    /** Touch-down shrink / release: quick, with a hint of bounce on release. */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = 650f)
    /** Selection changes, state swaps. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    /** Ambient artwork-color changes should drift, never snap. */
    const val colorDriftMs = 900
    const val pressedScale = 0.965f
}

/**
 * Glass thickness. A physically believable glass surface on a dark, lit scene is: a faint
 * white fill that's a little brighter at the top (light comes from above), and a hairline
 * edge that catches the light along its top and fades out toward the bottom. Real backdrop
 * blur (Haze) is only added where there's actually moving content behind the glass — the
 * top bar, the mini player, the bottom bar — never on cards sitting on a flat background,
 * where it would cost GPU time and look identical.
 */
enum class Glass(val fillTop: Float, val fillBottom: Float, val edgeTop: Float, val edgeBottom: Float) {
    Clear(0.055f, 0.030f, 0.12f, 0.03f),
    Regular(0.085f, 0.045f, 0.16f, 0.04f),
    Frosted(0.12f, 0.07f, 0.20f, 0.05f),
}

fun Modifier.glass(shape: Shape, level: Glass = Glass.Regular, tint: Color? = null): Modifier =
    this
        .clip(shape)
        .then(if (tint != null) Modifier.background(tint, shape) else Modifier)
        .background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = level.fillTop), Color.White.copy(alpha = level.fillBottom))),
            shape,
        )
        .border(
            width = 0.8.dp,
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = level.edgeTop), Color.White.copy(alpha = level.edgeBottom))),
            shape = shape,
        )
