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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "StartupTiming"

class DaydreaminApp : Application(), coil3.SingletonImageLoader.Factory {

    /** Coil's defaults, plus the extra trusted roots on Android 7.0 and older (album art from Apple needs them). */
    override fun newImageLoader(context: coil3.PlatformContext): coil3.ImageLoader = coil3.ImageLoader.Builder(context).apply {
        if (com.daydreamin.app.data.remote.LegacyTls.isNeeded) {
            components {
                add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = {
                    with(com.daydreamin.app.data.remote.LegacyTls) { okhttp3.OkHttpClient.Builder().withLegacyRoots().build() }
                }))
            }
        }
    }.build()

    // The app's long-lived pieces live in AppGraph (shared with the desktop app); these read through to it.
    val appScope: CoroutineScope get() = AppGraph.appScope
    val prefs: AppPreferences get() = AppGraph.prefs
    val repository: MusicRepository get() = AppGraph.repository
    val homeFeed: HomeFeedRepository get() = AppGraph.homeFeed

    /** Process start (best available proxy: this class's own <clinit>/construction, which
     *  happens as early as anything in the app can run) — the reference point every other
     *  "time to X" figure in [com.daydreamin.app.ui.screens.home.HomeViewModel] and
     *  [com.daydreamin.app.player.PlayerController]'s own timing logs is measured against, so
     *  cold-start latency can actually be profiled end to end instead of guessed at piecemeal. */
    val processStartAtMs: Long = SystemClock.elapsedRealtime()

    /** Home has something real to show — your mix, or what's popular where you are. */
    fun homeFeedReady(): Boolean = AppGraph.homeFeedReady()

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppInfo.versionName = BuildConfig.VERSION_NAME
        AppInfo.versionCode = BuildConfig.VERSION_CODE
        AppInfo.updateUrl = BuildConfig.UPDATE_URL
        AppInfo.usageTestMode = BuildConfig.USAGE_TEST_MODE
        AppInfo.debug = BuildConfig.DEBUG
        AppInfo.platformName = "Android"
        TasteProfile.init(filesDir)
        val prefsStart = SystemClock.elapsedRealtime()
        AppGraph.init(filesDir, AppPreferences(this), processStartAtMs)
        Log.d(TAG, "AppPreferences() at +${SystemClock.elapsedRealtime() - processStartAtMs}ms (took ${SystemClock.elapsedRealtime() - prefsStart}ms)")

        // The lifetime play count starts from what's already on record (see AppPreferences.totalPlays).
        appScope.launch { prefs.startPlayCount(maxOf(TasteProfile.recordedPlays(), prefs.history.first().size)) }

        Region.init(com.daydreamin.app.data.detectCountry(this))
        val newPipeStart = SystemClock.elapsedRealtime()
        YouTubeExtractorService.init(Region.country)
        Log.d(TAG, "YouTubeExtractorService.init() at +${SystemClock.elapsedRealtime() - processStartAtMs}ms (took ${SystemClock.elapsedRealtime() - newPipeStart}ms)")

        // Keeps a copy of the library in Download/Daydreamin so it survives an uninstall.
        com.daydreamin.app.platform.AutoBackup.start(this, prefs, appScope)

        // One background check for a newer release — there's no Play Store here to do this
        // automatically. Fire-and-forget: nothing on screen waits on it.
        com.daydreamin.app.data.update.UpdateChecker.start(prefs, appScope)

        // At most one anonymous count a day (version, Android version, country, how it was installed); off in Settings.
        com.daydreamin.app.data.usage.UsageStats.start(this, prefs, appScope)

        // Songs kept for offline listening — loads what's on the phone and keeps it in step with what you chose.
        com.daydreamin.app.data.download.Downloads.init(this, appScope)

        // Loads the last personal mix from disk straight away (Home shows it on the first frame),
        // then rebuilds it in the background if it's stale.
        AppGraph.startHomeFeed()

        Log.d(TAG, "DaydreaminApp.onCreate() done at +${SystemClock.elapsedRealtime() - processStartAtMs}ms")
    }

    companion object {
        lateinit var instance: DaydreaminApp
            private set
    }
}
