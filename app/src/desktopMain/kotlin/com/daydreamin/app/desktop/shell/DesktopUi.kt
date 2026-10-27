package com.daydreamin.app.desktop.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.daydreamin.app.desktop.nav.Navigator

enum class PanelTab(val label: String) { NOW_PLAYING("Now playing"), QUEUE("Queue") }

/** What the full-window Now playing shows beside the artwork. */
enum class FullView(val label: String) { LYRICS("Lyrics"), QUEUE("Queue") }

/**
 * The desktop window's own state: the side panel (open, and which view), and requests from
 * keyboard shortcuts and menus. The panel's open/closed choice and its view are remembered.
 */
object DesktopUi {
    private val settings = java.util.prefs.Preferences.userRoot().node("daydreamin")

    /** Your choice: the panel beside the page, when the window is wide enough for it. */
    var panelOpen by mutableStateOf(settings.getBoolean("panelOpen", true))
        private set
    var panelTab by mutableStateOf(runCatching { PanelTab.valueOf(settings.get("panelTab", PanelTab.NOW_PLAYING.name)) }.getOrDefault(PanelTab.NOW_PLAYING))
        private set
    /** On a window too narrow for the panel: shown floating over the page instead, until closed. */
    var overlayOpen by mutableStateOf(false)
        private set

    /** Set by the window from its width: whether the panel fits beside the page. */
    var panelFits by mutableStateOf(true)

    /** Bumped to move the keyboard to the search box (Cmd/Ctrl+K, Cmd/Ctrl+F). */
    var focusSearch by mutableIntStateOf(0)
        private set

    var navigator: Navigator? = null

    /** Now playing filling the window: big artwork beside the lyrics (or the queue). */
    var expanded by mutableStateOf(false)
    var fullView by mutableStateOf(FullView.LYRICS)

    /** The small always-on-top player is showing (in place of the window). */
    var miniPlayer by mutableStateOf(false)

    /** The lyrics, full window; asked for again, back to the app. */
    fun toggleLyrics() {
        if (expanded && fullView == FullView.LYRICS) expanded = false else { fullView = FullView.LYRICS; expanded = true }
    }

    /** Shows [tab]; showing the one already open closes the panel. */
    fun toggle(tab: PanelTab) {
        val showing = if (panelFits) panelOpen else overlayOpen
        if (showing && panelTab == tab) close() else open(tab)
    }

    fun open(tab: PanelTab) {
        panelTab = tab
        settings.put("panelTab", tab.name)
        if (panelFits) setOpen(true) else overlayOpen = true
    }

    fun close() {
        if (panelFits) setOpen(false) else overlayOpen = false
    }

    /** Whether [tab] is what the panel shows right now. */
    fun showing(tab: PanelTab) = panelTab == tab && (if (panelFits) panelOpen else overlayOpen)

    fun requestSearchFocus() { focusSearch++ }

    private fun setOpen(open: Boolean) {
        panelOpen = open
        settings.putBoolean("panelOpen", open)
    }
}
