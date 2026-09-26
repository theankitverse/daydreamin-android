package com.daydreamin.app.ui.screens.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.LyricLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LyricsUiState(
    val loading: Boolean = true,
    val syncedLines: List<LyricLine> = emptyList(),
    val plainText: String? = null,
    val notFound: Boolean = false,
)

class LyricsViewModel : ViewModel() {
    private val repo = DaydreaminApp.instance.repository

    private val _state = MutableStateFlow(LyricsUiState())
    val state: StateFlow<LyricsUiState> = _state

    private var loadedFor: String? = null

    fun load(artist: String, title: String) {
        val key = "$artist|$title"
        if (loadedFor == key) return
        loadedFor = key
        _state.value = LyricsUiState(loading = true)
        viewModelScope.launch {
            repo.lyrics(artist, title).fold(
                onSuccess = { res ->
                    val synced = res.syncedLyrics?.let(::parseLrc).orEmpty()
                    _state.value = LyricsUiState(
                        loading = false,
                        syncedLines = synced,
                        plainText = res.plainLyrics,
                        notFound = synced.isEmpty() && res.plainLyrics.isNullOrBlank(),
                    )
                },
                onFailure = { _state.value = LyricsUiState(loading = false, notFound = true) },
            )
        }
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val regex = Regex("""\[(\d{2}):(\d{2})[.:](\d{2,3})]""")
        return lrc.lines().mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            val (min, sec, frac) = match.destructured
            val ms = min.toLong() * 60_000 + sec.toLong() * 1000 + frac.padEnd(3, '0').take(3).toLong()
            val text = line.substring(match.range.last + 1).trim()
            if (text.isEmpty()) null else LyricLine(ms, text)
        }.sortedBy { it.timeMs }
    }
}
