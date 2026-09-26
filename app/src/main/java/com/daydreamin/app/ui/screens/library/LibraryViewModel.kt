package com.daydreamin.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The three collections the app actually keeps. (The old Artists/Albums tabs were just the liked
 * list regrouped — and with YouTube-sourced songs, "album" is almost always a placeholder and
 * "artist" often a channel name — so they were dropped rather than dressed up.)
 */
enum class LibraryTab(val label: String) { LIKED("Liked"), RECENT("Recent"), PLAYLISTS("Playlists") }

class LibraryViewModel : ViewModel() {
    private val prefs = DaydreaminApp.instance.prefs

    val likedSongs: StateFlow<List<Song>?> = prefs.likedSongs.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val history: StateFlow<List<Song>?> = prefs.history.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val playlists: StateFlow<List<Playlist>?> = prefs.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _tab = MutableStateFlow(LibraryTab.LIKED)
    val tab: StateFlow<LibraryTab> = _tab
    fun onTabChange(tab: LibraryTab) { _tab.value = tab }

    /** The playlist being looked at, if any (the detail view replaces the tab content). */
    private val _openPlaylistId = MutableStateFlow<String?>(null)
    val openPlaylistId: StateFlow<String?> = _openPlaylistId
    fun openPlaylist(id: String?) { _openPlaylistId.value = id }

    fun clearHistory() {
        viewModelScope.launch { prefs.clearHistory() }
    }
}
