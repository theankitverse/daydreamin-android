package com.daydreamin.app.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NowPlayingViewModel : ViewModel() {
    private val prefs = DaydreaminApp.instance.prefs

    val likedIds: StateFlow<Set<String>> = prefs.likedIds.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptySet())
    val nowPlayingStyle: StateFlow<String> = prefs.nowPlayingStyle.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "Classic")

    fun toggleLiked(song: Song) {
        viewModelScope.launch { prefs.toggleLiked(song) }
    }
}
