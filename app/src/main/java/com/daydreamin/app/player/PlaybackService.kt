package com.daydreamin.app.player

import android.content.Intent
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.daydreamin.app.DaydreaminApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "PlaybackService"

/**
 * The optional loudness boost. Kept small: most tracks on YouTube are mastered loud already
 * (-6 to -9 LUFS is typical), and every dB added past that is a dB the effect's limiter has to
 * squash back down — at +6 dB that was audible distortion.
 */
private const val LOUDNESS_TARGET_GAIN_MB = 300 // +3dB

/** -4.5 dB, matching the equalizer presets' boost (see AudioEqualizer). */
private const val EQ_HEADROOM_VOLUME = 0.6f

/**
 * Hosts the ExoPlayer instance + MediaSession. Once this is running as a foreground
 * service, Android shows lock-screen / notification transport controls automatically
 * and keeps audio playing with the screen off — the two things the web app could never do.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    @Volatile private var normalizationEnabled = false
    @Volatile private var eqPreset: EqPreset = EqPreset.OFF
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val upstreamDataSourceFactory = DefaultDataSource.Factory(this, buildStreamHttpDataSourceFactory())

        // Transparently serves repeat plays (and anything already downloaded once) from disk
        // instead of re-fetching from YouTube's CDN — the actual audio bytes, not just the
        // resolved URL. Safe even if the cache itself misbehaves (corrupt entry, disk full):
        // FLAG_IGNORE_CACHE_ON_ERROR falls back to upstream rather than failing playback.
        // See PlayerController.buildMediaItem for the custom cache key this depends on — the
        // resolved stream URL is a signed, expiring one-time link, so caching by raw URL would
        // never hit on a later play of the same song; the cache key must be the stable video id.
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(AudioDiskCache.get(this))
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // handleAudioFocus=true + setHandleAudioBecomingNoisy(true) cover the standard
        // interruption cases on their own: ExoPlayer auto-pauses on focus loss (a call, another
        // app's audio), auto-ducks on transient loss, resumes on regain, and auto-pauses when
        // the active route is about to go "noisy" — which Android fires for both a wired
        // headphone unplug *and* a Bluetooth device disconnect, not just wired. No extra
        // BroadcastReceiver needed for any of that; logged below for visibility, not because
        // we're implementing the behavior ourselves.
        // Audio goes through HeadroomAudioRenderer: it gives Android's Opus decoder room for the
        // over-full-scale peaks loud masters carry, which it would otherwise hard-clip.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioRenderers(
                context: android.content.Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: android.os.Handler,
                eventListener: AudioRendererEventListener,
                out: java.util.ArrayList<Renderer>,
            ) {
                out.add(HeadroomAudioRenderer(context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, audioSink))
            }
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
        this.player = player

        // No silence skipping: its defaults cut 80% out of anything quieter than -30 dBFS for
        // 100ms — quiet intros, rests, the breath before a chorus — which played as small jumps.
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
                Log.w(TAG, "audio underrun at ${player.currentPosition}ms (buffer ${bufferSizeMs}ms, ${elapsedSinceLastFeedMs}ms since last feed)")
            }
        })

        // onAudioSessionIdChanged only fires when the id *changes* — but ExoPlayer assigns its
        // session id during construction, before this listener exists, so relying on the
        // callback alone means the effects below could simply never attach. Attach once up
        // front with whatever id the player already has; the listener then keeps them in sync
        // if it's ever reassigned later.
        Log.d(TAG, "initial audioSessionId=${player.audioSessionId}")
        attachLoudnessEnhancer(player.audioSessionId)
        attachEqualizer(player.audioSessionId)

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                Log.d(TAG, "audioSessionId changed -> $audioSessionId")
                attachLoudnessEnhancer(audioSessionId)
                attachEqualizer(audioSessionId)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                    Log.d(TAG, "paused: lost audio focus")
                } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                    Log.d(TAG, "paused: audio becoming noisy (headphones/Bluetooth disconnected)")
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "player error: ${error.errorCodeName} ${error.message}", error)
            }
        })

        // Standard "skip to next/previous" commands (Bluetooth remote, notification, lock
        // screen, Android Auto) are routed to the session through this wrapper so they always
        // go through PlayerController's real queue logic — see QueueAwarePlayer's kdoc.
        mediaSession = MediaSession.Builder(this, QueueAwarePlayer(player)).build()

        // Playback ownership lives here, in the Service, not the UI: this guarantees the
        // MediaController connects (and the persisted playback state restores — see
        // PlayerController.restoreIfNeeded) as soon as this Service exists, whether or not any
        // Activity/Compose UI ever opens — e.g. the OS restarting this Service to deliver a
        // media-button press after the process was killed.
        PlayerController.ensureConnected(applicationContext)

        serviceScope.launch {
            DaydreaminApp.instance.prefs.audioNormalization.collect { enabled ->
                normalizationEnabled = enabled
                loudnessEnhancer?.enabled = enabled
            }
        }

        serviceScope.launch {
            DaydreaminApp.instance.prefs.equalizerPreset.collect { presetName ->
                eqPreset = EqPreset.fromLabel(presetName)
                equalizer?.let { applyPreset(it, eqPreset) }
                // The equalizer boosts after the player's volume is applied, so a boost needs the
                // same amount of room made below it, or boosted bands clip on loud tracks.
                player?.volume = if (eqPreset == EqPreset.OFF) 1f else EQ_HEADROOM_VOLUME
            }
        }

        // Keeps liked songs available offline even if they haven't actually been played from
        // this device — fires once with whatever's already liked at startup, then again every
        // time the liked list changes. Cheap to re-run: see SongPrecacher's kdoc.
        serviceScope.launch {
            DaydreaminApp.instance.prefs.likedSongs.collect { liked ->
                SongPrecacher.precacheAll(applicationContext, liked)
            }
        }
    }

    private fun attachLoudnessEnhancer(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        loudnessEnhancer?.release()
        loudnessEnhancer = try {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(LOUDNESS_TARGET_GAIN_MB)
                enabled = normalizationEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer unavailable on this device: ${e.message}")
            null // some devices/OEMs don't support this effect — playback still works fine without it
        }
        if (loudnessEnhancer != null) Log.d(TAG, "LoudnessEnhancer attached to session $audioSessionId")
    }

    private fun attachEqualizer(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        equalizer?.release()
        equalizer = createEqualizer(audioSessionId, eqPreset)
        if (equalizer != null) Log.d(TAG, "Equalizer attached to session $audioSessionId, preset=${eqPreset.label}")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        loudnessEnhancer?.release()
        equalizer?.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        AudioDiskCache.release()
        super.onDestroy()
    }
}
