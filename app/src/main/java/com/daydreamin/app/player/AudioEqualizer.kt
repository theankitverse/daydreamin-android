package com.daydreamin.app.player

import android.media.audiofx.Equalizer
import android.util.Log

private const val TAG = "AudioEqualizer"

/** Presets applied as real band-gain curves via [android.media.audiofx.Equalizer], not the
 *  platform's own built-in presets — those vary by OEM/device (different band counts, center
 *  frequencies, even missing entirely on some effect implementations), which would make the
 *  same preset name sound inconsistently different phone to phone. Classifying bands by their
 *  actual center frequency instead of a fixed index works the same way regardless of how many
 *  bands a given device's effect engine exposes. */
enum class EqPreset(val label: String) {
    OFF("Off"),
    BASS_BOOST("Bass Boost"),
    VOCAL_BOOST("Vocal Boost"),
    TREBLE_BOOST("Treble Boost");

    companion object {
        fun fromLabel(label: String): EqPreset = entries.firstOrNull { it.label == label } ?: OFF
    }
}

/** Builds an [Equalizer] on [audioSessionId] and applies [preset] to it. [PlaybackService]
 *  calls this once at startup with the player's existing session id (ExoPlayer keeps one id
 *  for the player's whole lifetime, so it is *not* re-created per song) and again only if that
 *  id is ever reassigned; changing the preset later just re-runs [applyPreset] on the live
 *  instance. Gives up with a warning if the device doesn't support the effect: audio still
 *  plays fine without it, same tolerance the loudness effect has. */
fun createEqualizer(audioSessionId: Int, preset: EqPreset): Equalizer? {
    val equalizer = try {
        Equalizer(0, audioSessionId)
    } catch (e: Exception) {
        Log.w(TAG, "Equalizer unavailable on this device: ${e.message}")
        return null
    }
    applyPreset(equalizer, preset)
    return equalizer
}

fun applyPreset(equalizer: Equalizer, preset: EqPreset) {
    if (preset == EqPreset.OFF) {
        equalizer.enabled = false
        return
    }
    val range = equalizer.bandLevelRange
    // 65% of the effect engine's own max gain — clearly audible without leaning into clipping
    // or the harsh, over-processed sound a full-strength boost tends to produce.
    val boostMb = (range[1] * 0.65).toInt().toShort()
    val numBands = equalizer.numberOfBands
    for (band in 0 until numBands) {
        val freqHz = equalizer.getCenterFreq(band.toShort()) / 1000
        val gain = when (preset) {
            EqPreset.BASS_BOOST -> if (freqHz < 250) boostMb else 0
            EqPreset.VOCAL_BOOST -> if (freqHz in 250..4000) boostMb else 0
            EqPreset.TREBLE_BOOST -> if (freqHz > 4000) boostMb else 0
            EqPreset.OFF -> 0
        }
        runCatching { equalizer.setBandLevel(band.toShort(), gain.toShort()) }
    }
    equalizer.enabled = true
}
