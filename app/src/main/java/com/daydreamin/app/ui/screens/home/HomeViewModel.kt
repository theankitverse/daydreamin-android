package com.daydreamin.app.ui.screens.home

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.Playlist
import com.daydreamin.app.data.recommend.HomeFeed
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import com.daydreamin.app.player.SongPrecacher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

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

/** The chart — only shown before there's enough listening to personalize from. */
data class ChartState(val loading: Boolean = true, val songs: List<Song> = emptyList(), val error: String? = null)

/** A mood tile's songs, shown in place of the feed until you go back. */
data class MoodBrowse(val mood: MoodCard, val loading: Boolean = true, val songs: List<Song> = emptyList(), val error: String? = null)

class HomeViewModel : ViewModel() {
    private val app = DaydreaminApp.instance
    private val repo = app.repository

    val feed: StateFlow<HomeFeed?> = app.homeFeed.feed
    val refreshing: StateFlow<Boolean> = app.homeFeed.refreshing
    val hasTaste: StateFlow<Boolean?> = app.homeFeed.hasTaste
    val feedFailed: StateFlow<Boolean> = app.homeFeed.failed

    /** Most recent first — feeds "Recently played". */
    val history: StateFlow<List<Song>> = app.prefs.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val liked: StateFlow<List<Song>> = app.prefs.likedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = app.prefs.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _chart = MutableStateFlow(ChartState())
    val chart: StateFlow<ChartState> = _chart

    private val _browse = MutableStateFlow<MoodBrowse?>(null)
    val browse: StateFlow<MoodBrowse?> = _browse

    // The first chart load reuses DaydreaminApp's chartPrefetch (in flight since process start).
    private var isFirstLoad = true
    private var moodJob: Job? = null

    init {
        Log.d(TAG, "HomeViewModel created at +${SystemClock.elapsedRealtime() - app.processStartAtMs}ms since process start")
        loadChart()
    }

    /** "New mix" — rebuilds your recommendations now. Runs in the app's scope so leaving Home doesn't cancel it. */
    fun refreshMix() {
        app.appScope.launch { app.homeFeed.refresh() }
    }

    fun retryChart() = loadChart()

    fun openMood(mood: MoodCard) {
        _browse.value = MoodBrowse(mood)
        loadMood(mood)
    }

    fun closeMood() {
        moodJob?.cancel()
        _browse.value = null
    }

    fun retryMood() {
        _browse.value?.mood?.let { loadMood(it) }
    }

    private fun loadChart() {
        _chart.value = _chart.value.copy(loading = true, error = null)
        val usingPrefetch = isFirstLoad
        isFirstLoad = false
        viewModelScope.launch {
            val result = if (usingPrefetch) app.chartPrefetch.await() else repo.chart()
            result.fold(
                onSuccess = { songs ->
                    if (usingPrefetch) Log.d(TAG, "trending list ready at +${SystemClock.elapsedRealtime() - app.processStartAtMs}ms since process start (${songs.size} songs)")
                    _chart.value = ChartState(loading = false, songs = songs)
                    // Only worth warming up when the chart is what Home actually leads with.
                    if (hasTaste.value == false) prefetchTop(songs)
                },
                onFailure = { e -> _chart.value = ChartState(loading = false, error = friendlyError(e)) },
            )
        }
    }

    private fun loadMood(mood: MoodCard) {
        moodJob?.cancel()
        _browse.value = MoodBrowse(mood, loading = true)
        moodJob = viewModelScope.launch {
            repo.search(mood.query).fold(
                onSuccess = { found ->
                    // The catalog often lists the same recording on several compilations — one row each is plenty.
                    val songs = found.distinctBy { it.title.trim().lowercase() to it.artist.trim().lowercase() }
                    _browse.value = MoodBrowse(mood, loading = false, songs = songs)
                    prefetchTop(songs)
                },
                onFailure = { e -> _browse.value = MoodBrowse(mood, loading = false, error = friendlyError(e)) },
            )
        }
    }

    /**
     * Speculatively resolves the top few visible songs so tapping one feels instant. The very top
     * one also gets its first ~20s of audio pre-buffered — the single most likely tap.
     */
    private fun prefetchTop(songs: List<Song>) {
        viewModelScope.launch {
            songs.take(3).forEachIndexed { index, song ->
                YouTubeExtractorService.prefetchSong(song.artist, song.title)
                if (index == 0) SongPrecacher.precacheLeadIn(app, song)
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
