package com.daydreamin.app.ui.theme

import androidx.compose.ui.graphics.Color

// AMOLED-first: the canvas is true black, so every glow and glass surface reads as light on
// darkness (and pixels that are off cost no power). Depth comes from glass, not grey fills.
val BgBase = Color(0xFF000000)
val BgElevated = Color(0xFF12131C)
val Surface = Color(0xFF171821)
val SurfaceVariant = Color(0xFF1E202C)
val SurfaceHigh = Color(0xFF262838)
val Divider = Color(0xFF2A2C3A)

val TextPrimary = Color(0xFFF5F5F8)
val TextSecondary = Color(0xFF9DA1B5)
val TextMuted = Color(0xFF6B6F84)

// Brand gradient — pulled from the Daydreamin logo mark
val BrandPurple = Color(0xFF8B7CF6)
val BrandViolet = Color(0xFF6D5EE8)
val BrandPink = Color(0xFFE87FC2)

val Success = Color(0xFF4ADE80)
val Warning = Color(0xFFFBBF24)
val DangerRed = Color(0xFFEF4444)

// Selectable accent palette shown on the Theme / Customize screen.
// The chosen entry becomes Material3's primary color app-wide.
data class AccentOption(val name: String, val color: Color)

val AccentPalette = listOf(
    AccentOption("Violet", Color(0xFF8B7CF6)),
    AccentOption("Purple", Color(0xFFA855F7)),
    AccentOption("Magenta", Color(0xFFE154C4)),
    AccentOption("Rose", Color(0xFFEF4488)),
    AccentOption("Orange", Color(0xFFF5943D)),
    AccentOption("Green", Color(0xFF4ADE80)),
    AccentOption("Cyan", Color(0xFF34D6E0)),
    AccentOption("Blue", Color(0xFF3B82F6)),
    AccentOption("Pink", Color(0xFFF472B6)),
    AccentOption("Slate", Color(0xFF94A3B8)),
)

fun accentByName(name: String?): Color =
    AccentPalette.firstOrNull { it.name == name }?.color ?: BrandPurple
