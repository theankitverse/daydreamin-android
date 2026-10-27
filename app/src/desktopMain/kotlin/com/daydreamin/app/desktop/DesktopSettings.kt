package com.daydreamin.app.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** How big the lyrics are, full window. */
enum class LyricsSize(val label: String, val fontSize: TextUnit) { SMALL("Small", 28.sp), MEDIUM("Medium", 36.sp), LARGE("Large", 46.sp) }

/** What the window's glass sits on. */
enum class Backdrop(val label: String) { ARTWORK("Song artwork"), BRAND("Daydreamin colours"), PLAIN("Plain dark") }

/**
 * Settings only the computer app has (the window, the tray, the lyrics view). They live with the
 * system's per-user preferences, next to the window's own state, rather than in the library data
 * the phone app shares.
 */
object DesktopSettings {
    private val store = java.util.prefs.Preferences.userRoot().node("daydreamin/settings")

    /** A setting: read by Compose like any state, saved as soon as it changes. */
    private class Stored<T>(initial: T, private val save: (T) -> Unit) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(initial)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) { state.value = value; save(value) }
    }

    private fun flag(key: String, default: Boolean) = Stored(store.getBoolean(key, default)) { store.putBoolean(key, it) }
    private inline fun <reified T : Enum<T>> choice(key: String, default: T) =
        Stored(runCatching { enumValueOf<T>(store.get(key, default.name)) }.getOrDefault(default)) { store.put(key, it.name) }

    var lyricsSize: LyricsSize by choice("lyricsSize", LyricsSize.MEDIUM)

    /** Apple Music's depth: the lines around the one being sung go soft. */
    var lyricsBlur: Boolean by flag("lyricsBlur", true)

    var backdrop: Backdrop by choice("backdrop", Backdrop.ARTWORK)

    /** The sidebar as icons only, however wide the window. */
    var compactSidebar: Boolean by flag("compactSidebar", false)

    /** An icon in the menu bar (Mac) / notification area (Windows, Linux) with the playback controls. */
    var trayIcon: Boolean by flag("trayIcon", true)

    /** Closing the window keeps the music playing (the tray icon brings it back); off: closing quits. */
    var keepPlayingWhenClosed: Boolean by flag("keepPlaying", false)

    /** A system notification when a new song starts while the window isn't in front. */
    var songNotifications: Boolean by flag("songNotifications", false)
}
