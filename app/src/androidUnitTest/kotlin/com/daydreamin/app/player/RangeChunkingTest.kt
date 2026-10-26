package com.daydreamin.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val TestUri: Uri = Uri.parse("https://example.com/audio")

/** A server that answers byte-range requests, like googlevideo, and counts them. */
private class RangeServer(private val body: ByteArray, private val sendContentRange: Boolean = true) : DataSource {
    var requests = 0
    private var pos = 0; private var left = 0
    override fun addTransferListener(transferListener: TransferListener) {}
    override fun open(dataSpec: DataSpec): Long {
        requests++
        pos = dataSpec.position.toInt()
        val remaining = body.size - pos
        left = if (dataSpec.length == C.LENGTH_UNSET.toLong()) remaining else minOf(dataSpec.length.toInt(), remaining)
        return left.toLong()
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (left == 0) return C.RESULT_END_OF_INPUT
        val n = minOf(length, left, 7_000) // short reads, like a real socket
        System.arraycopy(body, pos, buffer, offset, n); pos += n; left -= n
        return n
    }
    override fun getUri(): Uri? = null
    override fun getResponseHeaders(): Map<String, List<String>> =
        if (sendContentRange) mapOf("content-range" to listOf("bytes $pos-${pos + left - 1}/${body.size}")) else emptyMap()
    override fun close() {}
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RangeChunkingTest {
    private val body = ByteArray(2_500_000) { (it * 31 + 7).toByte() }

    private fun readAll(source: DataSource, spec: DataSpec): ByteArray {
        source.open(spec)
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16_384)
        while (true) { val n = source.read(buf, 0, buf.size); if (n == C.RESULT_END_OF_INPUT) break; out.write(buf, 0, n) }
        source.close()
        return out.toByteArray()
    }

    @Test fun `pieces join back into the exact file`() {
        val server = RangeServer(body)
        val bytes = readAll(RangeChunkingDataSource(server, 1_048_576), DataSpec(TestUri))
        assertArrayEquals(body, bytes)
        assertEquals(3, server.requests) // 1 MB + 1 MB + the rest
    }

    @Test fun `starting partway and stopping early read just that span`() {
        val spec = DataSpec.Builder().setUri(TestUri).setPosition(900_000).setLength(1_300_000).build()
        val bytes = readAll(RangeChunkingDataSource(RangeServer(body), 1_048_576), spec)
        assertArrayEquals(body.copyOfRange(900_000, 2_200_000), bytes)
    }

    @Test fun `still reads everything when the server doesn't say the size`() {
        val bytes = readAll(RangeChunkingDataSource(RangeServer(body, sendContentRange = false), 1_048_576), DataSpec(TestUri))
        assertArrayEquals(body, bytes)
    }
}
