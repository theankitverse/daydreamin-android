package com.daydreamin.app.desktop.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The desktop app's own scale. The phone app's type and spacing are sized for a thumb and a screen
 * held close; a computer is used with a pointer at arm's length, so text sits around the system's
 * own sizes (macOS 13 pt, Windows 14 px), rows are compact, and spacing is tighter. Colours come
 * from [DColors], light or dark; the glass/glow treatments from the shared theme.
 */
object DType {
    /** The big title on a playlist, Liked songs, an artist. */
    val hero get() = TextStyle(fontWeight = FontWeight.Black, fontSize = 52.sp, lineHeight = 58.sp, letterSpacing = (-1.6).sp, color = DColors.text)
    /** A page's title (Home's greeting, Search, Library). */
    val pageTitle get() = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.7).sp, color = DColors.text)
    /** A section on a page ("Top picks for you"). */
    val section get() = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp, color = DColors.text)
    /** Panel headings (the side panel, the library list). */
    val panelTitle get() = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp, color = DColors.text)
    /** A song's or card's title. */
    val itemTitle get() = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, color = DColors.text)
    val itemTitleStrong get() = itemTitle.copy(fontWeight = FontWeight.SemiBold)
    /** The line under a title (artist, "Playlist · 24 songs"). */
    val itemSubtitle get() = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp, color = DColors.textSecondary)
    val body get() = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp, color = DColors.textSecondary)
    val small get() = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = DColors.textSecondary)
    /** Table headers and overlines. */
    val label get() = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp, color = DColors.textMuted)
    /** Times next to the timeline, durations in tables. */
    val numeric get() = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = DColors.textSecondary)
    val button get() = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp, color = DColors.text)
}

object DSpace {
    /** Between the window's panels, and around them. */
    val gap = 8.dp
    /** Inside a panel, the left/right margin of its content. */
    val pagePad = 24.dp
    val sectionGap = 32.dp
    val titleToContent = 12.dp
}

object DSize {
    val rowHeight = 54.dp
    val rowArt = 40.dp
    val playerBarHeight = 80.dp
    val topBarHeight = 56.dp
    val sidebarWide = 264.dp
    val sidebarNarrow = 72.dp
    val sidePanel = 340.dp
    val iconButton = 32.dp
    val icon = 20.dp
    val panelRadius = 10.dp
    val cardRadius = 8.dp
}

/**
 * Whether the app is drawn light right now: Settings → Appearance → Theme, following the
 * system's own light/dark setting by default. Read as state, so everything redraws when it flips.
 * The full-window lyrics and queue are always dark (the whole window is the song's colours then).
 */
object DPalette {
    /** Set by the --theme flag: light (true) or dark (false) for this run only. */
    var forced: Boolean? = null

    val light: Boolean
        get() = !com.daydreamin.app.desktop.shell.DesktopUi.expanded && (forced ?: when (com.daydreamin.app.desktop.DesktopSettings.theme) {
            com.daydreamin.app.desktop.ThemeMode.LIGHT -> true
            com.daydreamin.app.desktop.ThemeMode.DARK -> false
            com.daydreamin.app.desktop.ThemeMode.SYSTEM -> !SystemTheme.dark
        })
}

/**
 * The desktop's colours, light or dark. [ink] is what text, icons, lines and washes are made of:
 * white on the dark app, near-black on the light one — so "ink at 7%" is a hover in either.
 * The full-window lyrics and the mini player stay dark in both, like Apple Music's player.
 */
object DColors {
    private val light get() = DPalette.light
    val ink get() = if (light) Color(0xFF15151A) else Color.White
    /** On something filled with [ink] (the player's round play button): the opposite. */
    val onInk get() = if (light) Color.White else Color.Black
    val text get() = ink
    val textSecondary get() = ink.copy(alpha = if (light) 0.64f else 0.7f)
    val textMuted get() = ink.copy(alpha = if (light) 0.46f else 0.45f)
    /** Behind the panels — the window itself. */
    val window get() = if (light) Color(0xFFE9E9EE) else Color(0xFF000000)
    /** A panel's surface (sidebar, page, side panel). */
    val panel get() = if (light) Color(0xFFF7F7F9) else Color(0xFF0E0E13)
    /** The side panel, solid (it floats over the page when the window is narrow). */
    val panelSolid get() = if (light) Color(0xFFF4F4F7) else Color(0xFF0B0B10)
    val panelEdge get() = ink.copy(alpha = if (light) 0.08f else 0.05f)
    /** The glass panels' tint over the window's glow. */
    val glassTint get() = if (light) Color.White.copy(alpha = 0.62f) else Color(0xFF09090E).copy(alpha = 0.66f)
    /** A row or card under the pointer. */
    val hover get() = ink.copy(alpha = if (light) 0.05f else 0.07f)
    val pressed get() = ink.copy(alpha = if (light) 0.08f else 0.11f)
    /** The selected row. */
    val selected get() = ink.copy(alpha = if (light) 0.09f else 0.13f)
    /** Menus, dialogs and tooltips. */
    val popup get() = if (light) Color(0xFFFFFFFF) else Color(0xFF1C1C24)
    val dialog get() = if (light) Color(0xFFFFFFFF) else Color(0xFF17171E)
    val divider get() = ink.copy(alpha = if (light) 0.09f else 0.07f)
    val field get() = ink.copy(alpha = if (light) 0.06f else 0.08f)
    val fieldFocused get() = ink.copy(alpha = if (light) 0.09f else 0.12f)
    val iconIdle get() = ink.copy(alpha = if (light) 0.6f else 0.66f)
    val iconHover get() = ink
    /** A playlist's placeholder tile (no artwork yet). */
    val tileTop get() = if (light) Color(0xFFDCDCE6) else Color(0xFF2A2A3A)
    val tileBottom get() = if (light) Color(0xFFC9C9D6) else Color(0xFF15151C)
}
