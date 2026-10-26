package com.daydreamin.app.system

import com.daydreamin.app.platform.Log
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import java.util.concurrent.CountDownLatch

private const val TAG = "WindowsMediaKeys"

/**
 * The keyboard's media keys on Windows (play/pause, next, previous, stop), taken as system-wide
 * hotkeys so they work while another window has focus. The hotkeys belong to the thread that
 * registers them, so one thread registers them and waits for their messages.
 */
class WindowsMediaKeys : MediaBridge {
    private val keys = mapOf(
        1 to (0xB3 to { MediaCommands.toggle() }),   // VK_MEDIA_PLAY_PAUSE
        2 to (0xB0 to { MediaCommands.next() }),     // VK_MEDIA_NEXT_TRACK
        3 to (0xB1 to { MediaCommands.previous() }), // VK_MEDIA_PREV_TRACK
        4 to (0xB2 to { MediaCommands.pause() }),    // VK_MEDIA_STOP
    )
    @Volatile private var threadId = 0

    init {
        val ready = CountDownLatch(1)
        Thread({
            threadId = com.sun.jna.platform.win32.Kernel32.INSTANCE.GetCurrentThreadId()
            val registered = keys.count { (id, key) ->
                // MOD_NOREPEAT: holding a key down doesn't fire it again and again.
                User32.INSTANCE.RegisterHotKey(null, id, 0x4000, key.first).also { ok ->
                    if (!ok) Log.w(TAG, "media key ${key.first} is taken by another app")
                }
            }
            Log.i(TAG, "$registered of ${keys.size} media keys registered")
            ready.countDown()
            val msg = WinUser.MSG()
            while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WinUser.WM_HOTKEY) {
                    val action = keys[msg.wParam.toInt()]?.second
                    runCatching { action?.invoke() }.onFailure { Log.w(TAG, "media key failed: ${it.message}") }
                }
            }
            keys.keys.forEach { User32.INSTANCE.UnregisterHotKey(null, it) }
        }, "media-keys").apply { isDaemon = true; start() }
        ready.await()
    }

    // Windows' own media overlay (the one with the song name) isn't reachable from here; the keys are.
    override fun update(state: NowPlaying?) {}

    override fun close() {
        if (threadId != 0) User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT, null, null)
    }
}
