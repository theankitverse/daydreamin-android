package com.daydreamin.app.data.update

import com.daydreamin.app.platform.Log
import com.daydreamin.app.platform.openUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private const val TAG = "UpdateInstaller"

/**
 * Updating the desktop app: downloads this system's installer from the release, checks it's
 * exactly the file the release lists (size and SHA-256), and opens it — on a Mac the disk image
 * (drag Daydreamin into Applications), on Windows the installer (it updates in place, so the app
 * closes to let it), on Linux the package in the system's software installer.
 */
actual object UpdateInstaller {
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    actual val state: StateFlow<InstallState> = _state
    private var job: Job? = null
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private val os = System.getProperty("os.name").lowercase()

    /** Which of the release's downloads is for this computer. */
    internal fun downloadKey(): String? = when {
        "mac" in os -> if (System.getProperty("os.arch").contains("aarch64") || System.getProperty("os.arch").contains("arm")) "macos-arm64" else "macos-x64"
        "win" in os -> "windows"
        File("/usr/bin/dpkg").exists() -> "deb"
        File("/usr/bin/rpm").exists() -> "rpm"
        else -> null
    }

    actual fun start(remote: RemoteVersion, scope: CoroutineScope) {
        if (job?.isActive == true) return
        // Windows: the setup wizard (from 1.1); an older release only has the .msi.
        val download = downloadKey()?.let { key -> (if (key == "windows") remote.downloads["windows-setup"] else null) ?: remote.downloads[key] }
        if (download == null) { openReleasePage(remote); return }
        job = scope.launch {
            runCatching {
                _state.value = InstallState.Downloading(0f)
                val file = fetch(download)
                _state.value = InstallState.Installing
                opener(file)
            }.onFailure { e ->
                Log.w(TAG, "update failed", e)
                _state.value = InstallState.Failed(e.message ?: "The update couldn't be downloaded.")
            }
        }
    }

    private suspend fun fetch(download: DesktopDownload): File = withContext(Dispatchers.IO) {
        val name = download.url.substringAfterLast('/').substringBefore('?').ifBlank { "Daydreamin-update" }
        val dir = File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory } ?: File(System.getProperty("java.io.tmpdir"))
        val target = File(dir, name)
        val partial = File(dir, "$name.part")
        try {
        client.newCall(Request.Builder().url(download.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The download failed (HTTP ${response.code}). Try again in a moment.")
            val body = response.body ?: throw IOException("The download came back empty.")
            val total = download.size.takeIf { it > 0 } ?: body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            partial.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = InstallState.Downloading(done.toFloat() / total)
                    }
                }
            }
            _state.value = InstallState.Verifying
            if (download.size > 0 && done != download.size) throw IOException("The download was incomplete. Try again.")
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (download.sha256 != null && !sha.equals(download.sha256, ignoreCase = true)) throw IOException("The download didn't match the release. Try again.")
        }
        } catch (e: Throwable) {
            partial.delete() // never leave a broken download lying in Downloads
            throw e
        }
        java.nio.file.Files.move(partial.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        target
    }

    /** Opens the downloaded installer (replaced in tests). */
    internal var opener: (File) -> Unit = ::open

    private fun open(file: File) {
        when {
            "win" in os -> {
                // The installer replaces the app's files, so the app steps aside for it.
                if (file.name.endsWith(".exe", ignoreCase = true)) ProcessBuilder(file.absolutePath).start()
                else ProcessBuilder("msiexec", "/i", file.absolutePath).start()
                exitProcess(0)
            }
            "mac" in os -> ProcessBuilder("open", file.absolutePath).start()
            else -> runCatching { Desktop.getDesktop().open(file) }.getOrElse { ProcessBuilder("xdg-open", file.absolutePath).start() }
        }
    }

    actual fun openReleasePage(remote: RemoteVersion) = openUrl(remote.url, "No browser found to open the release page")
    actual fun canInstallNow(): Boolean = true
    actual fun reset() { if (job?.isActive != true) _state.value = InstallState.Idle }
}
