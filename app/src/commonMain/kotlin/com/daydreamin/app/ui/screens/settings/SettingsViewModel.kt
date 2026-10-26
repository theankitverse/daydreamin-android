package com.daydreamin.app.ui.screens.settings

import com.daydreamin.app.platform.AutoBackup
import com.daydreamin.app.platform.DeviceStorage
import com.daydreamin.app.platform.PickedFile
import com.daydreamin.app.platform.platformContext
import com.daydreamin.app.platform.restoreLibrary
import com.daydreamin.app.platform.writeText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.prefs.BackupFormatException
import com.daydreamin.app.data.prefs.ImportSummary
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

enum class ExtractorState { UNKNOWN, CHECKING, OK, FAILED }

class SettingsViewModel : ViewModel() {
    private val prefs = AppGraph.prefs

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

    fun refreshStorage() {
        viewModelScope.launch {
            _storage.value = withContext(Dispatchers.IO) {
                val images = coil3.SingletonImageLoader.get(platformContext)
                Storage(DeviceStorage.audioCacheBytes(), DeviceStorage.audioCacheCapBytes, images.diskCache?.size ?: 0L)
            }
        }
    }

    /** Artwork only — re-downloads as screens need it. Never touches audio, the library or the queue. */
    fun clearArtworkCache() {
        viewModelScope.launch {
            val images = coil3.SingletonImageLoader.get(platformContext)
            withContext(Dispatchers.IO) { images.diskCache?.clear() }
            images.memoryCache?.clear()
            refreshStorage()
        }
    }

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus

    fun exportLibrary(file: PickedFile) {
        viewModelScope.launch {
            _backupStatus.value = try {
                val backup = prefs.exportLibrary()
                file.writeText(backup.toJson())
                "Exported ${count(backup.likedSongs.size, "liked song")}, ${count(backup.playlists.size, "playlist")} and ${count(backup.history.size, "recently played song")}."
            } catch (e: IOException) {
                "Couldn't write the file: ${e.message ?: "unknown error"}"
            } catch (e: SecurityException) {
                "Couldn't write the file: permission denied."
            }
        }
    }

    fun importLibrary(file: PickedFile) {
        viewModelScope.launch {
            _backupStatus.value = try {
                val result = restoreLibrary(prefs, file, keepBackingUpHere = false)
                describeImport(result.summary)
            } catch (e: BackupFormatException) {
                e.message ?: "That doesn't look like a Daydreamin library backup."
            } catch (e: IOException) {
                "Couldn't read the file: ${e.message ?: "unknown error"}"
            } catch (e: SecurityException) {
                "Couldn't read the file: permission denied."
            }
        }
    }

    val autoBackupAt: StateFlow<Long> = prefs.autoBackupAt.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    /** "Back up now" in Settings. */
    fun backupNow() {
        viewModelScope.launch {
            val ok = runCatching { AutoBackup.writeNow(prefs) }.getOrDefault(false)
            com.daydreamin.app.ui.components.Toaster.show(if (ok) "Backed up to Download/Daydreamin" else "Nothing to back up yet")
        }
    }

    private fun describeImport(summary: ImportSummary): String {
        if (summary.isEmpty) return "Nothing new — everything in that backup is already in your library."
        val parts = buildList {
            if (summary.likedAdded > 0) add(count(summary.likedAdded, "liked song"))
            if (summary.playlistsAdded > 0) add(count(summary.playlistsAdded, "playlist"))
            if (summary.playlistSongsAdded > 0) add(count(summary.playlistSongsAdded, "playlist song"))
            if (summary.historyAdded > 0) add(count(summary.historyAdded, "recently played song"))
        }
        return "Imported ${parts.joinToString(", ")}."
    }

    fun setAudioNormalization(v: Boolean) = viewModelScope.launch { prefs.setAudioNormalization(v) }
    fun setDownloadWifiOnly(v: Boolean) = viewModelScope.launch { prefs.setDownloadWifiOnly(v) }
    fun setEqualizerPreset(v: String) = viewModelScope.launch { prefs.setEqualizerPreset(v) }

    val usageStats: StateFlow<Boolean> = prefs.usageStatsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setUsageStats(v: Boolean) = viewModelScope.launch { prefs.setUsageStatsEnabled(v) }

    val downloadLiked: StateFlow<Boolean> = prefs.downloadLiked.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setDownloadLiked(v: Boolean) = viewModelScope.launch { prefs.setDownloadLiked(v) }
    fun removeAllDownloads() = viewModelScope.launch { com.daydreamin.app.data.download.Downloads.removeAll() }

    val crossfadeSeconds: StateFlow<Int> = prefs.crossfadeSeconds.stateIn(viewModelScope, SharingStarted.Eagerly, 6)
    fun setCrossfadeSeconds(v: Int) = viewModelScope.launch { prefs.setCrossfadeSeconds(v) }
}

private fun count(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"
