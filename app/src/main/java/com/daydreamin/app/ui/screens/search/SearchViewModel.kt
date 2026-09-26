package com.daydreamin.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.youtube.YtPlaylist
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SearchTab { SONGS, ARTISTS, ALBUMS, PLAYLISTS }

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val playlists: List<YtPlaylist> = emptyList(),
    val playlistsLoading: Boolean = false,
    val tab: SearchTab = SearchTab.SONGS,
    val error: String? = null,
)

class SearchViewModel : ViewModel() {
    private val repo = DaydreaminApp.instance.repository
    private var searchJob: Job? = null
    private var playlistJob: Job? = null
    private var playlistsLoadedForQuery: String? = null

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        searchJob?.cancel()
        playlistJob?.cancel()
        playlistsLoadedForQuery = null
        if (query.isBlank()) {
            _state.value = _state.value.copy(songs = emptyList(), playlists = emptyList(), loading = false, error = null)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.value = _state.value.copy(loading = true, error = null)
            // iTunes + YouTube run in parallel inside smartSearch, merged into one
            // popularity-ranked, typo-tolerant list — see SearchRanking.kt.
            repo.smartSearch(query).fold(
                onSuccess = { songs ->
                    _state.value = _state.value.copy(loading = false, songs = songs)
                    // The top result is what gets tapped most — warm it so it's instant.
                    songs.take(2).forEach { YouTubeExtractorService.prefetchSong(it.artist, it.title) }
                },
                onFailure = { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "Search failed") },
            )
            if (_state.value.tab == SearchTab.PLAYLISTS) loadPlaylistsIfNeeded(query)
        }
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
            _state.value = _state.value.copy(playlistsLoading = true)
            val playlists = repo.searchPlaylists(query).getOrDefault(emptyList())
            _state.value = _state.value.copy(playlistsLoading = false, playlists = playlists)
        }
    }

    val artists: List<Pair<String, Song>>
        get() = _state.value.songs
            .distinctBy { it.artist.lowercase() }
            .map { it.artist to it }

    val albums: List<Pair<String, Song>>
        get() = _state.value.songs
            .distinctBy { it.album.lowercase() }
            .map { it.album to it }
}
