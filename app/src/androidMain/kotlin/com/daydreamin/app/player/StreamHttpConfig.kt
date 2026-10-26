package com.daydreamin.app.player

import androidx.media3.datasource.DefaultHttpDataSource

internal const val STREAM_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

/**
 * The resolved googlevideo.com URLs previously went through our backend's proxy, which added
 * these headers for us. Now we're the only client, so every HTTP data source that fetches a
 * stream needs to send them itself — shared by [PlaybackService]'s live playback pipeline and
 * [LikedSongsPrecacher]'s background one, so the two can't drift out of sync.
 */
internal fun buildStreamHttpDataSourceFactory(): DefaultHttpDataSource.Factory =
    DefaultHttpDataSource.Factory()
        .setUserAgent(STREAM_USER_AGENT)
        .setDefaultRequestProperties(mapOf("Referer" to "https://www.youtube.com/"))
        .setAllowCrossProtocolRedirects(true)
