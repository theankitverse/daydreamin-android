package com.daydreamin.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.youtube.YtPlaylist
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * What a search can actually return: songs (iTunes + YouTube, merged and ranked), the artists
 * among those songs, and YouTube playlists. (The old Albums filter was dropped: its rows did
 * nothing when tapped, and YouTube results carry no real album — only the "Single" placeholder.)
 */
enum class SearchTab(val label: String) { SONGS("Songs"), ARTISTS("Artists"), PLAYLISTS("Playlists") }

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val songs: List<Song> = emptyList(),
    /** The query the current [songs] belong to — so "no results" is never shown for a query still being typed. */
    val resultsFor: String? = null,
    val playlists: List<YtPlaylist> = emptyList(),
    val playlistsLoading: Boolean = false,
    val playlistsError: String? = null,
    val tab: SearchTab = SearchTab.SONGS,
    val error: String? = null,
)

/** An artist found in the song results, with one of their songs to borrow artwork from. */
data class ArtistHit(val name: String, val sample: Song, val songCount: Int)

class SearchViewModel : ViewModel() {
    private val repo = DaydreaminApp.instance.repository
    private var searchJob: Job? = null
    private var playlistJob: Job? = null
    private var playlistsLoadedForQuery: String? = null

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    private val prefs = DaydreaminApp.instance.prefs
    val history: StateFlow<List<String>> = prefs.searchHistory.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Whether the query on screen is one you typed — a mood card or an artist row fills it in for you. */
    private var typedByUser = false

    /**
     * [fromUser] is false when the app sets the query itself (a mood, an artist row); those
     * never go into your search history.
     */
    fun onQueryChange(query: String, fromUser: Boolean = true) {
        typedByUser = fromUser
        _state.value = _state.value.copy(query = query)
        searchJob?.cancel()
        playlistJob?.cancel()
        playlistsLoadedForQuery = null
        if (query.isBlank()) {
            _state.value = _state.value.copy(songs = emptyList(), resultsFor = null, playlists = emptyList(), loading = false, error = null, playlistsError = null, playlistsLoading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // wait for a pause in typing
            _state.value = _state.value.copy(loading = true, error = null)
            // iTunes + YouTube run in parallel inside smartSearch, merged into one
            // popularity-ranked, typo-tolerant list — see SearchRanking.kt.
            repo.smartSearch(query).fold(
                onSuccess = { songs ->
                    // Both providers quietly return nothing when they can't be reached, so an empty
                    // answer while offline means "offline", not "no such song".
                    if (songs.isEmpty() && !isOnline()) {
                        _state.value = _state.value.copy(loading = false, songs = emptyList(), error = OFFLINE, resultsFor = query)
                        return@fold
                    }
                    _state.value = _state.value.copy(loading = false, songs = songs, resultsFor = query)
                    // The top result is what gets tapped most — warm it so it's instant.
                    songs.take(2).forEach { YouTubeExtractorService.prefetchSong(it.artist, it.title) }
                },
                onFailure = { e -> _state.value = _state.value.copy(loading = false, error = friendlyError(e), resultsFor = query) },
            )
            if (_state.value.tab == SearchTab.PLAYLISTS) loadPlaylistsIfNeeded(query)
        }
    }

    /**
     * You meant this search: you pressed the keyboard's search key, or played or opened something
     * it found. Only then does it join your history — never the half-typed queries in between.
     */
    fun commitSearch() {
        val q = _state.value.query
        if (typedByUser && q.isNotBlank()) viewModelScope.launch { prefs.addSearch(q) }
    }

    /** A past search, run again (and moved back to the top of the list). */
    fun searchAgain(query: String) {
        onQueryChange(query, fromUser = true)
        commitSearch()
    }

    fun removeFromHistory(query: String) { viewModelScope.launch { prefs.removeSearch(query) } }

    fun clearHistory() { viewModelScope.launch { prefs.clearSearchHistory() } }

    /** Runs the current query again (after an error). */
    fun retry() {
        val q = _state.value.query
        if (q.isNotBlank()) onQueryChange(q)
    }

    fun onTabChange(tab: SearchTab) {
        _state.value = _state.value.copy(tab = tab)
        if (tab == SearchTab.PLAYLISTS) loadPlaylistsIfNeeded(_state.value.query)
    }

    /** Playlist search is a separate network call — only fired when the user actually opens that tab, not on every keystroke, so the default Songs search stays as fast as possible. */
    private fun loadPlaylistsIfNeeded(query: String) {
        if (query.isBlank() || query == playlistsLoadedForQuery) return
        playlistsLoadedForQuery = query
        playlistJob?.cancel()
        playlistJob = viewModelScope.launch {
            _state.value = _state.value.copy(playlistsLoading = true, playlistsError = null)
            repo.searchPlaylists(query).fold(
                onSuccess = { _state.value = _state.value.copy(playlistsLoading = false, playlists = it) },
                onFailure = { e ->
                    playlistsLoadedForQuery = null // let a retry through
                    _state.value = _state.value.copy(playlistsLoading = false, playlists = emptyList(), playlistsError = friendlyError(e))
                },
            )
        }
    }

    fun retryPlaylists() = loadPlaylistsIfNeeded(_state.value.query)

    /** Artists among the song results, in the order they first appear (i.e. by relevance). */
    fun artists(songs: List<Song>): List<ArtistHit> =
        songs.groupBy { it.artist.trim().lowercase() }
            .values
            .map { group -> ArtistHit(group.first().artist.trim(), group.first(), group.size) }
}

private const val OFFLINE = "You're offline. Check your connection and try again."

private fun friendlyError(e: Throwable): String =
    if (generateSequence(e) { it.cause }.any { it is IOException } || !isOnline()) OFFLINE
    else "Search didn't work this time. Try again in a moment."

private fun isOnline(): Boolean {
    val cm = DaydreaminApp.instance.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return true
    @Suppress("DEPRECATION")
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return cm.activeNetworkInfo?.isConnected == true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
