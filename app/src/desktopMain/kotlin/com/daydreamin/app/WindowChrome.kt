package com.daydreamin.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daydreamin.app.platform.Log
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * The window's frame, made part of the app. On a Mac and on Windows the system's title bar goes and
 * the app's own top bar takes its place: the window buttons (the Mac's traffic lights; Windows'
 * minimise, maximise and close) sit inside it, and its empty space drags the window, double-clicks
 * to maximise and snaps like any title bar. That needs the JetBrains Runtime the app ships with;
 * on any other Java the system's frame stays, dark to match. Linux keeps its desktop's own frame.
 */
object WindowChrome {
    private val os = System.getProperty("os.name").lowercase()
    val isMac = "mac" in os
    private val isWindows = "win" in os

    /** Whether the app draws its own title bar on this system. */
    val customTitleBar: Boolean by lazy {
        (isMac || isWindows) && runCatching { JBR.isWindowDecorationsSupported() }.getOrDefault(false)
    }

    /** The app's title bar: how much room the window buttons take at each end. */
    class TitleBar internal constructor(private val bar: WindowDecorations.CustomTitleBar) {
        var start by mutableStateOf(bar.leftInset.dp)
            private set
        var end by mutableStateOf(bar.rightInset.dp)
            private set

        fun refresh() { start = bar.leftInset.dp; end = bar.rightInset.dp }

        /** true: the pointer is over one of the app's controls; false: over empty title bar. */
        fun forceHitTest(client: Boolean) = bar.forceHitTest(client)
    }

    /** Gives [window] the app's own title bar, [height] tall; null where the system's stays (see [systemFrame]). */
    fun install(window: ComposeWindow, height: Dp): TitleBar? {
        if (customTitleBar) {
            runCatching {
                val decorations = JBR.getWindowDecorations()
                val bar = decorations.createCustomTitleBar()
                bar.height = height.value
                // Windows' buttons: light icons on the dark app, a soft white wash under the pointer.
                bar.putProperty("controls.dark", true)
                bar.putProperty("controls.background.hovered", java.awt.Color(255, 255, 255, 24))
                bar.putProperty("controls.background.pressed", java.awt.Color(255, 255, 255, 40))
                decorations.setCustomTitleBar(window, bar)
                val titleBar = TitleBar(bar)
                // The buttons' room changes as the window shows, and leaves/enters full screen on a Mac.
                window.addComponentListener(object : java.awt.event.ComponentAdapter() {
                    override fun componentShown(e: java.awt.event.ComponentEvent) = titleBar.refresh()
                    override fun componentResized(e: java.awt.event.ComponentEvent) = titleBar.refresh()
                })
                Log.i("Window", "own title bar, buttons ${bar.leftInset} / ${bar.rightInset}")
                return titleBar
            }.onFailure { Log.w("Window", "own title bar unavailable", it) }
        }
        return null
    }

    /** How far the Mac's window buttons reach into the top bar, under the system's transparent title bar. */
    fun fallbackInset(): Dp = if (isMac) 72.dp else 0.dp

    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int
    }

    /** Where the system's frame stays: dark, and on a Mac with the content running under its transparent title bar. */
    fun systemFrame(window: ComposeWindow) {
        if (isMac) {
            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
        }
        if (!isWindows) return
        runCatching {
            val hwnd = WinDef.HWND(Native.getComponentPointer(window))
            val dwm = Native.load("dwmapi", Dwm::class.java)
            // DWMWA_USE_IMMERSIVE_DARK_MODE: 20 on Windows 10 20H1 and later, 19 before that.
            if (dwm.DwmSetWindowAttribute(hwnd, 20, IntByReference(1), 4) != 0) dwm.DwmSetWindowAttribute(hwnd, 19, IntByReference(1), 4)
            // The title bar only takes the change when it's next drawn: redraw it now, not on the first resize.
            User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0, 0x0001 or 0x0002 or 0x0004 or 0x0020) // NOSIZE NOMOVE NOZORDER FRAMECHANGED
        }
    }
}

/**
 * The title bar's hit test, decided for every pointer event: over something that takes the click
 * (a button, the search box — they consume the press) the app gets it; anywhere else the window
 * system does, so the empty bar drags the window, double-clicks to maximise, and snaps.
 */
fun Modifier.titleBarHitTest(bar: WindowChrome.TitleBar): Modifier = pointerInput(bar) {
    val context = currentCoroutineContext()
    awaitPointerEventScope {
        // From a press on a control until its release, everything stays the app's (dragging a slider out of it, say).
        var inControl = false
        while (context.isActive) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val consumed = event.changes.any { it.isConsumed }
            if (consumed || inControl) {
                if (event.type == PointerEventType.Press) inControl = true
                if (event.type == PointerEventType.Release) inControl = false
                bar.forceHitTest(true)
            } else {
                bar.forceHitTest(false)
            }
        }
    }
}
