package com.daydreamin.app.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStreamErrorTest {
    @Test fun `connection drops and refused URLs keep you on the song`() {
        assertTrue(isNetworkStreamError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(isNetworkStreamError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertTrue(isNetworkStreamError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)) // a URL signed for the old network
        assertTrue(isNetworkStreamError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
    }

    @Test fun `a song that can't be decoded is still skipped`() {
        assertFalse(isNetworkStreamError(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertFalse(isNetworkStreamError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
    }
}
