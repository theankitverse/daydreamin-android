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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private const val VERSION_URL = "https://raw.githubusercontent.com/theankitverse/daydreamin-android/main/version.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
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

    /** Also used by Settings' manual "Check for updates" row — same call, same silent-failure behaviour. */
    suspend fun checkNow() {
        _state.value = UpdateState(UpdateStatus.CHECKING)
        val remote = withContext(Dispatchers.IO) {
            withTimeoutOrNull(8_000) {
                runCatching {
                    client.newCall(Request.Builder().url(VERSION_URL).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val body = response.body?.string() ?: throw IOException("empty response")
                        json.decodeFromString<RemoteVersion>(body)
                    }
                }.onFailure { android.util.Log.w("UpdateChecker", "update check failed", it) }.getOrNull()
            }
        }
        _state.value = when {
            remote == null -> UpdateState(UpdateStatus.FAILED)
            remote.versionCode > BuildConfig.VERSION_CODE -> UpdateState(UpdateStatus.AVAILABLE, remote)
            else -> UpdateState(UpdateStatus.UP_TO_DATE, remote)
        }
    }

    /** Dismissing the Home banner for this version — it only reappears if a newer one is published later. */
    suspend fun dismiss(versionCode: Int) {
        prefs?.setDismissedUpdateVersion(versionCode)
    }

    suspend fun isDismissed(versionCode: Int): Boolean =
        prefs?.dismissedUpdateVersion?.first() == versionCode
}
