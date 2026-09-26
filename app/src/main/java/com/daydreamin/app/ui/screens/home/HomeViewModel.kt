package com.daydreamin.app.ui.screens.home

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import com.daydreamin.app.player.SongPrecacher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import kotlinx.coroutines.launch

private const val TAG = "StartupTiming"

/** [accent] is the mood's signature color — shown as the light strip on its tile. */
data class MoodCard(val title: String, val query: String, val accent: Long)

val moodCards = listOf(
    MoodCard("Chill", "chill lofi relax", 0xFF7FB2FF),
    MoodCard("Late Night", "late night drive songs", 0xFFA78BFA),
    MoodCard("Focus", "lofi study focus beats", 0xFF5EEAD4),
    MoodCard("Workout", "gym workout motivational", 0xFFFF7A7A),
    MoodCard("Romance", "romantic love songs", 0xFFF472B6),
    MoodCard("Party", "party dance hits", 0xFFFBBF24),
    MoodCard("Devotional", "devotional bhajan kirtan", 0xFFFDBA74),
    MoodCard("Feel Good", "feel good happy songs", 0xFF86EFAC),
)

val genreChips = listOf("All", "Chill", "Hindi", "Lo-fi", "Pop")

data class HomeUiState(
    val loading: Boolean = true,
    val trending: List<Song> = emptyList(),
    val selectedChip: String = "All",
    /** Whether [selectedChip] came from a Moods tile rather than a genre chip (a mood and a genre can share a name). */
    val selectionIsMood: Boolean = false,
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    private val repo = DaydreaminApp.instance.repository

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    /** Most recent first — feeds "Listen again". */
    val history: StateFlow<List<Song>> = DaydreaminApp.instance.prefs.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Feeds "From your likes". */
    val liked: StateFlow<List<Song>> = DaydreaminApp.instance.prefs.likedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Only the very first chart load reflects genuine cold-start timing — later ones (a chip
    // switch, a retry) aren't "app launch to first content" anymore, so they're left unlogged.
    // Also the one load that reuses DaydreaminApp's chartPrefetch (already in flight since
    // process start) instead of firing a fresh request — every later reload gets its own.
    private var isFirstLoad = true

    init {
        val vmCreatedAtMs = SystemClock.elapsedRealtime()
        Log.d(TAG, "HomeViewModel created at +${vmCreatedAtMs - DaydreaminApp.instance.processStartAtMs}ms since process start")
        loadChart()
    }

    fun onChipSelected(chip: String) {
        _state.value = _state.value.copy(selectedChip = chip, selectionIsMood = false)
        if (chip == "All") loadChart() else loadForQuery("$chip songs")
    }

    fun onMoodSelected(mood: MoodCard) {
        _state.value = _state.value.copy(selectedChip = mood.title, selectionIsMood = true)
        loadForQuery(mood.query)
    }

    fun retry() {
        val query = lastQuery
        if (_state.value.selectedChip == "All" || query == null) loadChart() else loadForQuery(query)
    }

    private fun loadChart() {
        _state.value = _state.value.copy(loading = true, error = null)
        val usingPrefetch = isFirstLoad
        isFirstLoad = false
        viewModelScope.launch {
            val result = if (usingPrefetch) DaydreaminApp.instance.chartPrefetch.await() else repo.chart()
            result.fold(
                onSuccess = { songs ->
                    if (usingPrefetch) {
                        Log.d(TAG, "trending list ready at +${SystemClock.elapsedRealtime() - DaydreaminApp.instance.processStartAtMs}ms since process start (${songs.size} songs)")
                    }
                    _state.value = _state.value.copy(loading = false, trending = songs)
                    prefetchTop(songs)
                },
                onFailure = { e -> _state.value = _state.value.copy(loading = false, error = friendlyError(e)) },
            )
        }
    }

    /** The exact query behind the current results, so Retry repeats what failed (not the chip's label). */
    private var lastQuery: String? = null

    private fun loadForQuery(query: String) {
        lastQuery = query
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repo.search(query).fold(
                onSuccess = { found ->
                    // The catalog often lists the same recording on several compilations — one row each is plenty.
                    val songs = found.distinctBy { it.title.trim().lowercase() to it.artist.trim().lowercase() }
                    _state.value = _state.value.copy(loading = false, trending = songs)
                    prefetchTop(songs)
                },
                onFailure = { e -> _state.value = _state.value.copy(loading = false, error = friendlyError(e)) },
            )
        }
    }

    /**
     * Speculatively resolves the top few visible songs so tapping one of them feels instant —
     * most taps land on what's already on screen. The very top one (the single most likely tap
     * target) also gets its first ~20s of audio pre-buffered to disk, not just the stream URL
     * resolved — that's the difference between "no search step" and "no wait at all" on tap.
     * Not extended to all 3: fully racing ahead on audio bytes for songs that might never get
     * tapped isn't worth the data for anything past the single best bet.
     */
    private fun prefetchTop(songs: List<Song>) {
        viewModelScope.launch {
            songs.take(3).forEachIndexed { index, song ->
                YouTubeExtractorService.prefetchSong(song.artist, song.title)
                if (index == 0) SongPrecacher.precacheLeadIn(DaydreaminApp.instance, song)
                delay(250)
            }
        }
    }
}

/** What the user sees when a load fails — never a raw exception message. */
private fun friendlyError(e: Throwable): String {
    var cause: Throwable? = e
    while (cause != null) {
        if (cause is IOException) return "You're offline. Check your connection and try again."
        cause = cause.cause
    }
    return "Something went wrong while loading. Try again in a moment."
}
