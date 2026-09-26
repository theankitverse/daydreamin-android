package com.daydreamin.app.ui.screens.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.prefs.BackupFormatException
import com.daydreamin.app.data.prefs.ImportSummary
import com.daydreamin.app.data.prefs.parseLibraryBackup
import com.daydreamin.app.data.prefs.toJson
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import com.daydreamin.app.player.EqPreset
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** A real backup is a few hundred KB even for thousands of songs; anything far past this isn't one. */
private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024

enum class ExtractorState { UNKNOWN, CHECKING, OK, FAILED }

class SettingsViewModel : ViewModel() {
    private val prefs = DaydreaminApp.instance.prefs

    // Dark mode, dynamic theming, streaming quality and the "fade out near track end" seconds
    // used to be exposed here, but nothing in the app ever read them (the app is always dark, the
    // extractor always takes the best stream, and there's no fade) — so they're no longer shown.
    // Their stored values are left alone.
    val audioNormalization: StateFlow<Boolean> = prefs.audioNormalization.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val downloadWifiOnly: StateFlow<Boolean> = prefs.downloadWifiOnly.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val equalizerPreset: StateFlow<String> = prefs.equalizerPreset.stateIn(viewModelScope, SharingStarted.Eagerly, EqPreset.OFF.label)

    private val _extractorState = MutableStateFlow(ExtractorState.UNKNOWN)
    val extractorState: StateFlow<ExtractorState> = _extractorState

    /** Does a trivial on-device YouTube search — the extractor breaks when YouTube changes internals, so this is a real diagnostic, not decoration. */
    fun testExtractor() {
        _extractorState.value = ExtractorState.CHECKING
        viewModelScope.launch {
            // A real song, not a word like "test": YouTube Music answers generic words with a page
            // layout that isn't a song list, which made this check report "broken" while search
            // itself worked fine. Falls back to regular YouTube search, which playback also uses.
            val probe = "Shape of You Ed Sheeran"
            val ok = YouTubeExtractorService.search(probe, limit = 1).isNotEmpty() ||
                YouTubeExtractorService.searchBroad(probe, limit = 1).isNotEmpty()
            _extractorState.value = if (ok) ExtractorState.OK else ExtractorState.FAILED
        }
    }

    /** What's actually on disk: offline audio (with its fixed cap) and cached artwork. */
    data class Storage(val audioBytes: Long, val audioCapBytes: Long, val artworkBytes: Long)

    private val _storage = MutableStateFlow<Storage?>(null)
    val storage: StateFlow<Storage?> = _storage

    fun refreshStorage(context: android.content.Context) {
        viewModelScope.launch {
            _storage.value = withContext(Dispatchers.IO) {
                // Measured from the files themselves, so reading it never touches the player's live cache.
                val audio = java.io.File(context.cacheDir, "audio_cache").walkTopDown().filter { it.isFile }.sumOf { it.length() }
                Storage(audio, com.daydreamin.app.player.AudioDiskCache.MAX_CACHE_BYTES, context.imageLoader.diskCache?.size ?: 0L)
            }
        }
    }

    /** Artwork only — re-downloads as screens need it. Never touches audio, the library or the queue. */
    fun clearArtworkCache(context: android.content.Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { context.imageLoader.diskCache?.clear() }
            context.imageLoader.memoryCache?.clear()
            refreshStorage(context)
        }
    }

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus

    fun exportLibrary(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _backupStatus.value = try {
                val backup = prefs.exportLibrary()
                withContext(Dispatchers.IO) {
                    val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("no output stream")
                    stream.use { it.write(backup.toJson().toByteArray(Charsets.UTF_8)) }
                }
                "Exported ${backup.likedSongs.size} liked songs, ${backup.playlists.size} playlists, ${backup.history.size} history entries."
            } catch (e: IOException) {
                "Couldn't write the file: ${e.message ?: "unknown error"}"
            } catch (e: SecurityException) {
                "Couldn't write the file: permission denied."
            }
        }
    }

    fun importLibrary(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _backupStatus.value = try {
                val text = withContext(Dispatchers.IO) {
                    val stream = resolver.openInputStream(uri) ?: throw IOException("no input stream")
                    stream.use { input ->
                        // Read one byte past the cap so an oversized file is detected, not silently truncated into "invalid JSON".
                        val bytes = input.readNBytes(MAX_BACKUP_BYTES + 1)
                        if (bytes.size > MAX_BACKUP_BYTES) throw BackupFormatException("That file is too large to be a Daydreamin backup.")
                        String(bytes, Charsets.UTF_8)
                    }
                }
                describeImport(prefs.importLibrary(parseLibraryBackup(text)))
            } catch (e: BackupFormatException) {
                e.message ?: "That doesn't look like a Daydreamin library backup."
            } catch (e: IOException) {
                "Couldn't read the file: ${e.message ?: "unknown error"}"
            } catch (e: SecurityException) {
                "Couldn't read the file: permission denied."
            }
        }
    }

    private fun describeImport(summary: ImportSummary): String {
        if (summary.isEmpty) return "Nothing new — everything in that backup is already in your library."
        val parts = buildList {
            if (summary.likedAdded > 0) add("${summary.likedAdded} liked songs")
            if (summary.playlistsAdded > 0) add("${summary.playlistsAdded} playlists")
            if (summary.playlistSongsAdded > 0) add("${summary.playlistSongsAdded} playlist songs")
            if (summary.historyAdded > 0) add("${summary.historyAdded} history entries")
        }
        return "Imported ${parts.joinToString(", ")}."
    }

    fun setAudioNormalization(v: Boolean) = viewModelScope.launch { prefs.setAudioNormalization(v) }
    fun setDownloadWifiOnly(v: Boolean) = viewModelScope.launch { prefs.setDownloadWifiOnly(v) }
    fun setEqualizerPreset(v: String) = viewModelScope.launch { prefs.setEqualizerPreset(v) }
}
