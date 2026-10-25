package com.daydreamin.app.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "Precache"

/**
 * Warms [AudioDiskCache] with just the *start* of songs visible on screen (Home's top picks), so
 * whichever one you tap begins instantly. The rest streams normally. Full songs for offline
 * listening are Downloads' job (see [com.daydreamin.app.data.download.Downloads]), not this cache's.
 *
 * Respects the "Wi-Fi only" download preference, and is cheap to call repeatedly — [CacheWriter]
 * skips whatever's already cached.
 */
object SongPrecacher {

    /** First ~20-25s of audio at typical resolved bitrates — enough for ExoPlayer to start
     *  playback instantly without waiting on the network, without fully downloading a song
     *  nobody's chosen yet. */
    private const val LEAD_IN_BYTES = 400_000L

    suspend fun precacheLeadIn(context: Context, song: Song) {
        if (!shouldPrecache(context)) return
        withContext(Dispatchers.IO) {
            runCatching { precacheOne(buildCacheDataSource(context), song, LEAD_IN_BYTES) }
                .onFailure { Log.w(TAG, "couldn't lead-in precache '${song.title}': ${it.message}") }
        }
    }

    private suspend fun shouldPrecache(context: Context): Boolean {
        if (DaydreaminApp.instance.prefs.downloadWifiOnly.first() && !isOnWifi(context)) {
            Log.d(TAG, "skipping — Wi-Fi only is on and we're not on Wi-Fi")
            return false
        }
        return true
    }

    private suspend fun precacheOne(cacheDataSource: CacheDataSource, song: Song, maxBytes: Long?) {
        try {
            cacheFromFreshStream(cacheDataSource, song, maxBytes)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (!isStaleStreamHttpStatus(e.responseCode)) throw e
            // A resolved URL that answers 403/404/410 is intermittently just a bad URL (see
            // StreamRecovery) — get a fresh one and try once more instead of giving up on the song
            // until the next pass.
            Log.w(TAG, "'${song.title}' stream answered ${e.responseCode} — re-resolving once")
            YouTubeExtractorService.invalidateStream(song.artist, song.title, song.videoId)
            cacheFromFreshStream(cacheDataSource, song, maxBytes)
        }
    }

    private suspend fun cacheFromFreshStream(cacheDataSource: CacheDataSource, song: Song, maxBytes: Long?) {
        val resolved = DaydreaminApp.instance.repository.resolveStream(song).getOrNull() ?: return
        // Must match PlayerController.buildMediaItem's custom cache key exactly, or a song
        // precached here would never actually be hit once the user plays it for real.
        val cacheKey = "${song.videoId ?: song.playId}_${resolved.bitrateKbps}_${resolved.codec}"
        val dataSpec = DataSpec.Builder()
            .setUri(Uri.parse(resolved.streamUrl))
            .setKey(cacheKey)
            .apply { if (maxBytes != null) setLength(maxBytes) }
            .build()
        CacheWriter(cacheDataSource, dataSpec, null, null).cache()
        Log.d(TAG, "precached '${song.title}'" + if (maxBytes != null) " (lead-in)" else " (full)")
    }

    private fun buildCacheDataSource(context: Context): CacheDataSource =
        CacheDataSource.Factory()
            .setCache(AudioDiskCache.get(context))
            .setUpstreamDataSourceFactory(streamDataSourceFactory(context))
            .createDataSource()

    private fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        @Suppress("DEPRECATION")
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
