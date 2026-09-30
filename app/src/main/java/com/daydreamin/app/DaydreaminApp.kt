package com.daydreamin.app

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.Region
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

class DaydreaminApp : Application(), coil.ImageLoaderFactory {

    /** Coil's defaults, plus the extra trusted roots on Android 7.0 and older (album art from Apple needs them). */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this).apply {
        if (com.daydreamin.app.data.remote.LegacyTls.isNeeded) {
            okHttpClient { with(com.daydreamin.app.data.remote.LegacyTls) { okhttp3.OkHttpClient.Builder().withLegacyRoots().build() } }
        }
    }.build()


    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var prefs: AppPreferences
        private set
    val repository = MusicRepository()

    /** Process start (best available proxy: this class's own <clinit>/construction, which
     *  happens as early as anything in the app can run) — the reference point every other
     *  "time to X" figure in [com.daydreamin.app.ui.screens.home.HomeViewModel] and
     *  [com.daydreamin.app.player.PlayerController]'s own timing logs is measured against, so
     *  cold-start latency can actually be profiled end to end instead of guessed at piecemeal. */
    val processStartAtMs: Long = SystemClock.elapsedRealtime()

    /** Home's personal recommendations — see [HomeFeedRepository]. */
    lateinit var homeFeed: HomeFeedRepository
        private set

    /** Home has something real to show — your mix, or what's popular where you are. */
    fun homeFeedReady(): Boolean = homeFeed.feed.value != null

    override fun onCreate() {
        super.onCreate()
        instance = this
        TasteProfile.init(this)
        val prefsStart = SystemClock.elapsedRealtime()
        prefs = AppPreferences(this)
        Log.d(TAG, "AppPreferences() at +${SystemClock.elapsedRealtime() - processStartAtMs}ms (took ${SystemClock.elapsedRealtime() - prefsStart}ms)")

        Region.init(this)
        val newPipeStart = SystemClock.elapsedRealtime()
        YouTubeExtractorService.init(Region.country)
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
