package com.daydreamin.app.data.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.daydreamin.app.MainActivity
import com.daydreamin.app.R

private const val CHANNEL_ID = "downloads"
private const val NOTIFICATION_ID = 2001

/**
 * Keeps downloads going with the app closed, with a progress notification ("Downloading 3 songs").
 * The work itself is done by [Downloads]' manager; this only lends it a foreground service.
 */
@UnstableApi
class DaydreaminDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.downloads_channel_name,
    R.string.downloads_channel_description,
) {
    private val notifications by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = Downloads.manager(this)

    /** None: unfinished downloads resume when the app is next opened. */
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val active = downloads.count { it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED }
        val message = if (active == 1) "Downloading 1 song" else "Downloading $active songs"
        return notifications.buildProgressNotification(this, R.drawable.ic_stat_download, open, message, downloads, notMetRequirements)
    }

    /**
     * Android 15+ limits this kind of service to 6 hours a day and kills apps that run over. Pause
     * cleanly instead; downloads pick up again the next time the app is opened.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w("Downloads", "daily background download time used up — pausing until the app is next opened")
        Downloads.manager(this).pauseDownloads()
        androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
