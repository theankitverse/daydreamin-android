package com.daydreamin.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.daydreamin.app.data.Region
import com.daydreamin.app.data.prefs.AppPreferences
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
    AppInfo.versionName = System.getProperty("daydreamin.version") ?: "dev"
    AppInfo.platformName = platformName()

    val dataDir = appDataDir()
    val store = PreferenceDataStoreFactory.create { File(dataDir, "daydreamin.preferences_pb") }
    TasteProfile.init(dataDir)
    AppGraph.init(dataDir, AppPreferences(store, dataDir), startedAt)
    Region.init(null)
    YouTubeExtractorService.init(Region.country)
    // No update check yet: version.json describes the Android app. Desktop gets its own with its installers.
    AppGraph.startHomeFeed()

    val snapshotDir = args.indexOf("--snapshot").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    if (snapshotDir != null) {
        // --onboarded: skip first-run setup, to see Home and the rest with whatever library is here.
        if ("--onboarded" in args) kotlinx.coroutines.runBlocking { AppGraph.prefs.setOnboarded() }
        saveSnapshots(File(snapshotDir)) { DaydreaminRoot() }
        kotlin.system.exitProcess(0)
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Daydreamin",
            icon = painterResource(Res.drawable.splash_logo),
            state = rememberWindowState(size = DpSize(1200.dp, 820.dp)),
        ) {
            DaydreaminRoot()
        }
    }
}

/** The whole app: first-run setup until it's done, then Home and everything reachable from it. */
@androidx.compose.runtime.Composable
fun DaydreaminRoot() {
    val prefs = AppGraph.prefs
    val accentName by prefs.accentName.collectAsState(initial = "Violet")
    // null until the store has been read — nothing is shown until we know.
    val onboarded by prefs.onboarded.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    DaydreaminTheme(accent = accentByName(accentName)) {
        Box(Modifier.fillMaxSize()) {
            if (onboarded == true) AppNavHost()
            if (onboarded == false) OnboardingScreen(onDone = { scope.launch { prefs.setOnboarded() } })
        }
    }
}
