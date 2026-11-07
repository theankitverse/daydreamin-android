package com.daydreamin.app.system

import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.share.IncomingLinks
import com.daydreamin.app.desktop.shell.DesktopUi
import com.daydreamin.app.platform.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

/**
 * Opens daydreamin:// links, the ones a shared song's web page hands to the computer. A Mac delivers
 * them to the running app; Windows and Linux start the app again with the link as an argument, so
 * that copy leaves the link in a file for the one already running and quits.
 */
object DesktopLinks {
    private const val SCHEME = "daydreamin://"
    private var running: FileLock? = null

    private fun linkIn(args: Array<String>) = args.firstOrNull { it.startsWith(SCHEME) }
    private fun inbox(dir: File) = File(dir, "incoming-link")

    /** True when another copy of the app has taken the link and this one should quit. */
    fun handedOff(dir: File, args: Array<String>): Boolean {
        running = runCatching {
            FileChannel.open(File(dir, "instance.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).tryLock()
        }.getOrElse { if (it is OverlappingFileLockException) null else throw it }
        val link = linkIn(args) ?: return false
        if (running != null) return false
        inbox(dir).writeText(link)
        return true
    }

    fun start(dir: File, args: Array<String>) {
        IncomingLinks.showPlayer = {
            DesktopUi.expanded = true
            java.awt.EventQueue.invokeLater { java.awt.Window.getWindows().firstOrNull { it.isVisible }?.toFront() }
        }
        runCatching {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.APP_OPEN_URI)) desktop.setOpenURIHandler { IncomingLinks.open(it.uri.toString()) }
        }
        linkIn(args)?.let(IncomingLinks::open)
        if (running != null) AppGraph.appScope.launch(Dispatchers.IO) {
            val file = inbox(dir)
            while (true) {
                if (file.exists()) {
                    val link = runCatching { file.readText() }.getOrNull()
                    file.delete()
                    IncomingLinks.open(link)
                }
                delay(700)
            }
        }
        AppGraph.appScope.launch(Dispatchers.IO) { runCatching { register() }.onFailure { Log.w("Links", "couldn't register for links: ${it.message}") } }
    }

    /** Tells Windows or Linux this app opens daydreamin:// links. An installed Mac app says so in its Info.plist. */
    private fun register() {
        val app = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() } ?: return
        val os = System.getProperty("os.name").lowercase()
        when {
            "win" in os -> {
                val key = """HKCU\Software\Classes\daydreamin"""
                run("reg", "add", key, "/ve", "/d", "URL:Daydreamin", "/f")
                run("reg", "add", key, "/v", "URL Protocol", "/d", "", "/f")
                run("reg", "add", """$key\shell\open\command""", "/ve", "/d", "\"$app\" \"%1\"", "/f")
            }
            "mac" !in os -> {
                val folder = File(System.getProperty("user.home"), ".local/share/applications").apply { mkdirs() }
                File(folder, "daydreamin-links.desktop").writeText(
                    "[Desktop Entry]\nType=Application\nName=Daydreamin\nExec=\"$app\" %u\nNoDisplay=true\nMimeType=x-scheme-handler/daydreamin;\n",
                )
                run("xdg-mime", "default", "daydreamin-links.desktop", "x-scheme-handler/daydreamin")
            }
        }
    }

    private fun run(vararg command: String) {
        ProcessBuilder(*command).redirectErrorStream(true).start().apply { inputStream.readBytes() }.waitFor()
    }
}
