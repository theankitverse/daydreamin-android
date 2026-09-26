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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "StartupTiming"

data class MoodCard(val title: String, val subtitle: String, val query: String, val gradient: List<Long>)

val moodCards = listOf(
    MoodCard("Chill Vibes", "Relax and unwind", "chill lofi relax", listOf(0xFF3B4E7A, 0xFF1B1F33)),
    MoodCard("Late Night", "Perfect for now", "late night drive songs", listOf(0xFF2B1F4A, 0xFF120E22)),
    MoodCard("Study Focus", "Deep work beats", "lofi study focus beats", listOf(0xFF244A3E, 0xFF10201B)),
    MoodCard("Workout", "Get moving", "gym workout motivational", listOf(0xFF5A2233, 0xFF210D14)),
)

val genreChips = listOf("All", "Chill", "Hindi", "Lo-fi", "Pop")

data class HomeUiState(
    val loading: Boolean = true,
    val trending: List<Song> = emptyList(),
    val selectedChip: String = "All",
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    private val repo = DaydreaminApp.instance.repository

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

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
        _state.value = _state.value.copy(selectedChip = chip)
        if (chip == "All") loadChart() else loadForQuery("$chip songs")
    }

    fun onMoodSelected(mood: MoodCard) {
        _state.value = _state.value.copy(selectedChip = mood.title)
        loadForQuery(mood.query)
    }

    fun retry() {
        if (_state.value.selectedChip == "All") loadChart() else loadForQuery(_state.value.selectedChip)
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
                onFailure = { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "Couldn't load trending songs") },
            )
        }
    }

    private fun loadForQuery(query: String) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repo.search(query).fold(
                onSuccess = { songs -> _state.value = _state.value.copy(loading = false, trending = songs); prefetchTop(songs) },
                onFailure = { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "Couldn't load songs") },
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
