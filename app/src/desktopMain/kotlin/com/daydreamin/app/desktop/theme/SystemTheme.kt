package com.daydreamin.app.desktop.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.daydreamin.app.platform.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Whether the computer is set to dark mode (macOS Appearance, Windows' app mode, GNOME's colour
 * scheme), looked up again every few seconds so the app follows when it's switched. Dark when
 * it can't tell.
 */
object SystemTheme {
    var dark by mutableStateOf(read())
        private set

    fun follow(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(3_000)
                val now = read()
                if (now != dark) launch(Dispatchers.Main) { dark = now }
            }
        }
    }

    private fun read(): Boolean = runCatching {
        val os = System.getProperty("os.name").lowercase()
        when {
            // "Dark" when dark; the key doesn't exist in light mode (the command then fails).
            "mac" in os -> run("defaults", "read", "-g", "AppleInterfaceStyle")?.trim() == "Dark"
            "win" in os -> com.sun.jna.platform.win32.Advapi32Util.registryGetIntValue(
                com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER,
                "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "AppsUseLightTheme",
            ) == 0
            else -> run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")?.contains("dark")
                ?: run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")?.contains("dark", ignoreCase = true)
                ?: true
        }
    }.onFailure { Log.w("Theme", "couldn't read the system's light/dark setting: ${it.message}") }.getOrDefault(true)

    /** The command's output, or null if it didn't succeed. */
    private fun run(vararg command: String): String? {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        if (!process.waitFor(2, TimeUnit.SECONDS)) { process.destroy(); return null }
        val out = process.inputStream.bufferedReader().readText()
        return out.takeIf { process.exitValue() == 0 }
    }
}
