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

/** Whether Compose blur (RenderEffect) works here — Android 12+, and always on desktop. */
expect val supportsRenderEffectBlur: Boolean

/** Whether the device has a working internet connection right now (best guess; true when unknown). */
expect fun isOnline(): Boolean

/** Handles "back": Android's back gesture/button; Esc on desktop. */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** Opens [url] in the app that handles it (browser, Instagram…); shows [failMessage] if nothing can. */
expect fun openUrl(url: String, failMessage: String)

/** Donating by UPI: copy the ID and open a UPI app on phones; on a computer, show how to pay it from a phone. */
expect fun donateViaUpi(upiId: String)
