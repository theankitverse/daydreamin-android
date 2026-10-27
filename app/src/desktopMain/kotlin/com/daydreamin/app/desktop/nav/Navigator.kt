package com.daydreamin.app.desktop.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.daydreamin.app.data.model.Song

/** The places in the desktop app. */
sealed interface Route {
    data object Home : Route
    data class Search(val query: String = "") : Route
    data object Library : Route
    data object Liked : Route
    data object Downloads : Route
    data object History : Route
    data class Playlist(val id: String) : Route
    /** A list that came from somewhere else — a mood, a Home shelf's "Show all", an artist's songs. */
    data class Collection(val title: String, val subtitle: String?, val songs: List<Song>, val kind: String = "Collection") : Route
    /** A mood from Home: its songs are looked up when it opens. */
    data class Mood(val title: String, val query: String) : Route
    /** An artist's songs, looked up when it opens. */
    data class Artist(val name: String) : Route
    /** A YouTube playlist from Search: its tracks are loaded when it opens. */
    data class YtPlaylist(val url: String, val title: String, val author: String, val thumbnail: String) : Route
    data object Profile : Route
    data object Stats : Route
}

/**
 * Browser-style history: every page you open goes on the back stack, Back and Forward move along
 * it (the toolbar arrows, Cmd/Ctrl+[ and ], the mouse's side buttons), and opening something new
 * clears Forward.
 */
class Navigator(start: Route = Route.Home) {
    var current by mutableStateOf(start)
        private set
    private val back = mutableStateListOf<Route>()
    private val forward = mutableStateListOf<Route>()

    val canGoBack: Boolean get() = back.isNotEmpty()
    val canGoForward: Boolean get() = forward.isNotEmpty()

    fun go(route: Route) {
        if (route == current) return
        back.add(current)
        if (back.size > 50) back.removeAt(0)
        forward.clear()
        current = route
    }

    /** Replaces the current page without adding to history (typing in the search box). */
    fun replace(route: Route) {
        current = route
    }

    fun goBack(): Boolean {
        val previous = back.removeLastOrNull() ?: return false
        forward.add(current)
        current = previous
        return true
    }

    fun goForward(): Boolean {
        val next = forward.removeLastOrNull() ?: return false
        back.add(current)
        current = next
        return true
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("no navigator") }
