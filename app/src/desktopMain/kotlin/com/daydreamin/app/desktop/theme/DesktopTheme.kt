package com.daydreamin.app.desktop.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.ui.theme.TextMuted
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary

/**
 * The desktop app's own scale. The phone app's type and spacing are sized for a thumb and a screen
 * held close; a computer is used with a pointer at arm's length, so text sits around the system's
 * own sizes (macOS 13 pt, Windows 14 px), rows are compact, and spacing is tighter. Colours and
 * the glass/glow treatments come from the shared theme.
 */
object DType {
    /** The big title on a playlist, Liked songs, an artist. */
    val hero = TextStyle(fontWeight = FontWeight.Black, fontSize = 52.sp, lineHeight = 58.sp, letterSpacing = (-1.6).sp, color = TextPrimary)
    /** A page's title (Home's greeting, Search, Library). */
    val pageTitle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.7).sp, color = TextPrimary)
    /** A section on a page ("Top picks for you"). */
    val section = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp, color = TextPrimary)
    /** Panel headings (the side panel, the library list). */
    val panelTitle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp, color = TextPrimary)
    /** A song's or card's title. */
    val itemTitle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, color = TextPrimary)
    val itemTitleStrong = itemTitle.copy(fontWeight = FontWeight.SemiBold)
    /** The line under a title (artist, "Playlist · 24 songs"). */
    val itemSubtitle = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp, color = TextSecondary)
    val body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp, color = TextSecondary)
    val small = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = TextSecondary)
    /** Table headers and overlines. */
    val label = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp, color = TextMuted)
    /** Times next to the timeline, durations in tables. */
    val numeric = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = TextSecondary)
    val button = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp, color = TextPrimary)
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

object DColors {
    /** Behind the panels — the window itself. */
    val window = Color(0xFF000000)
    /** A panel's surface (sidebar, page, side panel). */
    val panel = Color(0xFF0E0E13)
    val panelEdge = Color.White.copy(alpha = 0.05f)
    /** A row or card under the pointer. */
    val hover = Color.White.copy(alpha = 0.07f)
    val pressed = Color.White.copy(alpha = 0.11f)
    /** The selected row. */
    val selected = Color.White.copy(alpha = 0.13f)
    /** Menus and tooltips. */
    val popup = Color(0xFF1C1C24)
    val divider = Color.White.copy(alpha = 0.07f)
    val field = Color.White.copy(alpha = 0.08f)
    val fieldFocused = Color.White.copy(alpha = 0.12f)
    val iconIdle = Color.White.copy(alpha = 0.66f)
    val iconHover = Color.White
}
