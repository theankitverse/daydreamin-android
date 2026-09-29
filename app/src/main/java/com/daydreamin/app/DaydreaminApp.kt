package com.daydreamin.app

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.AppPreferences
import com.daydreamin.app.data.recommend.HomeFeedRepository
import com.daydreamin.app.data.taste.TasteProfile
import com.daydreamin.app.data.repository.MusicRepository
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

private const val TAG = "StartupTiming"

class DaydreaminApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var prefs: AppPreferences
        private set
    val repository = MusicRepository()

    /** Fired the instant the app process exists — before Android has even started building the
     *  Activity/Compose UI, let alone `HomeViewModel`. Measured cold-start: the trending-chart
     *  network call was taking ~1.9s *serially after* ~1.76s of Activity/Compose startup, when
     *  the two have no actual dependency on each other — nothing about fetching the chart needs
     *  the UI to exist first. Starting it here lets that network round trip overlap with
     *  Android's own startup work instead of stacking after it; `HomeViewModel` awaits this
     *  instead of firing its own fresh request the first time it loads. */
    val chartPrefetch: Deferred<Result<List<Song>>> = appScope.async { repository.chart() }

    /** Process start (best available proxy: this class's own <clinit>/construction, which
     *  happens as early as anything in the app can run) — the reference point every other
     *  "time to X" figure in [com.daydreamin.app.ui.screens.home.HomeViewModel] and
     *  [com.daydreamin.app.player.PlayerController]'s own timing logs is measured against, so
     *  cold-start latency can actually be profiled end to end instead of guessed at piecemeal. */
    val processStartAtMs: Long = SystemClock.elapsedRealtime()

    /** Home's personal recommendations — see [HomeFeedRepository]. */
    lateinit var homeFeed: HomeFeedRepository
        private set

    /** Home has something real to show: your cached mix, or (with nothing to personalize from yet) the chart. */
    fun homeFeedReady(): Boolean = homeFeed.feed.value != null || chartPrefetch.isCompleted

    override fun onCreate() {
        super.onCreate()
        instance = this
        TasteProfile.init(this)
        val prefsStart = SystemClock.elapsedRealtime()
        prefs = AppPreferences(this)
        Log.d(TAG, "AppPreferences() at +${SystemClock.elapsedRealtime() - processStartAtMs}ms (took ${SystemClock.elapsedRealtime() - prefsStart}ms)")

        val newPipeStart = SystemClock.elapsedRealtime()
        YouTubeExtractorService.init()
        Log.d(TAG, "YouTubeExtractorService.init() at +${SystemClock.elapsedRealtime() - processStartAtMs}ms (took ${SystemClock.elapsedRealtime() - newPipeStart}ms)")

        // Keeps a copy of the library in Download/Daydreamin so it survives an uninstall.
        com.daydreamin.app.data.prefs.AutoBackup.start(this, prefs, appScope)

        // One background check for a newer release — there's no Play Store here to do this
        // automatically. Fire-and-forget: nothing on screen waits on it.
        com.daydreamin.app.data.update.UpdateChecker.start(prefs, appScope)

        // Loads the last personal mix from disk straight away (Home shows it on the first frame),
        // then rebuilds it in the background if it's stale.
        homeFeed = HomeFeedRepository(this).also { it.start() }

        Log.d(TAG, "DaydreaminApp.onCreate() done at +${SystemClock.elapsedRealtime() - processStartAtMs}ms")
    }

    companion object {
        lateinit var instance: DaydreaminApp
            private set
    }
}
