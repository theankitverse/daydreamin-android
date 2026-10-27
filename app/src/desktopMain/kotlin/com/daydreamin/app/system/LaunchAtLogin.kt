package com.daydreamin.app.system

import com.daydreamin.app.platform.Log
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/**
 * Starting Daydreamin when you sign in to the computer, the way each system expects: a login item
 * (LaunchAgent) on a Mac, the Run key on Windows, an autostart entry on Linux. Only the installed
 * app can do this — a development run has no app to start.
 */
object LaunchAtLogin {
    private val os = System.getProperty("os.name").lowercase()
    private const val ID = "com.daydreamin.desktop"

    /** The installed app's launcher (…/Daydreamin.app/Contents/MacOS/Daydreamin, Daydreamin.exe, bin/Daydreamin). */
    private val launcher: String? by lazy {
        ProcessHandle.current().info().command().orElse(null)?.takeUnless { cmd ->
            val name = File(cmd).name.lowercase()
            name == "java" || name == "java.exe" || name == "javaw.exe"
        }
    }

    val available: Boolean get() = launcher != null

    private val macAgent get() = File(System.getProperty("user.home"), "Library/LaunchAgents/$ID.plist")
    private val linuxEntry get() = File(System.getenv("XDG_CONFIG_HOME") ?: (System.getProperty("user.home") + "/.config"), "autostart/daydreamin.desktop")

    fun isEnabled(): Boolean = runCatching {
        when {
            "mac" in os -> macAgent.isFile
            "win" in os -> Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, "Daydreamin")
            else -> linuxEntry.isFile
        }
    }.getOrDefault(false)

    fun setEnabled(enabled: Boolean): Boolean = runCatching {
        val exe = launcher ?: return false
        when {
            "mac" in os -> {
                if (!enabled) { macAgent.delete(); return@runCatching }
                // Opened as the app (not its binary), so it's the same Daydreamin in the Dock.
                val app = exe.substringBefore("/Contents/MacOS/")
                macAgent.parentFile.mkdirs()
                macAgent.writeText(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                    <plist version="1.0">
                    <dict>
                        <key>Label</key><string>$ID</string>
                        <key>ProgramArguments</key><array><string>/usr/bin/open</string><string>-a</string><string>${xml(app)}</string></array>
                        <key>RunAtLoad</key><true/>
                    </dict>
                    </plist>
                    """.trimIndent(),
                )
            }
            "win" in os -> {
                if (enabled) Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, "Daydreamin", "\"$exe\"")
                else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, "Daydreamin")) Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, "Daydreamin")
            }
            else -> {
                if (!enabled) { linuxEntry.delete(); return@runCatching }
                linuxEntry.parentFile.mkdirs()
                linuxEntry.writeText("[Desktop Entry]\nType=Application\nName=Daydreamin\nExec=\"$exe\"\nX-GNOME-Autostart-enabled=true\n")
            }
        }
    }.onFailure { Log.w("LaunchAtLogin", "couldn't change it", it) }.isSuccess

    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
