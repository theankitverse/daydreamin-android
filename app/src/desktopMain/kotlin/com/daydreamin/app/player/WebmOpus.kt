package com.daydreamin.app.player

import io.github.jaredmdobson.concentus.OpusDecoder
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** Opus always decodes at 48 kHz; the player works in 48 kHz stereo throughout. */
const val SAMPLE_RATE = 48_000
const val CHANNELS = 2

/**
 * Just enough of WebM (Matroska) for YouTube's Opus audio: the length, the Opus setup, the seek
 * table (cues) and the audio packets. YouTube's audio-only WebM files keep the cues before the
 * audio, so a seek costs one small read at the start and one ranged read where it lands.
 */
class WebmHeader(
    /** Where the Segment's contents start; cue positions count from here. */
    val segmentStart: Long,
    val durationMs: Long,
    /** Samples the encoder put in front of the song, to be dropped (from OpusHead). */
    val preSkip: Int,
    /** (time ms, absolute file offset of the cluster) */
    val cues: List<Pair<Long, Long>>,
    val firstClusterOffset: Long,
)

private const val ID_SEGMENT = 0x18538067L
private const val ID_INFO = 0x1549A966L
private const val ID_TIMESCALE = 0x2AD7B1L
private const val ID_DURATION = 0x4489L
private const val ID_TRACKS = 0x1654AE6BL
private const val ID_TRACK = 0xAEL
private const val ID_CODEC_PRIVATE = 0x63A2L
private const val ID_CUES = 0x1C53BB6BL
private const val ID_CUE_POINT = 0xBBL
private const val ID_CUE_TIME = 0xB3L
private const val ID_CUE_POSITIONS = 0xB7L
private const val ID_CUE_CLUSTER = 0xF1L
private const val ID_CLUSTER = 0x1F43B675L
private const val ID_TIMECODE = 0xE7L
private const val ID_SIMPLE_BLOCK = 0xA3L
private const val ID_BLOCK_GROUP = 0xA0L
private const val ID_BLOCK = 0xA1L
/** Elements whose children we read; everything else is skipped over. */
private val CONTAINERS = setOf(ID_SEGMENT, ID_INFO, ID_TRACKS, ID_TRACK, ID_CUES, ID_CUE_POINT, ID_CUE_POSITIONS, ID_CLUSTER, ID_BLOCK_GROUP)

private class Ebml(private val input: InputStream, var offset: Long) {
    /** [start]: where the element itself begins; [dataStart]: where its contents begin. */
    class Element(val id: Long, val size: Long, val dataStart: Long, val start: Long)

    private fun byte(): Int {
        val b = input.read()
        if (b < 0) throw EOFException()
        offset++
        return b
    }

    private fun vint(keepMarker: Boolean): Long {
        val first = byte()
        var len = 1
        var mask = 0x80
        while (len <= 8 && first and mask == 0) { len++; mask = mask shr 1 }
        if (len > 8) throw IOException("bad EBML length")
        var v = (if (keepMarker) first else first and (mask - 1)).toLong()
        repeat(len - 1) { v = (v shl 8) or byte().toLong() }
        if (!keepMarker && v == (1L shl (7 * len)) - 1) return -1 // "unknown size" (live-style clusters)
        return v
    }

    fun next(): Element? = try {
        val start = offset
        val id = vint(keepMarker = true)
        val size = vint(keepMarker = false)
        Element(id, size, offset, start)
    } catch (e: EOFException) {
        null
    }

    fun bytes(n: Long): ByteArray {
        if (n < 0 || n > 16 * 1024 * 1024) throw IOException("bad element size $n")
        val b = input.readNBytes(n.toInt())
        if (b.size < n) throw EOFException()
        offset += b.size
        return b
    }

    fun skip(n: Long) {
        var k = n
        while (k > 0) {
            val s = input.skip(k)
            if (s <= 0) { byte(); k-- } else { k -= s; offset += s }
        }
    }

    fun uint(n: Long) = bytes(n).fold(0L) { a, b -> (a shl 8) or (b.toLong() and 0xFF) }

    fun float(n: Long): Double {
        val v = uint(n)
        return if (n == 4L) java.lang.Float.intBitsToFloat(v.toInt()).toDouble() else java.lang.Double.longBitsToDouble(v)
    }
}

