package com.daydreamin.app.data.prefs

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** A real backup is a few hundred KB even for thousands of songs (plus a small photo); anything far past this isn't one. */
private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024

data class RestoreResult(val summary: ImportSummary, val profileApplied: Boolean)

/**
 * Restores a backup file the user picked: merges its library in (never removing anything), applies
 * its profile if this device hasn't been set up yet, and — when [keepBackingUpHere] — keeps the
 * automatic backup going into that same file. Shared by first-run setup and Settings → Import.
 *
 * Throws [BackupFormatException] (message fit to show) for a file that isn't a usable backup,
 * [IOException] / [SecurityException] when it can't be read.
 */
suspend fun restoreLibrary(context: Context, prefs: AppPreferences, uri: Uri, keepBackingUpHere: Boolean): RestoreResult {
    val text = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no input stream")
        input.use {
            // Read one chunk past the cap so an oversized file is detected, not silently truncated
            // into "invalid JSON". (Manual loop: InputStream.readNBytes is Android 13+ only.)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_BACKUP_BYTES) throw BackupFormatException("That file is too large to be a Daydreamin backup.")
            }
            out.toString(Charsets.UTF_8.name())
        }
    }
    val backup = parseLibraryBackup(text)
    val summary = prefs.importLibrary(backup)
    val profileApplied = prefs.applyProfileIfFresh(backup.profile)
    if (keepBackingUpHere) AutoBackup.adopt(context, prefs, uri)
    return RestoreResult(summary, profileApplied)
}
