package com.daydreamin.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import kotlin.math.min

/** Size of each ranged request. YouTube serves a request this size at full speed; past its first burst a single long one is held to ~2× playback speed. */
internal const val STREAM_CHUNK_BYTES = 1L * 1024 * 1024

/**
 * Everything that fetches song audio — playback, downloads, warming the cache — goes through here.
 *
 * YouTube throttles one long download of a stream to roughly twice playback speed after a fast
 * first burst (measured: 37 KB/s), but answers separate 1 MB byte-range requests at full speed
 * (3.2 MB/s on the same link). So this asks for the audio a piece at a time, one request per MB,
 * and hands the bytes on as one continuous stream. A 4 MB song downloads in about a second instead
 * of two minutes, and playback buffers ahead just as fast.
 */
@UnstableApi
internal fun streamDataSourceFactory(context: Context): DataSource.Factory =
    DefaultDataSource.Factory(context, RangeChunkingDataSource.Factory(buildStreamHttpDataSourceFactory(), STREAM_CHUNK_BYTES))

@UnstableApi
internal class RangeChunkingDataSource(private val upstream: DataSource, private val chunkBytes: Long) : DataSource {

    class Factory(private val upstream: DataSource.Factory, private val chunkBytes: Long) : DataSource.Factory {
        override fun createDataSource(): DataSource = RangeChunkingDataSource(upstream.createDataSource(), chunkBytes)
    }

    private var spec: DataSpec? = null
    private var position = 0L          // the next byte to hand out, as an offset in the whole resource
    private var end = C.LENGTH_UNSET.toLong() // where the requested data stops (exclusive), once known
    private var chunkLeft = 0L         // bytes still to come from the open piece
    private var chunkOpen = false

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length else C.LENGTH_UNSET.toLong()
        openPiece()
        return if (end != C.LENGTH_UNSET.toLong()) end - dataSpec.position else C.LENGTH_UNSET.toLong()
    }

    private fun openPiece() {
        val base = spec ?: throw IllegalStateException("not open")
        val want = if (end != C.LENGTH_UNSET.toLong()) min(chunkBytes, end - position) else chunkBytes
        val opened = upstream.open(base.buildUpon().setPosition(position).setLength(want).build())
        chunkOpen = true
        if (end == C.LENGTH_UNSET.toLong()) totalFromHeaders()?.let { total -> end = total }
        chunkLeft = if (opened != C.LENGTH_UNSET.toLong()) opened else want
    }

    /** "Content-Range: bytes 0-1048575/4232944" — the full size, learned from the first piece. */
    private fun totalFromHeaders(): Long? {
        val value = upstream.responseHeaders.entries.firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }?.value?.firstOrNull() ?: return null
        return value.substringAfterLast('/', "").trim().toLongOrNull()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (end != C.LENGTH_UNSET.toLong() && position >= end) return C.RESULT_END_OF_INPUT
        if (chunkLeft <= 0L) {
            closePiece()
            openPiece()
        }
        val read = upstream.read(buffer, offset, min(length.toLong(), chunkLeft).toInt())
        if (read == C.RESULT_END_OF_INPUT) {
            // The server ended this piece early: either the real end of the file, or (size unknown) nothing more to come.
            chunkLeft = 0
            return C.RESULT_END_OF_INPUT
        }
        position += read
        chunkLeft -= read
        return read
    }

    private fun closePiece() {
        if (chunkOpen) {
            chunkOpen = false
            upstream.close()
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        try { closePiece() } finally { spec = null; chunkLeft = 0 }
    }
}
