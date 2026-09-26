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

    val darkMode: StateFlow<Boolean> = prefs.darkMode.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val dynamicTheming: StateFlow<Boolean> = prefs.dynamicTheming.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val streamingQuality: StateFlow<String> = prefs.streamingQuality.stateIn(viewModelScope, SharingStarted.Eagerly, "Auto")
    val crossfadeSeconds: StateFlow<Int> = prefs.crossfadeSeconds.stateIn(viewModelScope, SharingStarted.Eagerly, 3)
    val audioNormalization: StateFlow<Boolean> = prefs.audioNormalization.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val downloadWifiOnly: StateFlow<Boolean> = prefs.downloadWifiOnly.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val equalizerPreset: StateFlow<String> = prefs.equalizerPreset.stateIn(viewModelScope, SharingStarted.Eagerly, EqPreset.OFF.label)

    private val _extractorState = MutableStateFlow(ExtractorState.UNKNOWN)
    val extractorState: StateFlow<ExtractorState> = _extractorState

    /** Does a trivial on-device YouTube search — the extractor breaks when YouTube changes internals, so this is a real diagnostic, not decoration. */
    fun testExtractor() {
        _extractorState.value = ExtractorState.CHECKING
        viewModelScope.launch {
            val results = YouTubeExtractorService.search("test", limit = 1)
            _extractorState.value = if (results.isNotEmpty()) ExtractorState.OK else ExtractorState.FAILED
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

    fun setDarkMode(v: Boolean) = viewModelScope.launch { prefs.setDarkMode(v) }
    fun setDynamicTheming(v: Boolean) = viewModelScope.launch { prefs.setDynamicTheming(v) }
    fun setStreamingQuality(v: String) = viewModelScope.launch { prefs.setStreamingQuality(v) }
    fun setCrossfadeSeconds(v: Int) = viewModelScope.launch { prefs.setCrossfadeSeconds(v) }
    fun setAudioNormalization(v: Boolean) = viewModelScope.launch { prefs.setAudioNormalization(v) }
    fun setDownloadWifiOnly(v: Boolean) = viewModelScope.launch { prefs.setDownloadWifiOnly(v) }
    fun setEqualizerPreset(v: String) = viewModelScope.launch { prefs.setEqualizerPreset(v) }
}
