package com.daydreamin.app

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference

/**
 * The window's frame, made to belong to the app: on a Mac the content runs edge to edge under a
 * transparent title bar (the window buttons sit over the app's own top bar); on Windows the title
 * bar is switched to dark. Linux keeps its desktop's own frame.
 */
object WindowChrome {
    private val os = System.getProperty("os.name").lowercase()
    val isMac = "mac" in os
    private val isWindows = "win" in os

    /** Set before the window first shows (the Mac properties must be in place by then). */
    fun prepare(window: ComposeWindow) {
        if (isMac) {
            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
        }
    }

    /** How far the Mac's window buttons reach into the top bar. */
    fun titleBarInset(window: ComposeWindow): Dp = if (isMac) 72.dp else 0.dp

    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int
    }

    fun applyDarkTitleBar(window: ComposeWindow) {
        if (isMac) prepare(window)
        if (!isWindows) return
        runCatching {
            val hwnd = WinDef.HWND(Native.getComponentPointer(window))
            val dwm = Native.load("dwmapi", Dwm::class.java)
            // DWMWA_USE_IMMERSIVE_DARK_MODE: 20 on Windows 10 20H1 and later, 19 before that.
            if (dwm.DwmSetWindowAttribute(hwnd, 20, IntByReference(1), 4) != 0) dwm.DwmSetWindowAttribute(hwnd, 19, IntByReference(1), 4)
        }
    }
}
