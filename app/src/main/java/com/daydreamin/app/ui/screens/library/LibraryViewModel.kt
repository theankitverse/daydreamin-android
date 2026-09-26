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
import java.util.UUID

enum class LibraryTab { PLAYLISTS, ARTISTS, ALBUMS, LIKED }

class LibraryViewModel : ViewModel() {
    private val prefs = DaydreaminApp.instance.prefs

    val likedSongs: StateFlow<List<Song>> = prefs.likedSongs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists: StateFlow<List<Playlist>> = prefs.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _tab = MutableStateFlow(LibraryTab.LIKED)
    val tab: StateFlow<LibraryTab> = _tab
    fun onTabChange(tab: LibraryTab) { _tab.value = tab }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val updated = playlists.value + Playlist(id = UUID.randomUUID().toString(), name = name.trim())
            prefs.savePlaylists(updated)
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { prefs.savePlaylists(playlists.value.filterNot { it.id == id }) }
    }

    fun addToPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            val updated = playlists.value.map {
                if (it.id == playlistId && it.songs.none { s -> s.playId == song.playId }) it.copy(songs = it.songs + song) else it
            }
            prefs.savePlaylists(updated)
        }
    }
}
