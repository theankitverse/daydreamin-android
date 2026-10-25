package com.daydreamin.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daydreamin.app.data.download.DownloadInfo
import com.daydreamin.app.data.download.DownloadStatus
import com.daydreamin.app.data.download.Downloads
import com.daydreamin.app.data.model.Song
import kotlinx.coroutines.launch

/** This song's download, if it has one — updates as it progresses. */
@Composable
fun rememberDownload(song: Song): DownloadInfo? {
    val items by Downloads.items.collectAsState()
    return items[song.playId]
}

/** Starts, cancels or removes [song]'s download, saying what happened. */
fun toggleDownload(song: Song, info: DownloadInfo?, scope: kotlinx.coroutines.CoroutineScope) {
    when (info?.status) {
        null -> {
            Downloads.download(song)
            Toaster.show("Downloading “${song.title}”")
        }
        DownloadStatus.FAILED -> {
            Downloads.download(song)
            Downloads.retryFailed()
            Toaster.show("Trying “${song.title}” again")
        }
        else -> scope.launch {
            val keptBy = Downloads.remove(song)
            Toaster.show(
                when {
                    keptBy != null -> "Still downloaded with “$keptBy”"
                    info.status == DownloadStatus.DONE -> "Removed download"
                    else -> "Download cancelled"
                },
            )
        }
    }
}

/**
 * The download control: an arrow to download, a filling ring while it downloads, a tick once it's
 * on the phone, and a warning to try again if it failed. Tapping a tick or a ring removes it.
 */
@Composable
fun DownloadButton(song: Song, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val info = rememberDownload(song)
    val scope = rememberCoroutineScope()
    val label = when (info?.status) {
        null -> "Download"
        DownloadStatus.DONE -> "Downloaded — remove download"
        DownloadStatus.FAILED -> "Download failed — try again"
        else -> "Downloading — cancel"
    }
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = label }
            .pressable(onClick = { toggleDownload(song, info, scope) }),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = info?.status,
            transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.7f)) togetherWith fadeOut(tween(120)) },
            label = "downloadState",
        ) { status ->
            when (status) {
                null -> Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(24.dp))
                DownloadStatus.DONE -> Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                DownloadStatus.FAILED -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFFFB4AB), modifier = Modifier.size(22.dp))
                else -> {
                    val progress by animateFloatAsState((info?.percent ?: 0f) / 100f, tween(400), label = "downloadProgress")
                    Box(contentAlignment = Alignment.Center) {
                        if (status == DownloadStatus.DOWNLOADING && progress > 0f) {
                            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(20.dp), color = Color.White, trackColor = Color.White.copy(alpha = 0.18f), strokeWidth = 2.dp)
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White.copy(alpha = 0.7f), trackColor = Color.White.copy(alpha = 0.12f), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

/** A small mark beside a song in a list: a tick once it's downloaded, an arrow while it's downloading. */
@Composable
fun DownloadedMark(song: Song, modifier: Modifier = Modifier) {
    val info = rememberDownload(song) ?: return
    val icon = when (info.status) {
        DownloadStatus.DONE -> Icons.Rounded.DownloadDone
        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> Icons.Rounded.Download
        DownloadStatus.FAILED -> return
    }
    Icon(
        icon,
        contentDescription = if (info.status == DownloadStatus.DONE) "Downloaded" else "Downloading",
        tint = Color.White.copy(alpha = if (info.status == DownloadStatus.DONE) 0.7f else 0.45f),
        modifier = modifier.size(14.dp),
    )
}
