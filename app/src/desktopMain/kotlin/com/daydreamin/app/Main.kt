package com.daydreamin.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.daydreamin.app.data.Region
import com.daydreamin.app.data.prefs.AppPreferences
import com.daydreamin.app.data.repository.toSong
import com.daydreamin.app.data.taste.TasteProfile
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import com.daydreamin.app.platform.elapsedRealtime
import com.daydreamin.app.resources.Res
import com.daydreamin.app.resources.splash_logo
import com.daydreamin.app.ui.navigation.AppNavHost
import com.daydreamin.app.ui.screens.onboarding.OnboardingScreen
import com.daydreamin.app.ui.theme.DaydreaminTheme
import com.daydreamin.app.ui.theme.accentByName
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import java.io.File

/** Where the app keeps its files: the usual per-user app-data folder on each system. */
private fun appDataDir(): File {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    val dir = when {
        "mac" in os -> File(home, "Library/Application Support/Daydreamin")
        "win" in os -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "Daydreamin")
        else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "daydreamin")
    }
    return dir.apply { mkdirs() }
}

private fun platformName(): String = System.getProperty("os.name").lowercase().let {
    when {
        "mac" in it -> "macOS"
        "win" in it -> "Windows"
        else -> "Linux"
    }
}

fun main(args: Array<String>) {
    val startedAt = elapsedRealtime()
    // --snapshot also writes the app's log next to the pictures (on Windows the app has no console).
    args.indexOf("--snapshot").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }?.let { dir ->
        File(dir).mkdirs()
        val log = java.io.FileOutputStream(File(dir, "app.log"), true)
        fun tee(to: java.io.PrintStream) = java.io.PrintStream(object : java.io.OutputStream() {
            override fun write(b: Int) { to.write(b); log.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { to.write(b, off, len); log.write(b, off, len); log.flush() }
        }, true)
        System.setOut(tee(System.out))
        System.setErr(tee(System.err))
    }
    AppInfo.versionName = System.getProperty("daydreamin.version") ?: "dev"
    AppInfo.versionCode = System.getProperty("daydreamin.versionCode")?.toIntOrNull() ?: 0
    // Desktop releases have their own file (Android's version.json describes the APK), with the same mirrors.
    AppInfo.updateUrl = "https://raw.githubusercontent.com/theankitverse/daydreamin-android/main/desktop-version.json"
    AppInfo.updateMirrors = listOf("https://cdn.jsdelivr.net/gh/theankitverse/daydreamin-android@main/desktop-version.json")
    AppInfo.platformName = platformName()

    val dataDir = appDataDir()
    val store = PreferenceDataStoreFactory.create { File(dataDir, "daydreamin.preferences_pb") }
    TasteProfile.init(dataDir)
    AppGraph.init(dataDir, AppPreferences(store, dataDir), startedAt)
    Region.init(null)
    YouTubeExtractorService.init(Region.country)
    // The desktop player decodes Opus itself (YouTube's best audio, the same stream Android plays).
    YouTubeExtractorService.opusOnly = true
    com.daydreamin.app.data.download.Downloads.init()
    com.daydreamin.app.player.PlayerController.ensureConnected()
    // The system's media controls: Now Playing on macOS, MPRIS on Linux, media keys on Windows.
    com.daydreamin.app.system.SystemMedia.start()
    // A development run (no version) never offers updates.
    if (AppInfo.versionCode > 0) com.daydreamin.app.data.update.UpdateChecker.start(AppGraph.prefs, AppGraph.appScope)
    AppGraph.startHomeFeed()

    val snapshotDir = args.indexOf("--snapshot").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    if (snapshotDir != null) {
        // --onboarded: skip first-run setup, to see Home and the rest with whatever library is here.
        if ("--onboarded" in args) kotlinx.coroutines.runBlocking { AppGraph.prefs.setOnboarded() }
        // --play "<search>": something playing, to see the player bar and Now Playing.
        args.indexOf("--play").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }?.let { query ->
            kotlinx.coroutines.runBlocking {
                YouTubeExtractorService.search(query, limit = 1).firstOrNull()?.let { com.daydreamin.app.player.PlayerController.playSong(it.toSong()) }
            }
        }
        // --pages home,settings,now_playing (routes; default: home)
        val pages = args.indexOf("--pages").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }?.split(',') ?: listOf("home")
        saveSnapshots(File(snapshotDir), pages, quick = "--quick" in args) { page -> DaydreaminRoot(startRoute = page) }
        kotlin.system.exitProcess(0)
    }

    val saved = WindowMemory.load(dataDir)
    application {
        val windowState = rememberWindowState(
            placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
            // Only a place that's on one of today's screens (a monitor may have been unplugged since).
            position = saved?.takeIf { WindowMemory.isOnScreen(it) }?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
            size = saved?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(1200.dp, 820.dp),
        )
        Window(
            onCloseRequest = {
                WindowMemory.save(dataDir, windowState)
                com.daydreamin.app.player.PlayerController.disconnect()
                com.daydreamin.app.system.SystemMedia.stop()
                exitApplication()
            },
            title = "Daydreamin",
            icon = painterResource(Res.drawable.splash_logo),
            state = windowState,
            onKeyEvent = ::handleShortcut,
        ) {
            // Narrower than a phone, the phone layout itself would start to squeeze.
            androidx.compose.runtime.LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(380, 560) }
            DaydreaminRoot()
        }
    }
}

/**
 * Keyboard shortcuts. These only see keys nothing else wanted — typing in the search box, or Space
 * on a focused button, never gets here.
 */
internal fun handleShortcut(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return false
    val command = event.isMetaPressed || event.isCtrlPressed // Cmd on a Mac, Ctrl elsewhere
    val player = com.daydreamin.app.player.PlayerController
    return when {
        event.key == Key.Spacebar && !command -> { player.togglePlayPause(); true }
        event.key == Key.DirectionRight && command -> { player.next(); true }
        event.key == Key.DirectionLeft && command -> { player.previous(); true }
        event.key == Key.DirectionRight && event.isShiftPressed -> { seekBy(10_000); true }
        event.key == Key.DirectionLeft && event.isShiftPressed -> { seekBy(-10_000); true }
        event.key == Key.F && command -> { com.daydreamin.app.ui.navigation.NavCommands.openSearch?.invoke(); true }
        event.key == Key.Escape -> com.daydreamin.app.platform.DesktopBack.handle()
        else -> false
    }
}

private fun seekBy(deltaMs: Long) {
    val player = com.daydreamin.app.player.PlayerController
    val p = player.progress.value
    if (player.meta.value.currentSong == null) return
    val target = (p.positionMs + deltaMs).coerceIn(0L, if (p.durationMs > 0) p.durationMs - 1_000 else Long.MAX_VALUE)
    player.seekTo(target)
}

/** The whole app: first-run setup until it's done, then Home and everything reachable from it. */
@androidx.compose.runtime.Composable
fun DaydreaminRoot(startRoute: String = com.daydreamin.app.ui.navigation.Dest.HOME) {
    val prefs = AppGraph.prefs
    val accentName by prefs.accentName.collectAsState(initial = "Violet")
    // null until the store has been read — nothing is shown until we know.
    val onboarded by prefs.onboarded.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    DaydreaminTheme(accent = accentByName(accentName)) {
        Box(Modifier.fillMaxSize().background(com.daydreamin.app.ui.theme.BgBase)) {
            if (onboarded == true) AppNavHost(startRoute)
            if (onboarded == false) OnboardingScreen(onDone = { scope.launch { prefs.setOnboarded() } })
        }
    }
}
