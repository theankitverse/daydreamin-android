package com.daydreamin.app.player

import com.daydreamin.app.platform.Log
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

private const val TAG = "AudioEngine"

/** How far ahead each song decodes (and downloads). Rides out a slow network without a stutter. */
private const val BUFFER_SECONDS = 15
/** Mixer step: 10 ms of audio. */
private const val STEP_FRAMES = SAMPLE_RATE / 100

/**
 * One song, decoding on its own thread into a [BUFFER_SECONDS] buffer from [startMs] on. The
 * mixer only ever takes what's already decoded, so a slow connection shows up as "buffering"
 * instead of blocking playback of the other deck during a crossfade.
 */
class Deck(val key: String, val source: ByteSource, val header: WebmHeader, val startMs: Long) {
    private val ring = ShortArray(SAMPLE_RATE * CHANNELS * BUFFER_SECONDS)
    private val lock = Object()
    private var readPos = 0L
    private var writePos = 0L
    @Volatile var ended = false
        private set
    @Volatile var error: Throwable? = null
        private set
    @Volatile private var closed = false

    /** Frames handed to the mixer so far — the deck's own clock. */
    @Volatile var framesPlayed = 0L
        private set

    val durationMs: Long get() = header.durationMs
    val decodedPositionMs: Long get() = startMs + framesPlayed * 1000 / SAMPLE_RATE
    val bufferedSamples: Int get() = synchronized(lock) { (writePos - readPos).toInt() }

    private val thread = Thread({ decodeLoop() }, "deck-$key").apply { isDaemon = true; start() }

