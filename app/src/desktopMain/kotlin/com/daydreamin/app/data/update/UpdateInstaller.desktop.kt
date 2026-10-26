package com.daydreamin.app.data.update

import com.daydreamin.app.platform.openUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Installing in place comes with the desktop installers (Phase 5); until then, the release page.
actual object UpdateInstaller {
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    actual val state: StateFlow<InstallState> = _state
    actual fun start(remote: RemoteVersion, scope: CoroutineScope) = openReleasePage(remote)
    actual fun openReleasePage(remote: RemoteVersion) = openUrl(remote.url, "No browser found to open the release page")
    actual fun canInstallNow(): Boolean = true
    actual fun reset() { _state.value = InstallState.Idle }
}
