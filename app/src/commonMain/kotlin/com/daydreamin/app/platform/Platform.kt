package com.daydreamin.app.platform

import androidx.compose.runtime.Composable

/** Logging that goes to Logcat on Android and the console on desktop. */
expect object Log {
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String, tr: Throwable? = null)
    fun e(tag: String, msg: String, tr: Throwable? = null)
}

/** Milliseconds since an arbitrary fixed point; only differences mean anything (Android: SystemClock.elapsedRealtime). */
expect fun elapsedRealtime(): Long

/** The desktop app (macOS, Windows, Linux) — wide windows get the sidebar layout, phone-only settings are hidden. */
expect val isDesktop: Boolean

/** Whether Compose blur (RenderEffect) works here — Android 12+, and always on desktop. */
expect val supportsRenderEffectBlur: Boolean

/** Whether the device has a working internet connection right now (best guess; true when unknown). */
expect fun isOnline(): Boolean

/** Handles "back": Android's back gesture/button; Esc on desktop. */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * "Back" that follows the finger: on Android 14+ [onProgress] gets the back swipe's progress
 * (0–1) as it happens, then [onBack] if it's let go or [onCancel] if it's taken back. Elsewhere
 * (a button press, Esc on desktop) it's a plain [onBack].
 */
@Composable
expect fun PredictiveBackHandler(enabled: Boolean = true, onProgress: (Float) -> Unit, onCancel: () -> Unit, onBack: () -> Unit)

/** Right-click with a mouse (desktop); nothing on a phone. */
expect fun androidx.compose.ui.Modifier.onSecondaryClick(onClick: () -> Unit): androidx.compose.ui.Modifier

/** Opens [url] in the app that handles it (browser, Instagram…); shows [failMessage] if nothing can. */
expect fun openUrl(url: String, failMessage: String)

/** Donating by UPI: copy the ID and open a UPI app on phones; on a computer, show how to pay it from a phone. */
expect fun donateViaUpi(upiId: String)
