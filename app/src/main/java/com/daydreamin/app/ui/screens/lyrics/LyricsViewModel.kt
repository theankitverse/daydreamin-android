package com.daydreamin.app.ui.screens.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.LyricLine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * [syncedLines] may contain instrumental breaks — lines with empty [LyricLine.text] — which the
 * UI shows as a breathing "• • •" rather than dropping, so a long solo doesn't leave the last
 * sung line highlighted for a minute.
 */
data class LyricsUiState(
    val loading: Boolean = true,
    val syncedLines: List<LyricLine> = emptyList(),
    val plainText: String? = null,
    val notFound: Boolean = false,
    /** The lookup itself failed for lack of a connection — distinct from "this song has none". */
    val offline: Boolean = false,
)

class LyricsViewModel : ViewModel() {
    private val repo = DaydreaminApp.instance.repository

    private val _state = MutableStateFlow(LyricsUiState())
    val state: StateFlow<LyricsUiState> = _state

    private var loadedFor: String? = null
    private var job: Job? = null
    // Skipping back and forth between songs shouldn't refetch (or flash a loading state).
    private val cache = HashMap<String, LyricsUiState>()

    fun load(artist: String, title: String, force: Boolean = false) {
        val key = "$artist|$title"
        if (loadedFor == key && !force) return
        loadedFor = key
        job?.cancel()
        cache[key]?.takeIf { !force }?.let { _state.value = it; return }
        _state.value = LyricsUiState(loading = true)
        job = viewModelScope.launch {
            val result = repo.lyrics(artist, title).fold(
                onSuccess = { res ->
                    val synced = res.syncedLyrics?.let(::parseLrc).orEmpty()
                    LyricsUiState(
                        loading = false,
                        syncedLines = synced,
                        plainText = res.plainLyrics,
                        notFound = synced.none { it.text.isNotEmpty() } && res.plainLyrics.isNullOrBlank(),
                    )
                },
                onFailure = { e ->
                    val offline = generateSequence(e) { it.cause }.any { it is IOException }
                    LyricsUiState(loading = false, notFound = !offline, offline = offline)
                },
            )
            if (!result.offline) cache[key] = result // an offline miss should be retried next time, not remembered
            if (loadedFor == key) _state.value = result
        }
    }

    fun retry() {
        val key = loadedFor ?: return
        val (artist, title) = key.split("|", limit = 2)
        load(artist, title, force = true)
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val regex = Regex("""\[(\d{2}):(\d{2})[.:](\d{2,3})]""")
        val lines = lrc.lines().mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            val (min, sec, frac) = match.destructured
            val ms = min.toLong() * 60_000 + sec.toLong() * 1000 + frac.padEnd(3, '0').take(3).toLong()
            // Empty timestamped lines mark instrumental breaks — keep them as breaks.
            LyricLine(ms, line.substring(match.range.last + 1).trim())
        }.sortedBy { it.timeMs }
        // Collapse runs of breaks, drop leading/trailing ones (the outro needs no dots).
        val collapsed = mutableListOf<LyricLine>()
        for (l in lines) {
            if (l.text.isEmpty() && (collapsed.isEmpty() || collapsed.last().text.isEmpty())) continue
            collapsed += l
        }
        while (collapsed.isNotEmpty() && collapsed.last().text.isEmpty()) collapsed.removeAt(collapsed.lastIndex)
        // A long intro before the first sung line is a break too.
        if (collapsed.isNotEmpty() && collapsed.first().timeMs > 4_000) collapsed.add(0, LyricLine(0L, ""))
        return collapsed
    }
}
