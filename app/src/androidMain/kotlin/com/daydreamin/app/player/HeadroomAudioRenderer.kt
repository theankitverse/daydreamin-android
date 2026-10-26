package com.daydreamin.app.player

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** -2 dB, in the Opus header's Q7.8 fixed point. */
internal const val OPUS_HEADROOM_Q8 = -2 * 256

/**
 * Leaves room inside the decoder for the peaks loud masters carry. Most of what YouTube serves
 * is mastered hot, and after Opus encoding a track's peaks can land above full scale (measured:
 * +1.6 dB, 0.2% of samples). Android's Opus decoder outputs 16-bit only, so those were hard
 * clipped — crackle on loud hits — before any volume or effect could do anything about it.
 *
 * The Opus header carries an output gain that the decoder applies before converting to 16-bit;
 * setting it to [OPUS_HEADROOM_Q8] brings those peaks back under full scale at the source.
 */
internal class HeadroomAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    selector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    handler: Handler?,
    listener: AudioRendererEventListener?,
    sink: AudioSink,
) : MediaCodecAudioRenderer(context, codecAdapterFactory, selector, enableDecoderFallback, handler, listener, sink) {

    override fun getMediaFormat(format: Format, codecMimeType: String, codecMaxInputSize: Int, codecOperatingRate: Float): MediaFormat {
        val mediaFormat = super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate)
        if (codecMimeType == MimeTypes.AUDIO_OPUS) {
            mediaFormat.getByteBuffer("csd-0")?.let { mediaFormat.setByteBuffer("csd-0", withOpusOutputGain(it, OPUS_HEADROOM_Q8)) }
        }
        return mediaFormat
    }
}

/**
 * A copy of an OpusHead with [gainQ8] added to its output gain (bytes 16-17, little-endian
 * signed Q7.8 dB). Anything that isn't a valid OpusHead is returned untouched.
 */
internal fun withOpusOutputGain(head: ByteBuffer, gainQ8: Int): ByteBuffer {
    val bytes = ByteArray(head.remaining()).also { head.duplicate().get(it) }
    if (bytes.size < 19 || String(bytes, 0, 8, Charsets.US_ASCII) != "OpusHead") return head
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val gain = (buffer.getShort(16) + gainQ8).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    buffer.putShort(16, gain.toShort())
    return ByteBuffer.wrap(bytes)
}
