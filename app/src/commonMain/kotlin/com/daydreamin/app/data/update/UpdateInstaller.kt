package com.daydreamin.app.data.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

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

/** Updates the app in place to a newer release; each platform installs its own kind of package. */
expect object UpdateInstaller {
    val state: StateFlow<InstallState>

    /** Starts (or continues, after a permission prompt) updating to [remote]. */
    fun start(remote: RemoteVersion, scope: CoroutineScope)

    /** The fallback: the release page in the browser. */
    fun openReleasePage(remote: RemoteVersion)

    /** Whether installing can go ahead without asking for a permission first. */
    fun canInstallNow(): Boolean

    fun reset()
}
