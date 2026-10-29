package com.daydreamin.app.platform

import androidx.compose.foundation.onClick
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.daydreamin.app.ui.components.Toaster
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

actual object Log {
    private val clock = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private fun now() = java.time.LocalTime.now().format(clock)
    actual fun d(tag: String, msg: String) = println("${now()} D/$tag: $msg")
    actual fun i(tag: String, msg: String) = println("${now()} I/$tag: $msg")
    actual fun w(tag: String, msg: String, tr: Throwable?) = System.err.println("${now()} W/$tag: $msg" + (tr?.let { " — $it" } ?: ""))
    actual fun e(tag: String, msg: String, tr: Throwable?) = System.err.println("${now()} E/$tag: $msg" + (tr?.let { " — $it" } ?: ""))
}

actual fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000

// A computer rarely has no connection at all, and a wrong "offline" is worse than a wrong "online".
actual fun isOnline(): Boolean = true

/**
 * Esc is "back" on desktop. Screens register what back means for them (close the lyrics, the open
 * playlist…); the most recently shown one that's enabled gets it, otherwise the app goes back a page.
 */
object DesktopBack {
    class Entry { var enabled = false; var onBack: () -> Unit = {} }
    private val entries = mutableListOf<Entry>()

    fun add(e: Entry) = synchronized(entries) { entries.add(e) }
    fun remove(e: Entry) = synchronized(entries) { entries.remove(e) }

    /** True if something handled it. */
    fun handle(): Boolean {
        val entry = synchronized(entries) { entries.lastOrNull { it.enabled } }
        if (entry != null) { entry.onBack(); return true }
        return com.daydreamin.app.ui.navigation.NavCommands.goBack?.invoke() ?: false
    }
}

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val latest = rememberUpdatedState(onBack)
    val entry = remember { DesktopBack.Entry() }
    entry.enabled = enabled
    entry.onBack = { latest.value() }
    DisposableEffect(entry) {
        DesktopBack.add(entry)
        onDispose { DesktopBack.remove(entry) }
    }
}

@Composable
actual fun PredictiveBackHandler(enabled: Boolean, onProgress: (Float) -> Unit, onCancel: () -> Unit, onBack: () -> Unit) = BackHandler(enabled, onBack)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
actual fun androidx.compose.ui.Modifier.onSecondaryClick(onClick: () -> Unit): androidx.compose.ui.Modifier =
    this.onClick(matcher = androidx.compose.foundation.PointerMatcher.mouse(androidx.compose.ui.input.pointer.PointerButton.Secondary), onClick = onClick)

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

actual val isDesktop: Boolean = true
