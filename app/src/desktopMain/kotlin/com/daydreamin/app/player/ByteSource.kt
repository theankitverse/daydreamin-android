package com.daydreamin.app.player

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** For tests: makes every stream read fail, as if the connection dropped. */
internal object StreamFaults {
    @Volatile var dropConnection = false
    /** Each stream reads as finished after this many bytes (a stream that breaks off with no error). */
    @Volatile var endAfterBytes: Long? = null
}

/** A song's audio file, readable from any offset: a YouTube stream or a downloaded file. */
interface ByteSource {
    /** Total size in bytes. */
    val size: Long
    fun open(offset: Long): InputStream
}

/** YouTube said no to this stream URL (403/410): it expired, or it was signed for another network address. */
class StreamExpiredException(message: String) : IOException(message)

/** Size of each ranged request — see RangeChunking on Android: YouTube serves these at full speed, one long request at ~2× playback speed. */
private const val CHUNK_BYTES = 1L * 1024 * 1024
private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

internal val streamClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

class HttpByteSource(private val url: String) : ByteSource {
    // YouTube puts the length in the URL (clen=); otherwise ask for one byte and read the total.
    override val size: Long by lazy {
        Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: probeSize()
    }

    private fun probeSize(): Long {
        val request = Request.Builder().url(url).header("Range", "bytes=0-0").header("User-Agent", USER_AGENT).build()
        streamClient.newCall(request).execute().use { r ->
            if (r.code == 403 || r.code == 410) throw StreamExpiredException("HTTP ${r.code}")
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: r.body?.contentLength()?.takeIf { it > 1 }
                ?: throw IOException("unknown length")
        }
    }

    override fun open(offset: Long): InputStream = ChunkedHttpStream(url, offset, size)
}

/**
 * Reads [url] from [start] to the end as one stream, asking for it 1 MB at a time. A dropped
 * connection is retried from the exact byte it stopped at (three tries, backing off); an expired
 * URL fails at once with [StreamExpiredException] so the player can fetch a fresh one.
 */
private class ChunkedHttpStream(private val url: String, private val start: Long, private val total: Long) : InputStream() {
    private var pos = start
    private var body: InputStream? = null
    private var call: Call? = null
    private var left = 0L
    @Volatile private var closed = false

    override fun read(): Int {
        val b = ByteArray(1)
        return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= total) return -1
        StreamFaults.endAfterBytes?.let { if (pos - start >= it) return -1 }
        var attempt = 0
        while (true) {
            if (closed) throw IOException("closed")
            try {
                if (StreamFaults.dropConnection) throw IOException("connection dropped (test)")
                if (left <= 0) openPiece()
                val n = body!!.read(b, off, minOf(len.toLong(), left).toInt())
                if (n < 0) throw IOException("connection ended early")
                pos += n; left -= n
                return n
            } catch (e: StreamExpiredException) {
                throw e
            } catch (e: IOException) {
                closePiece()
                if (closed || ++attempt > 3) throw e
                Thread.sleep(500L shl (attempt - 1)) // 0.5 s, 1 s, 2 s
            }
        }
    }

    private fun openPiece() {
        closePiece()
        val end = minOf(pos + CHUNK_BYTES - 1, total - 1)
        val request = Request.Builder().url(url).header("Range", "bytes=$pos-$end").header("User-Agent", USER_AGENT).build()
        val c = streamClient.newCall(request).also { call = it }
        val r = c.execute()
        if (r.code == 403 || r.code == 410) { r.close(); throw StreamExpiredException("HTTP ${r.code}") }
        if (r.code != 206 && r.code != 200) { r.close(); throw IOException("HTTP ${r.code}") }
        body = r.body!!.byteStream()
        left = end - pos + 1
    }

    private fun closePiece() {
        runCatching { body?.close() }
        body = null
        left = 0
    }

    override fun close() {
        closed = true
        call?.cancel()
        closePiece()
    }
}

class FileByteSource(private val file: File) : ByteSource {
    override val size: Long get() = file.length()
    override fun open(offset: Long): InputStream =
        BufferedInputStream(FileInputStream(file).also { it.channel.position(offset) }, 64 * 1024)
}
