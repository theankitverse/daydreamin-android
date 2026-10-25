package com.daydreamin.app.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "Crossfader"

/** How long before the fade the next song is loaded on the spare player, so it's ready to start on time. */
private const val PRIME_LEAD_MS = 8_000L

/**
 * Crossfade between songs, the way Apple Music does it: the next song comes in underneath the end
 * of the current one and the two cross over.
 *
 * It takes two players, because one player can only ever play one song at a time. Shortly before
 * a song ends, the spare player is loaded with the songs after it (the active player has already
 * buffered them, so it's ready almost at once). When the song has the fade length left, the spare
 * starts silently and becomes the session's player, so the app, the notification and the lock
 * screen all move to the new song. The two volumes then cross on an equal-power curve while the
 * old song plays out its last seconds on the other player, which is then stopped and becomes the
 * spare for the next crossfade. Neither song is ever cut, restarted or seeked, so there's no
 * glitch at the handoff.
 *
 * Only songs that end on their own crossfade. A skip, pause, seek or newly picked song cancels a
 * pending or running fade at once (the old song simply stops), and repeat-one is left alone.
 */
internal class Crossfader(
    first: ExoPlayer,
    second: ExoPlayer,
    /** Fade length in ms; 0 turns crossfading off. */
    private val fadeMs: () -> Long,
    /** The volume both players rest at (lower while an equalizer preset needs headroom). */
    private val baseVolume: () -> Float,
    /** False while something else is in charge of the song's end (repeat-one). */
    private val allowed: () -> Boolean,
    /** Makes [player] the one the session (and so the app and system controls) talks to. */
    private val makeActive: (player: ExoPlayer, previous: ExoPlayer) -> Unit,
) {
    var active: ExoPlayer = first
        private set
    private var spare: ExoPlayer = second

    private enum class Phase { IDLE, PRIMED, FADING }
    private var phase = Phase.IDLE
    private var primedFor: String? = null   // the song whose ending the spare was loaded for
    private var incomingId: String? = null  // the song fading in
    private var fadeLengthMs = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, if (phase == Phase.FADING) 40L else 250L)
        }
    }

    val isFading: Boolean get() = phase == Phase.FADING

    fun start() { handler.removeCallbacks(tick); handler.post(tick) }

    fun release() {
        handler.removeCallbacks(tick)
        if (phase != Phase.IDLE) reset()
    }

    /** Stops whatever is pending or playing out — for when everything is being stopped at once. */
    fun cancel() { if (phase != Phase.IDLE) reset() }

    private fun step() {
        val fade = fadeMs()
        when (phase) {
            Phase.IDLE -> if (fade > 0 && shouldPrime(fade)) prime()
            Phase.PRIMED -> when {
                !primeStillValid(fade) -> reset()
                remaining(active) <= fade && spare.playbackState == Player.STATE_READY -> beginFade()
            }
            Phase.FADING -> continueFade()
        }
    }

    private fun remaining(p: Player): Long {
        val d = p.duration
        return if (d == C.TIME_UNSET || d <= 0) Long.MAX_VALUE else (d - p.currentPosition).coerceAtLeast(0)
    }

    private fun shouldPrime(fade: Long): Boolean {
        val p = active
        if (!allowed() || !p.isPlaying || p.playbackState != Player.STATE_READY) return false
        val d = p.duration
        if (d == C.TIME_UNSET || d < fade * 2 + 10_000) return false // too short to give a fade away
        if (p.currentMediaItemIndex + 1 >= p.mediaItemCount) return false // nothing lined up next
        val left = remaining(p)
        return left <= fade + PRIME_LEAD_MS && left > fade / 2
    }

    private fun prime() {
        val p = active
        val next = p.currentMediaItemIndex + 1
        val upcoming = (next until p.mediaItemCount).map { p.getMediaItemAt(it) }
        primedFor = p.currentMediaItem?.mediaId
        incomingId = upcoming.first().mediaId
        spare.volume = 0f
        spare.playWhenReady = false
        spare.setMediaItems(upcoming, /* resetPosition = */ true)
        spare.prepare()
        phase = Phase.PRIMED
        Log.d(TAG, "primed '$incomingId' to follow '$primedFor'")
    }

    /** The spare is only good while the same song is still playing toward its end with the same song next. */
    private fun primeStillValid(fade: Long): Boolean {
        val p = active
        if (fade <= 0 || !allowed() || !p.playWhenReady) return false
        if (p.currentMediaItem?.mediaId != primedFor) return false
        val next = p.currentMediaItemIndex + 1
        if (next >= p.mediaItemCount || p.getMediaItemAt(next).mediaId != incomingId) return false
        return p.playbackState != Player.STATE_ENDED && spare.playerError == null
    }

    private fun beginFade() {
        val outgoing = active
        val incoming = spare
        fadeLengthMs = remaining(outgoing).coerceAtLeast(1)
        // The outgoing player must not move on to the next song itself: the incoming one plays it now.
        val after = outgoing.currentMediaItemIndex + 1
        if (outgoing.mediaItemCount > after) outgoing.removeMediaItems(after, outgoing.mediaItemCount)
        incoming.volume = 0f
        incoming.play()
        active = incoming
        spare = outgoing
        makeActive(incoming, outgoing)
        phase = Phase.FADING
        Log.d(TAG, "crossfading into '$incomingId' over ${fadeLengthMs}ms")
        continueFade()
    }

    private fun continueFade() {
        val outgoing = spare
        val incoming = active
        // Anything you do on the new song (pause, skip, seek, a new pick) ends the fade at once.
        if (!incoming.playWhenReady || incoming.currentMediaItem?.mediaId != incomingId) {
            Log.d(TAG, "fade interrupted — stopping the outgoing song")
            reset()
            return
        }
        val left = if (outgoing.playbackState == Player.STATE_ENDED) 0L else remaining(outgoing)
        val progress = (1f - left.toFloat() / fadeLengthMs).coerceIn(0f, 1f)
        val base = baseVolume()
        // Equal-power: the combined loudness holds steady through the middle instead of dipping.
        outgoing.volume = base * cos(progress * PI / 2).toFloat()
        incoming.volume = base * sin(progress * PI / 2).toFloat()
        if (progress >= 1f) reset()
    }

    /** Back to one player: the spare is stopped (PRIMED: songs that never played; FADING: the outgoing song). */
    private fun reset() {
        if (phase != Phase.IDLE) {
            spare.stop()
            spare.clearMediaItems()
        }
        spare.volume = baseVolume()
        active.volume = baseVolume()
        phase = Phase.IDLE
        primedFor = null
        incomingId = null
    }
}
