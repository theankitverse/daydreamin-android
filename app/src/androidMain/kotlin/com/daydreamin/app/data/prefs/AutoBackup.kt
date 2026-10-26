package com.daydreamin.app.data.prefs

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AutoBackup"
const val AUTO_BACKUP_FOLDER = "Daydreamin"
const val AUTO_BACKUP_FILE = "daydreamin-library.json"

/**
 * Keeps a copy of the library (likes, playlists, history, profile) in the phone's public
 * `Download/Daydreamin` folder, rewritten a few seconds after anything in it changes.
 *
 * Why there: uninstalling an app deletes everything in its private storage, but files it saved
 * to Downloads stay. After a reinstall the app can't read that file on its own (Android 11+
 * doesn't let a new install see files an old one created), so first-run setup offers
 * "Restore from backup", which opens the system file picker right at this folder — one tap.
 *
 * Android 10+ only (older versions need a storage permission for this; there, the in-app
 * Export and Android's own Google backup still apply).
 */
object AutoBackup {
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @OptIn(FlowPreview::class)
    fun start(context: Context, prefs: AppPreferences, scope: CoroutineScope) {
        if (!isSupported) return
        val app = context.applicationContext
        scope.launch {
            // Anything a backup contains; the first value is the state at launch (nothing changed yet).
            combine(prefs.likedSongs, prefs.playlists, prefs.history, prefs.userName, prefs.avatarVersion) { l, p, h, n, a ->
                listOf(l.size, l.hashCode(), p.hashCode(), h.firstOrNull()?.playId, h.size, n, a)
            }
                .distinctUntilChanged()
                .drop(1)
                .debounce(4_000)
                .collect { runCatching { writeNow(app, prefs) }.onFailure { Log.w(TAG, "backup failed: ${it.message}") } }
        }
    }

    /** Writes the backup file now. Returns false (and writes nothing) when there's nothing worth keeping yet. */
    suspend fun writeNow(context: Context, prefs: AppPreferences): Boolean {
        if (!isSupported) return false
        val backup = prefs.exportLibrary()
        // A fresh install with nothing in it must never produce a file that looks like "the backup".
        if (backup.likedSongs.isEmpty() && backup.playlists.isEmpty() && backup.history.isEmpty()) return false
        val bytes = backup.toJson().toByteArray(Charsets.UTF_8)
        val resolver = context.contentResolver
        withContext(Dispatchers.IO) {
            val known = prefs.autoBackupUri.first()?.let(Uri::parse)
            val wrote = known != null && runCatching {
                resolver.openOutputStream(known, "wt")?.use { it.write(bytes) } != null
            }.getOrDefault(false)
            if (wrote) {
                prefs.setAutoBackup(known.toString(), System.currentTimeMillis())
            } else {
                val uri = createInDownloads(context) ?: error("couldn't create the backup file")
                resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("couldn't open the backup file")
                prefs.setAutoBackup(uri.toString(), System.currentTimeMillis())
            }
        }
        Log.d(TAG, "saved ${backup.likedSongs.size} liked, ${backup.playlists.size} playlists, ${backup.history.size} history")
        return true
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q) // only reached when isSupported
    private fun createInDownloads(context: Context): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, AUTO_BACKUP_FILE)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$AUTO_BACKUP_FOLDER")
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }

    /**
     * After restoring from a file the user picked, keep backing up into that same file (when the
     * picker granted write access) instead of starting a second copy next to it.
     */
    suspend fun adopt(context: Context, prefs: AppPreferences, uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val ok = runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }.isSuccess
        if (ok) prefs.setAutoBackup(uri.toString(), prefs.autoBackupAt.first())
    }

    /** Where to open the file picker for "Restore": the backup folder in Downloads. */
    val pickerStartUri: Uri
        get() = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2F$AUTO_BACKUP_FOLDER")
}
