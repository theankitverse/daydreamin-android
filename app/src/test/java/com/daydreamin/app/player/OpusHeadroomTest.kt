package com.daydreamin.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OpusHeadroomTest {

    /** A real OpusHead layout: magic, version, channels, pre-skip, input rate, output gain, mapping family. */
    private fun opusHead(gainQ8: Short): ByteBuffer {
        val b = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
        b.put("OpusHead".toByteArray(Charsets.US_ASCII))
        b.put(1) // version
        b.put(2) // channels
        b.putShort(312) // pre-skip
        b.putInt(48_000) // input sample rate
        b.putShort(gainQ8)
        b.put(0) // mapping family
        b.flip()
        return b
    }

    private fun gainOf(head: ByteBuffer) = head.duplicate().order(ByteOrder.LITTLE_ENDIAN).getShort(16).toInt()

    @Test fun `adds the headroom to the header's output gain`() {
        assertEquals(-512, gainOf(withOpusOutputGain(opusHead(0), OPUS_HEADROOM_Q8)))
        assertEquals(256 - 512, gainOf(withOpusOutputGain(opusHead(256), OPUS_HEADROOM_Q8)))
    }

    @Test fun `leaves everything else in the header alone`() {
        val original = opusHead(0)
        val patched = withOpusOutputGain(original, OPUS_HEADROOM_Q8).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(19, patched.remaining())
        assertEquals(312, patched.getShort(10).toInt())
        assertEquals(48_000, patched.getInt(12))
        assertEquals(0, gainOf(original)) // the input isn't modified
    }

    @Test fun `ignores anything that isn't an OpusHead`() {
        val notOpus = ByteBuffer.wrap(ByteArray(19) { 7 })
        assertSame(notOpus, withOpusOutputGain(notOpus, OPUS_HEADROOM_Q8))
    }

    @Test fun `never overflows the field`() {
        assertEquals(Short.MIN_VALUE.toInt(), gainOf(withOpusOutputGain(opusHead(Short.MIN_VALUE), OPUS_HEADROOM_Q8)))
    }
}
