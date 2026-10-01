package com.daydreamin.app.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import com.daydreamin.app.data.remote.LegacyTls.withLegacyRoots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Where an in-app update is. */
sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val fraction: Float?) : InstallState
    data object Verifying : InstallState
    /** Android needs "Install unknown apps" allowed for Daydreamin first; the settings page has been opened. */
    data object NeedsPermission : InstallState
    data object Installing : InstallState
    data class Failed(val message: String) : InstallState
}

/**
 * Downloads the new APK itself and hands it straight to Android's installer, instead of sending
 * you to a browser. Updating through the browser meant downloading from GitHub, finding the file
 * and opening it with the phone's installer — any of which could hand the installer an incomplete
 * or wrong file, and on Xiaomi phones all you'd see is "package appears to be invalid".
 *
 * Here the file is checked before Android ever sees it — exact size, SHA-256 from version.json,
 * and that it really is the next Daydreamin — and if Android still refuses, the reason it gives is
 * shown as-is.
 */
object UpdateInstaller {
    private const val TAG = "UpdateInstaller"

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .withLegacyRoots()
        .build()

    private var job: Job? = null

    /** Starts (or continues, after the permission screen) updating to [remote]. */
    fun start(context: Context, remote: RemoteVersion, scope: CoroutineScope) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        val apkUrl = remote.apkUrl
        if (apkUrl.isNullOrBlank()) { openReleasePage(app, remote); return }
        job = scope.launch {
            runCatching { update(app, remote, apkUrl) }.onFailure { e ->
                Log.w(TAG, "update failed", e)
                _state.value = InstallState.Failed(friendly(e))
            }
        }
    }

    /** The old way, kept as a fallback: the release page in the browser. */
    fun openReleasePage(context: Context, remote: RemoteVersion) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(remote.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private suspend fun update(context: Context, remote: RemoteVersion, apkUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = InstallState.NeedsPermission
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val file = download(context, apkUrl)
        _state.value = InstallState.Verifying
        withContext(Dispatchers.IO) { verify(context, file, remote) }
        _state.value = InstallState.Installing
        withContext(Dispatchers.IO) { install(context, file) }
    }

    private suspend fun download(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // never install a leftover from an earlier attempt
        val file = File(dir, "daydreamin-update.apk")
        _state.value = InstallState.Downloading(null)
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("the download failed (HTTP ${response.code})")
            val body = response.body ?: throw IOException("the download was empty")
            val total = body.contentLength().takeIf { it > 0 }
            var read = 0L
            var lastShown = -1
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total != null) {
                            val percent = (read * 100 / total).toInt()
                            if (percent != lastShown) { lastShown = percent; _state.value = InstallState.Downloading(read.toFloat() / total) }
                        }
                    }
                }
            }
        }
        file
    }

    /** Refuses anything that isn't exactly the release version.json describes. */
    private fun verify(context: Context, file: File, remote: RemoteVersion) {
        remote.apkSize?.let { expected ->
            if (file.length() != expected) throw IOException("the download was incomplete (${file.length()} of $expected bytes) — try again")
        }
        remote.sha256?.let { expected ->
            if (!sha256(file).equals(expected, ignoreCase = true)) throw IOException("the download was damaged (its fingerprint didn't match) — try again")
        }
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
            ?: throw IOException("the downloaded file isn't a readable app — try again")
        if (info.packageName != context.packageName) throw IOException("the download wasn't Daydreamin")
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        if (code != remote.versionCode.toLong()) throw IOException("the download was version $code, not ${remote.versionCode}")
    }

    private fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("daydreamin.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(context, sessionId, Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName), flags)
            session.commit(callback.intentSender)
        }
        Log.d(TAG, "install session $sessionId committed (${file.length()} bytes)")
    }

    internal fun onResult(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.d(TAG, "install status=$status message=$detail")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android's own "Update this app?" confirmation.
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) { _state.value = InstallState.Failed("Android didn't show the install screen — try again"); return }
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { _state.value = InstallState.Failed("Android didn't show the install screen — open Daydreamin and try again") }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = InstallState.Idle // the new version is starting; this process is on its way out
            else -> _state.value = InstallState.Failed(installFailure(status, detail))
        }
    }

    internal fun reset() { if (job?.isActive != true) _state.value = InstallState.Idle }

    private fun friendly(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            e is java.net.UnknownHostException || "timeout" in msg.lowercase() || "failed to connect" in msg.lowercase() ->
                "Couldn't download the update — check your connection and try again."
            e is IOException && msg.isNotBlank() -> "Couldn't update: $msg."
            else -> "Couldn't update${if (msg.isNotBlank()) ": $msg" else ""}."
        }
    }
}

/** What Android's installer said, in plain words — with its own wording kept, so a failure can be diagnosed. */
internal fun installFailure(status: Int, detail: String?): String {
    val why = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "The update was cancelled."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Your phone's security settings blocked the update."
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "Android says the update conflicts with the installed app."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Android says this version isn't compatible with your phone."
        PackageInstaller.STATUS_FAILURE_INVALID -> "Android rejected the update file."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "There isn't enough free storage for the update."
        else -> "Android couldn't install the update."
    }
    return if (detail.isNullOrBlank()) why else "$why ($detail)"
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Receives Android's answer to an install session: show its confirmation, or report how it went. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = UpdateInstaller.onResult(context, intent)
}
