package com.daydreamin.app.data.download

import com.daydreamin.app.data.model.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

enum class DownloadStatus { QUEUED, DOWNLOADING, DONE, FAILED }

data class DownloadInfo(val song: Song, val status: DownloadStatus, val percent: Float, val bytes: Long)

/** Songs kept on the device for offline listening; each platform stores them its own way. */
expect object Downloads {
    /** Every song that's downloaded or on its way, by playId. */
    val items: StateFlow<Map<String, DownloadInfo>>

    fun isDownloaded(playId: String): Boolean
    fun downloadedSong(playId: String): Song?
    fun bytesUsed(): Long
    fun download(song: Song): Job
    /** Returns the name of a playlist that still keeps it downloaded, if one does. */
    suspend fun remove(song: Song): String?
    fun retryFailed()
    suspend fun removeAll()
}
