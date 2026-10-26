package com.daydreamin.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.daydreamin.app.data.prefs.AppPreferences
import com.daydreamin.app.data.prefs.RestoreResult

/** A file the person chose in the system picker: a document Uri on Android, a path on desktop. */
expect class PickedFile

enum class PickKind {
    /** A library backup to read (Settings → Import). */
    OPEN_BACKUP,
    /** A library backup to read during first-run setup — opens where the automatic backup lives. */
    RESTORE_BACKUP,
    /** Where to write a library backup (Settings → Export). */
    SAVE_BACKUP,
    /** A photo for the profile picture. */
    PHOTO,
}

fun interface PickerLauncher {
    /** Opens the picker; [suggestedName] is the file name offered when saving. */
    fun launch(suggestedName: String)
}

/** The platform's file picker for [kind]; [onPicked] gets the choice, or null if it was cancelled. */
@Composable
expect fun rememberPicker(kind: PickKind, onPicked: (PickedFile?) -> Unit): PickerLauncher

expect suspend fun PickedFile.writeText(text: String)

/**
 * Restores a backup file: merges its library in (never removing anything), applies its profile if
 * this device hasn't been set up yet, and — when [keepBackingUpHere] — keeps the automatic backup
 * going into that same file where the platform supports it.
 */
expect suspend fun restoreLibrary(prefs: AppPreferences, file: PickedFile, keepBackingUpHere: Boolean): RestoreResult

/** Turns a picked photo into the stored avatar: center-cropped square, 512 px, JPEG. Null if it can't be read. */
expect suspend fun encodeAvatar(file: PickedFile): ByteArray?

/** Decodes a PNG/JPEG into something Compose can draw. */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/** A copy of the library kept outside the app, so uninstalling never loses it. */
expect object AutoBackup {
    val isSupported: Boolean
    /** "Back up now" in Settings; false when there's nothing to back up yet. */
    suspend fun writeNow(prefs: AppPreferences): Boolean
}

/** What the app's caches take up on disk. */
expect object DeviceStorage {
    /** Streamed songs kept for replay (not downloads). */
    fun audioCacheBytes(): Long
    val audioCacheCapBytes: Long
}

/** What Coil needs to reach its image loader outside a composable. */
expect val platformContext: coil3.PlatformContext
