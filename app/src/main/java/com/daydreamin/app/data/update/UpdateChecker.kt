package com.daydreamin.app.data.update

import com.daydreamin.app.BuildConfig
import com.daydreamin.app.data.prefs.AppPreferences
import com.daydreamin.app.data.remote.LegacyTls.withLegacyRoots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * What `version.json` (published at the repo root, updated on every release) says the latest
 * build is. [url] is where to send someone to get it — the GitHub Releases page, not a raw APK
 * link, so they see release notes and Android's "unknown sources" prompt shows a real page.
 */
@Serializable
data class RemoteVersion(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val notes: String? = null,
    /** The release's APK itself, for updating in-app; older version.json files without it fall back to [url]. */
    val apkUrl: String? = null,
    val apkSize: Long? = null,
    val sha256: String? = null,
)

enum class UpdateStatus { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, FAILED }

data class UpdateState(val status: UpdateStatus, val remote: RemoteVersion? = null)

/**
 * There's no Play Store here to notify anyone of a new build, so this is the substitute: once
 * per app launch, fetch one small JSON file from GitHub and compare its version to this build's.
 *
 * Deliberately light-touch so it can never make the app feel slower: it runs on [Dispatchers.IO]
 * inside the app's own background scope (the same pattern as [com.daydreamin.app.data.prefs.AutoBackup]),
 * nothing on screen waits on it, it has its own short timeout separate from the app's other
 * network calls, and any failure (offline, GitHub unreachable, malformed file) is silent — it
 * just leaves [state] as [UpdateStatus.FAILED] rather than showing an error anywhere unasked.
 */
object UpdateChecker {
    // Raw file from the Android project's own repo — see /version.json there, updated on each release.
    private val VERSION_URL = BuildConfig.UPDATE_URL

    /**
     * Where else the same file is published. Some networks in India (Jio, Airtel) block
     * raw.githubusercontent.com, which left the update check failing for good. Each release also
     * carries version.json as a download (the same host the APK itself downloads from), and
     * jsDelivr mirrors the repo. All are asked at once; the newest answer wins.
     */
    private val MIRRORS = listOf(
        "https://github.com/theankitverse/daydreamin-android/releases/latest/download/version.json",
        "https://cdn.jsdelivr.net/gh/theankitverse/daydreamin-android@main/version.json",
    )
    private const val DEFAULT_URL = "https://raw.githubusercontent.com/theankitverse/daydreamin-android/main/version.json"

    /** Coming back to the app checks again after this long (the process can stay alive for days). */
    private const val RECHECK_AFTER_MS = 10 * 60 * 1000L
    @Volatile private var lastCheckAtMs = 0L

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .withLegacyRoots()
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(UpdateState(UpdateStatus.IDLE))
    val state: StateFlow<UpdateState> = _state

    private var prefs: AppPreferences? = null
    private var started = false

    /** Called once from [com.daydreamin.app.DaydreaminApp.onCreate] — fires a single background check. */
    fun start(prefs: AppPreferences, scope: CoroutineScope) {
        if (started) return
        started = true
        this.prefs = prefs
        scope.launch { checkNow() }
    }

    /** When the app comes back on screen: checks again if the last check was a while ago, or failed. */
    fun recheckIfStale(scope: CoroutineScope) {
        if (!started || _state.value.status == UpdateStatus.CHECKING) return
        val stale = android.os.SystemClock.elapsedRealtime() - lastCheckAtMs > RECHECK_AFTER_MS
        if (stale || _state.value.status == UpdateStatus.FAILED) scope.launch { checkNow() }
    }

    /** Also used by Settings' manual "Check for updates" row — same call, same silent-failure behaviour. */
    suspend fun checkNow() {
        _state.value = UpdateState(UpdateStatus.CHECKING)
        lastCheckAtMs = android.os.SystemClock.elapsedRealtime()
        // A test build pointed at its own file asks only that file.
        val urls = if (VERSION_URL == DEFAULT_URL) listOf(VERSION_URL) + MIRRORS else listOf(VERSION_URL)
        val remote = withContext(Dispatchers.IO) {
            // Each request has its own time limit (the client's), so a blocked address can't hide a good answer.
            urls.map { url -> async { fetch(url) } }.awaitAll().filterNotNull().maxByOrNull { it.versionCode }
        }
        _state.value = when {
            remote == null -> UpdateState(UpdateStatus.FAILED)
            remote.versionCode > BuildConfig.VERSION_CODE -> UpdateState(UpdateStatus.AVAILABLE, remote)
            else -> UpdateState(UpdateStatus.UP_TO_DATE, remote)
        }
    }

    private fun fetch(url: String): RemoteVersion? = runCatching {
        // A fresh copy, not one a cache along the way has kept.
        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("empty response")
            json.decodeFromString<RemoteVersion>(body)
        }
    }.onFailure { android.util.Log.w("UpdateChecker", "update check failed for $url: ${it.message}") }.getOrNull()

    /** Dismissing the Home banner for this version — it only reappears if a newer one is published later. */
    suspend fun dismiss(versionCode: Int) {
        prefs?.setDismissedUpdateVersion(versionCode)
    }

    suspend fun isDismissed(versionCode: Int): Boolean =
        prefs?.dismissedUpdateVersion?.first() == versionCode
}
