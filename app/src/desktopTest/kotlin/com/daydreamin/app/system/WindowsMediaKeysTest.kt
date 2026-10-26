package com.daydreamin.app.system

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/** Windows only: pressing the media keys (sent through the system, as a keyboard would) reaches the app. */
class WindowsMediaKeysTest {
    private fun press(vk: Int) {
        fun send(up: Boolean) {
            val input = WinUser.INPUT()
            input.type = WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
            input.input.setType("ki")
            input.input.ki.wVk = WinDef.WORD(vk.toLong())
            input.input.ki.dwFlags = WinDef.DWORD(if (up) 2L else 0L) // KEYEVENTF_KEYUP
            User32.INSTANCE.SendInput(WinDef.DWORD(1), arrayOf(input), input.size())
        }
        send(false); send(true)
        Thread.sleep(400)
    }

    @Test fun mediaKeysReachTheApp() {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        val got = CopyOnWriteArrayList<String>()
        MediaCommands.onCommand = { got += it }
        val keys = WindowsMediaKeys()
        try {
            press(0xB3) // play/pause
            press(0xB0) // next
            press(0xB1) // previous
            assertEquals(listOf("toggle", "next", "previous"), got.toList())
            println("[windows-test] media keys reached the app: $got")
        } finally {
            keys.close()
            MediaCommands.onCommand = null
        }
    }
}