    private fun decodeLoop() {
        val chunk = ShortArray(STEP_FRAMES * CHANNELS * 4)
        try {
            OpusPcmReader(source, header, startMs).use { reader ->
                while (!closed) {
                    val n = reader.read(chunk, chunk.size)
                    if (n < 0) break
                    var off = 0
                    while (off < n && !closed) {
                        synchronized(lock) {
                            while (!closed && writePos - readPos >= ring.size) lock.wait(50)
                            if (closed) return
                            val free = (ring.size - (writePos - readPos)).toInt()
                            val k = minOf(free, n - off)
                            for (i in 0 until k) ring[((writePos + i) % ring.size).toInt()] = chunk[off + i]
                            writePos += k
                            off += k
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            if (!closed) {
                error = e
                Log.w(TAG, "deck $key stopped: ${e.javaClass.simpleName}: ${e.message}")
            }
        } finally {
            ended = true
        }
    }

    /** Copies up to [max] decoded samples into [out]; 0 when nothing is decoded yet. */
    fun take(out: ShortArray, max: Int): Int = synchronized(lock) {
        val n = minOf(max.toLong(), writePos - readPos).toInt()
        for (i in 0 until n) out[i] = ring[((readPos + i) % ring.size).toInt()]
        readPos += n
        framesPlayed += n / CHANNELS
        lock.notifyAll()
        n
    }

    /** Nothing left now and nothing more coming. */
    val finished: Boolean get() = ended && bufferedSamples == 0

    fun close() {
        closed = true
        synchronized(lock) { lock.notifyAll() }
        thread.interrupt()
    }
}

/** What the engine tells the player about. Called on the mixer thread — keep it quick. */
interface EngineListener {
    /** [to] took over from [from]: the prepared next song, gaplessly or by crossfade. */
    fun onTransition(from: Deck, to: Deck)
    /** The song finished and nothing was lined up after it. */
    fun onEnded(deck: Deck)
    fun onError(deck: Deck, error: Throwable)
    fun onBuffering(buffering: Boolean)
}

/**
 * The sound card side: one mixer thread pulling 10 ms at a time from the playing deck (and, during
 * a crossfade, the outgoing one), running it through the equalizer and loudness boost, and writing
 * it to the system's audio output. Writing blocks once the output's small buffer is full, which
 * keeps the whole thing in step with real time.
 */
class AudioEngine(private val listener: EngineListener) {
    private val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, CHANNELS, true, false)
    private var line: SourceDataLine? = null

    @Volatile var current: Deck? = null
        private set
    @Volatile var next: Deck? = null
        private set
    @Volatile private var outgoing: Deck? = null
    @Volatile private var fadeFrames = 0L
    @Volatile private var fadeDone = 0L

    @Volatile var playing = false
        private set
    @Volatile private var buffering = false

    /** Crossfade length when a song ends on its own; 0 = straight into the next (gapless). */
    @Volatile var crossfadeMs = 0L
    @Volatile var eqPreset: EqPreset = EqPreset.OFF
        set(value) { field = value; eqChanged = true }
    @Volatile private var eqChanged = true
    /** "Loudness boost": +3 dB, with a soft limiter so peaks round off instead of clipping. */
    @Volatile var boost = false
    /** Output level, 0–1, as a gain (the player maps the slider onto a curve that sounds even). */
    @Volatile var volume = 1f

    private val a = ShortArray(STEP_FRAMES * CHANNELS)
    private val b = ShortArray(STEP_FRAMES * CHANNELS)
    private val mix = FloatArray(STEP_FRAMES * CHANNELS)
    private val bytes = ByteArray(STEP_FRAMES * CHANNELS * 2)
    private var filters: List<Biquad> = emptyList()

    private val thread = Thread({ loop() }, "audio-mixer").apply { isDaemon = true; priority = Thread.MAX_PRIORITY; start() }

    /** Plays [deck] now, dropping whatever was playing. */
    @Synchronized fun play(deck: Deck) {
        closeAllExcept(next)
        if (next === deck) next = null
        current = deck
        flushOutput()
        playing = true
    }

    /** Lines up [deck] to follow the current song (gapless, or crossfaded into); replaces any earlier one. */
    @Synchronized fun setNext(deck: Deck?) {
        val old = next
        next = deck
        if (old != null && old !== deck) old.close()
    }

    /** Skips to the lined-up song now, without a fade. Returns false if nothing is lined up. */
    @Synchronized fun skipToNext(): Boolean {
        val from = current ?: return false
        val to = next ?: return false
        next = null
        outgoing?.close(); outgoing = null
        current = to
        from.close()
        flushOutput()
        playing = true
        listener.onTransition(from, to)
        return true
    }

    /** Replaces the playing deck with [deck] — a seek, or the same song from a fresh URL — keeping play/pause as it is. */
    @Synchronized fun replaceCurrent(deck: Deck) {
        outgoing?.close(); outgoing = null
        current?.close()
        current = deck
        flushOutput()
    }

    @Synchronized fun pause() {
        playing = false
        line?.stop()
    }

    @Synchronized fun resume() {
        if (current == null) return
        playing = true
        line?.start()
    }

    /** Stops and forgets everything (another song is about to load). */
    @Synchronized fun stop() {
        playing = false
        closeAllExcept(null)
        flushOutput()
        setBuffering(false)
    }

    /** Where the playing song is, allowing for what's still waiting in the output buffer. */
    val positionMs: Long
        get() {
            val deck = current ?: return 0
            val l = line
            val queuedFrames = if (l != null) (l.bufferSize - l.available()) / (CHANNELS * 2) else 0
            return (deck.decodedPositionMs - queuedFrames * 1000L / SAMPLE_RATE).coerceAtLeast(deck.startMs.coerceAtMost(deck.decodedPositionMs))
        }

    private fun closeAllExcept(keep: Deck?) {
        listOfNotNull(current, outgoing, next).forEach { if (it !== keep) it.close() }
        current = null; outgoing = null
        if (next !== keep) next = null
    }

    private fun flushOutput() {
        line?.flush()
    }

    private fun openLine(): SourceDataLine? = runCatching {
        AudioSystem.getSourceDataLine(format).apply {
            open(format, SAMPLE_RATE * CHANNELS * 2 / 5) // 200 ms: short enough for snappy pause/seek
            start()
        }
    }.onFailure { Log.e(TAG, "no audio output", it) }.getOrNull()

    private fun setBuffering(value: Boolean) {
        if (buffering != value) { buffering = value; listener.onBuffering(value) }
    }

    private fun loop() {
        while (true) {
            try {
                step()
            } catch (e: InterruptedException) {
                return
            } catch (e: Throwable) {
                Log.e(TAG, "mixer step failed", e)
                Thread.sleep(50)
            }
        }
    }

    private fun step() {
        val deck = current
        if (!playing || deck == null) { Thread.sleep(10); return }
        val out = line ?: openLine().also { line = it } ?: run { Thread.sleep(500); return }

        val n = deck.take(a, a.size)
        if (n == 0) {
            when {
                deck.error != null -> synchronized(this) {
                    if (current === deck) { playing = false; listener.onError(deck, deck.error!!) }
                }
                deck.finished -> synchronized(this) {
                    if (current !== deck) return
                    val to = next
                    if (to != null) {
                        next = null
                        current = to
                        outgoing?.close(); outgoing = null
                        deck.close()
                        listener.onTransition(deck, to) // gapless: the next song's audio follows on the very next step
                    } else {
                        playing = false
                        listener.onEnded(deck)
                    }
                }
                else -> { setBuffering(true); Thread.sleep(5) }
            }
            return
        }
        setBuffering(false)

        // A song ending on its own crossfades into the next once it's within the fade's length of the end.
        maybeStartCrossfade(deck)

        for (i in 0 until n) mix[i] = a[i] / 32768f
        val fadingOut = outgoing
        if (fadingOut != null) {
            val m = fadingOut.take(b, n)
            val total = fadeFrames.coerceAtLeast(1)
            for (f in 0 until n / CHANNELS) {
                val t = ((fadeDone + f).toFloat() / total).coerceIn(0f, 1f)
                val gainIn = sin(t * PI / 2).toFloat()   // equal-power: loudness stays even through the fade
                val gainOut = cos(t * PI / 2).toFloat()
                for (c in 0 until CHANNELS) {
                    val i = f * CHANNELS + c
                    mix[i] = mix[i] * gainIn + (if (i < m) b[i] / 32768f else 0f) * gainOut
                }
            }
            fadeDone += n / CHANNELS
            if (fadeDone >= fadeFrames || fadingOut.finished) synchronized(this) {
                if (outgoing === fadingOut) { outgoing = null; fadingOut.close() }
            }
        }

        process(mix, n)
        for (i in 0 until n) {
            val s = (mix[i] * 32767f).toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = s.toByte()
            bytes[i * 2 + 1] = (s shr 8).toByte()
        }
        out.write(bytes, 0, n * 2)
    }

    private fun maybeStartCrossfade(deck: Deck) {
        val fadeMs = crossfadeMs
        val to = next
        if (fadeMs <= 0 || to == null || outgoing != null || deck.durationMs <= 0) return
        val remaining = deck.durationMs - deck.decodedPositionMs
        // Only once the next song has some audio ready, so the fade never fades into silence.
        if (remaining > fadeMs || to.bufferedSamples < SAMPLE_RATE * CHANNELS / 2) return
        synchronized(this) {
            if (current !== deck || next !== to) return
            next = null
            outgoing = deck
            fadeFrames = remaining.coerceAtLeast(500) * SAMPLE_RATE / 1000
            fadeDone = 0
            current = to
            listener.onTransition(deck, to)
        }
    }

    // ---- equalizer and loudness

    private fun process(buf: FloatArray, n: Int) {
        if (eqChanged) { filters = filtersFor(eqPreset); eqChanged = false }
        val gain = if (boost) 1.4125f else 1f // +3 dB
        val fs = filters
        for (f in 0 until n / CHANNELS) {
            for (c in 0 until CHANNELS) {
                val i = f * CHANNELS + c
                var x = buf[i]
                for (filter in fs) x = filter.process(x, c)
                buf[i] = softLimit(x * gain) * volume
            }
        }
    }

    /** Leaves normal levels alone; rounds off anything over -2 dBFS instead of clipping it. */
    private fun softLimit(x: Float): Float {
        val knee = 0.8f
        val ax = abs(x)
        if (ax <= knee) return x
        val over = (ax - knee) / (1f - knee)
        val y = knee + (1f - knee) * tanh(over.toDouble()).toFloat()
        return if (x < 0) -y else y
    }

    private fun filtersFor(preset: EqPreset): List<Biquad> = when (preset) {
        EqPreset.OFF -> emptyList()
        EqPreset.BASS_BOOST -> listOf(Biquad.lowShelf(110.0, 6.0))
        EqPreset.VOCAL_BOOST -> listOf(Biquad.lowShelf(180.0, -2.0), Biquad.peaking(2_500.0, 1.0, 4.0))
        EqPreset.TREBLE_BOOST -> listOf(Biquad.highShelf(6_000.0, 6.0))
    }
}

/** A standard two-pole filter (the Audio EQ Cookbook's shelving and peaking shapes), one state per channel. */
class Biquad private constructor(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
    private val nb0 = (b0 / a0).toFloat(); private val nb1 = (b1 / a0).toFloat(); private val nb2 = (b2 / a0).toFloat()
    private val na1 = (a1 / a0).toFloat(); private val na2 = (a2 / a0).toFloat()
    private val x1 = FloatArray(CHANNELS); private val x2 = FloatArray(CHANNELS)
    private val y1 = FloatArray(CHANNELS); private val y2 = FloatArray(CHANNELS)

    fun process(x: Float, ch: Int): Float {
        val y = nb0 * x + nb1 * x1[ch] + nb2 * x2[ch] - na1 * y1[ch] - na2 * y2[ch]
        x2[ch] = x1[ch]; x1[ch] = x
        y2[ch] = y1[ch]; y1[ch] = y
        return y
    }

    companion object {
        private fun w0(f: Double) = 2 * PI * f / SAMPLE_RATE

        fun peaking(f: Double, q: Double, gainDb: Double): Biquad {
            val amp = 10.0.pow(gainDb / 40); val w = w0(f); val alpha = sin(w) / (2 * q)
            return Biquad(1 + alpha * amp, -2 * cos(w), 1 - alpha * amp, 1 + alpha / amp, -2 * cos(w), 1 - alpha / amp)
        }

        fun lowShelf(f: Double, gainDb: Double): Biquad {
            val amp = 10.0.pow(gainDb / 40); val w = w0(f); val alpha = sin(w) / 2 * sqrt(2.0); val c = cos(w); val sa = 2 * sqrt(amp) * alpha
            return Biquad(
                amp * ((amp + 1) - (amp - 1) * c + sa), 2 * amp * ((amp - 1) - (amp + 1) * c), amp * ((amp + 1) - (amp - 1) * c - sa),
                (amp + 1) + (amp - 1) * c + sa, -2 * ((amp - 1) + (amp + 1) * c), (amp + 1) + (amp - 1) * c - sa,
            )
        }

        fun highShelf(f: Double, gainDb: Double): Biquad {
            val amp = 10.0.pow(gainDb / 40); val w = w0(f); val alpha = sin(w) / 2 * sqrt(2.0); val c = cos(w); val sa = 2 * sqrt(amp) * alpha
            return Biquad(
                amp * ((amp + 1) + (amp - 1) * c + sa), -2 * amp * ((amp - 1) + (amp + 1) * c), amp * ((amp + 1) + (amp - 1) * c - sa),
                (amp + 1) - (amp - 1) * c + sa, 2 * ((amp - 1) - (amp + 1) * c), (amp + 1) - (amp - 1) * c - sa,
            )
        }
    }
}
