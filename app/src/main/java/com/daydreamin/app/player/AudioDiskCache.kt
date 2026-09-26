package com.daydreamin.app.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * The one disk-backed LRU cache Media3 allows per directory per process — shared here so
 * [PlaybackService] can wrap playback in it. Unlike the in-memory stream-URL cache in
 * [com.daydreamin.app.data.youtube.YouTubeExtractorService] (which only remembers *where* to
 * fetch a song from, for ~20 minutes), this stores the actual audio bytes ExoPlayer streams.
 * Once a song has played once, replaying it — or hitting it again on a dead connection — is
 * served straight from disk instead of re-fetching from YouTube's CDN. Capped well under
 * typical free phone storage so it never becomes a "why is my phone full" complaint on its own;
 * least-recently-used entries are evicted automatically once the cap is hit.
 */
object AudioDiskCache {

    // Roughly 16+ hours of audio at the bitrates this app resolves — generous headroom for
    // "recently played plus liked songs" without being an unreasonable ask of phone storage.
    const val MAX_CACHE_BYTES = 1024L * 1024 * 1024

    @Volatile private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.applicationContext.cacheDir, "audio_cache"),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { cache = it }
        }

    /** Called from [PlaybackService.onDestroy] — releases the underlying file lock so a later
     *  [get] (a fresh Service instance in the same still-alive process) can safely reopen it. */
    fun release() {
        synchronized(this) {
            cache?.release()
            cache = null
        }
    }
}
