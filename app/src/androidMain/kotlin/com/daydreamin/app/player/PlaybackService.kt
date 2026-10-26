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
    private var spare: ExoPlayer? = null
    private var crossfader: Crossfader? = null
    @Volatile private var crossfadeMs = 0L
    /** The volume players rest at: full, or lowered to leave the equalizer room to boost. */
    @Volatile private var baseVolume = 1f
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

        val upstreamDataSourceFactory = streamDataSourceFactory(this)

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

        // Downloaded songs play from the download store — no connection needed. It's read-only here:
        // the download manager is the only writer, and a missing piece (should it ever happen)
        // streams from a fresh link instead.
        val downloadsDataSourceFactory = CacheDataSource.Factory()
            .setCache(com.daydreamin.app.data.download.Downloads.cache(this))
            .setUpstreamDataSourceFactory(com.daydreamin.app.data.download.Downloads.upstream(this))
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val dataSourceFactory = DownloadRoutingDataSourceFactory(cacheDataSourceFactory, downloadsDataSourceFactory)

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
        // Two identical players: one plays, the other is the spare the crossfade brings the next
        // song in on (see Crossfader). They share one audio session, so the equalizer and the
        // loudness boost below apply to whichever is playing, and to both during a fade.
        val audioSessionId = androidx.media3.common.util.Util.generateAudioSessionIdV21(this)
        fun buildPlayer(active: Boolean): ExoPlayer = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ active)
            .setHandleAudioBecomingNoisy(active)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { p ->
                p.setAudioSessionId(audioSessionId)
                // No silence skipping: its defaults cut 80% out of anything quieter than -30 dBFS for
                // 100ms — quiet intros, rests, the breath before a chorus — which played as small jumps.
                p.addAnalyticsListener(object : AnalyticsListener {
                    override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
                        Log.w(TAG, "audio underrun at ${p.currentPosition}ms (buffer ${bufferSizeMs}ms, ${elapsedSinceLastFeedMs}ms since last feed)")
                    }
                })
                p.addListener(object : Player.Listener {
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
            }
        val player = buildPlayer(active = true)
        val spare = buildPlayer(active = false)
        this.player = player
        this.spare = spare

        Log.d(TAG, "audioSessionId=$audioSessionId")
        attachLoudnessEnhancer(audioSessionId)
        attachEqualizer(audioSessionId)

        // Standard "skip to next/previous" commands (Bluetooth remote, notification, lock
        // screen, Android Auto) are routed to the session through this wrapper so they always
        // go through PlayerController's real queue logic — see QueueAwarePlayer's kdoc.
        // Tapping the player notification, the lock screen player or the Dynamic Island opens Daydreamin
        // on Now Playing. Without this, some phones (HyperOS) opened another app instead, or nothing.
        val openPlayer = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.daydreamin.app.MainActivity::class.java)
                .setAction(com.daydreamin.app.MainActivity.ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val session = MediaSession.Builder(this, QueueAwarePlayer(player)).setSessionActivity(openPlayer).build()
        mediaSession = session

        crossfader = Crossfader(
            first = player,
            second = spare,
            fadeMs = { crossfadeMs },
            baseVolume = { baseVolume },
            allowed = { PlayerController.meta.value.repeatMode != RepeatMode.ONE },
            makeActive = { incoming, outgoing ->
                // Focus first leaves the outgoing player (so asking for it on the incoming one doesn't
                // pause the song that's fading out), then the incoming one takes it, and the session
                // — app, notification, lock screen — moves to it.
                outgoing.setAudioAttributes(audioAttributes, false)
                outgoing.setHandleAudioBecomingNoisy(false)
                incoming.setAudioAttributes(audioAttributes, true)
                incoming.setHandleAudioBecomingNoisy(true)
                session.player = QueueAwarePlayer(incoming)
                this.player = incoming
                this.spare = outgoing
            },
        ).also { it.start() }

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
                baseVolume = if (eqPreset == EqPreset.OFF) 1f else EQ_HEADROOM_VOLUME
                if (crossfader?.isFading != true) player?.volume = baseVolume // mid-fade, the fade applies it
            }
        }

        serviceScope.launch {
            DaydreaminApp.instance.prefs.crossfadeSeconds.collect { seconds ->
                crossfadeMs = seconds.coerceIn(0, 12) * 1000L
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

    /**
     * Swiping Daydreamin out of Recents ends the session: music stops and the notification goes,
     * instead of playing on with no app to come back to. (The pause also saves where you were,
     * so the next launch resumes there.) Media3 keeps the service in the foreground for a while
     * after a pause, so a plain stopSelf() is no longer enough — this pauses and stops together.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        crossfader?.cancel()
        pauseAllPlayersAndStopSelf()
        PlayerController.disconnect()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        crossfader?.release()
        spare?.release()
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
