package com.daydreamin.app

import com.daydreamin.app.data.prefs.AppPreferences
import com.daydreamin.app.data.recommend.HomeFeedRepository
import com.daydreamin.app.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * The app's long-lived pieces, shared by every screen on every platform. Each platform's startup
 * (Android: DaydreaminApp.onCreate) calls [init] before any screen is shown.
 */
object AppGraph {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository = MusicRepository()

    /** Where the app keeps its own files (library cache, taste profile, avatar). */
    lateinit var filesDir: File
        private set
    lateinit var prefs: AppPreferences
        private set

    /** Home's personal recommendations — see [HomeFeedRepository]. */
    lateinit var homeFeed: HomeFeedRepository
        private set

    /** Process start — the reference point for the "time to X" startup logs. */
    var processStartAtMs: Long = 0L
        private set

    fun init(filesDir: File, prefs: AppPreferences, processStartAtMs: Long) {
        this.filesDir = filesDir
        this.prefs = prefs
        this.processStartAtMs = processStartAtMs
    }

    fun startHomeFeed() {
        homeFeed = HomeFeedRepository().also { it.start() }
    }

    /** Home has something real to show — your mix, or what's popular where you are. */
    fun homeFeedReady(): Boolean = homeFeed.feed.value != null
}