/** Reads everything in front of the first audio cluster. */
fun readWebmHeader(source: ByteSource): WebmHeader {
    source.open(0).use { stream ->
        val r = Ebml(stream, 0)
        var segmentStart = 0L
        var scale = 1_000_000L
        var duration = 0.0
        var preSkip = 0
        var cueTime = 0L
        val cueOffsets = mutableListOf<Pair<Long, Long>>() // relative positions until segmentStart is known
        while (true) {
            val e = r.next() ?: throw IOException("no audio in this file")
            when (e.id) {
                ID_SEGMENT -> segmentStart = e.dataStart
                ID_CLUSTER -> {
                    val firstCluster = e.start
                    val cues = cueOffsets.map { (t, rel) -> t * scale / 1_000_000 to segmentStart + rel }
                    return WebmHeader(segmentStart, (duration * scale / 1_000_000).toLong(), preSkip, cues, firstCluster)
                }
                in CONTAINERS -> {}
                ID_TIMESCALE -> scale = r.uint(e.size)
                ID_DURATION -> duration = r.float(e.size)
                ID_CODEC_PRIVATE -> {
                    val head = r.bytes(e.size)
                    if (head.size >= 12) preSkip = (head[10].toInt() and 0xFF) or ((head[11].toInt() and 0xFF) shl 8)
                }
                ID_CUE_TIME -> cueTime = r.uint(e.size)
                ID_CUE_CLUSTER -> cueOffsets += cueTime to r.uint(e.size)
                else -> if (e.size >= 0) r.skip(e.size)
            }
        }
    }
}

/**
 * Decoded audio of one song from [startMs] on, as interleaved 16-bit stereo at 48 kHz. Starts at
 * the last cue before [startMs] and drops what comes before it, so a seek lands on the exact millisecond.
 */
class OpusPcmReader(source: ByteSource, header: WebmHeader, private val startMs: Long) : Closeable {
    private val cueOffset: Long
    private val stream: InputStream
    private val r: Ebml
    private val decoder = OpusDecoder(SAMPLE_RATE, CHANNELS)
    private val pcm = ShortArray(5760 * CHANNELS) // the longest Opus packet: 120 ms
    private var pcmStart = 0
    private var pcmEnd = 0
    private var clusterMs = 0L
    private var toDrop: Int
    private var ended = false

    init {
        val cue = header.cues.lastOrNull { it.first <= startMs }
        // No cues (unusual for YouTube): read from the first cluster and skip forward.
        cueOffset = cue?.second ?: header.firstClusterOffset
        stream = source.open(cueOffset)
        r = Ebml(stream, cueOffset)
        toDrop = if (startMs == 0L) header.preSkip else 0
    }

    /** Fills [out] with up to [max] samples (interleaved, so frames × 2). Returns how many, or -1 at the end of the song. */
    fun read(out: ShortArray, max: Int): Int {
        while (pcmStart >= pcmEnd) {
            if (ended || !decodeNextPacket()) { ended = true; return -1 }
        }
        val n = minOf(max, pcmEnd - pcmStart)
        System.arraycopy(pcm, pcmStart, out, 0, n)
        pcmStart += n
        return n
    }

    private fun decodeNextPacket(): Boolean {
        while (true) {
            val e = r.next() ?: return false
            when (e.id) {
                in CONTAINERS -> {}
                ID_TIMECODE -> clusterMs = r.uint(e.size)
                ID_SIMPLE_BLOCK, ID_BLOCK -> {
                    val block = r.bytes(e.size)
                    // Track number (1-byte vint for track 1), 16-bit relative time, flags; YouTube's Opus is never laced.
                    if (block.size < 5 || block[3].toInt() and 0x06 != 0) continue
                    val rel = ((block[1].toInt() shl 8) or (block[2].toInt() and 0xFF)).toShort()
                    val atMs = clusterMs + rel
                    val frames = decoder.decode(block, 4, block.size - 4, pcm, 0, 5760, false)
                    var from = 0
                    if (atMs + frames * 1000L / SAMPLE_RATE <= startMs) continue // entirely before the seek target
                    if (atMs < startMs) from = ((startMs - atMs) * SAMPLE_RATE / 1000).toInt().coerceAtMost(frames)
                    if (toDrop > 0) { val d = minOf(toDrop, frames - from); from += d; toDrop -= d }
                    if (from >= frames) continue
                    pcmStart = from * CHANNELS
                    pcmEnd = frames * CHANNELS
                    return true
                }
                else -> if (e.size >= 0) r.skip(e.size)
            }
        }
    }

    override fun close() = stream.close()
}
