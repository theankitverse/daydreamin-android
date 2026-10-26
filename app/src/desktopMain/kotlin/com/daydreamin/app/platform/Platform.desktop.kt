package com.daydreamin.app.platform

import androidx.compose.runtime.Composable
import com.daydreamin.app.ui.components.Toaster
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

actual object Log {
    actual fun d(tag: String, msg: String) = println("D/$tag: $msg")
    actual fun i(tag: String, msg: String) = println("I/$tag: $msg")
    actual fun w(tag: String, msg: String, tr: Throwable?) = System.err.println("W/$tag: $msg" + (tr?.let { " — $it" } ?: ""))
    actual fun e(tag: String, msg: String, tr: Throwable?) = System.err.println("E/$tag: $msg" + (tr?.let { " — $it" } ?: ""))
}

actual fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000

// A computer rarely has no connection at all, and a wrong "offline" is worse than a wrong "online".
actual fun isOnline(): Boolean = true

// Esc handling comes with the desktop layout (Phase 3); there's no system back button here.
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {}

actual val supportsRenderEffectBlur: Boolean = true

actual fun openUrl(url: String, failMessage: String) {
    val opened = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url)); true
        } else false
    }.getOrDefault(false)
    if (!opened) Toaster.show(failMessage)
}

/** A computer has no UPI app: copy the ID so it can be sent to a phone, and say how to pay it. */
actual fun donateViaUpi(upiId: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(upiId), null) }
    Toaster.show("UPI ID copied: $upiId. Pay it from any UPI app on your phone (“Pay UPI ID”)", durationMs = 8_000)
}
