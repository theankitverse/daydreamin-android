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

    fun setAccent(name: String) = viewModelScope.launch { prefs.setAccentName(name) }
}
