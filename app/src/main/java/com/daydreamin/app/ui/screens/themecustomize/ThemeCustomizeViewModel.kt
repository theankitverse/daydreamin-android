package com.daydreamin.app.ui.screens.themecustomize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daydreamin.app.DaydreaminApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeCustomizeViewModel : ViewModel() {
    private val prefs = DaydreaminApp.instance.prefs

    val accentName: StateFlow<String> = prefs.accentName.stateIn(viewModelScope, SharingStarted.Eagerly, "Violet")
    val backgroundStyle: StateFlow<String> = prefs.backgroundStyle.stateIn(viewModelScope, SharingStarted.Eagerly, "Default")
    val nowPlayingStyle: StateFlow<String> = prefs.nowPlayingStyle.stateIn(viewModelScope, SharingStarted.Eagerly, "Classic")

    fun setAccent(name: String) = viewModelScope.launch { prefs.setAccentName(name) }
    fun setBackgroundStyle(v: String) = viewModelScope.launch { prefs.setBackgroundStyle(v) }
    fun setNowPlayingStyle(v: String) = viewModelScope.launch { prefs.setNowPlayingStyle(v) }
}
