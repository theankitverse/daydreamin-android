package com.daydreamin.app.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRecoveryTest {
    @Test fun badHttpStatusIsRecoverable() {
        assertTrue(shouldRecoverInPlace(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, "a", null))
    }

    @Test fun otherFailuresAreNotRecoveredInPlace() {
        for (code in listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        )) assertFalse("code $code", shouldRecoverInPlace(code, "a", null))
    }

    @Test fun aSongIsOnlyRecoveredOncePerFailure() {
        assertFalse(shouldRecoverInPlace(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, "a", "a"))
    }

    @Test fun aDifferentSongStillGetsItsOwnAttempt() {
        assertTrue(shouldRecoverInPlace(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, "b", "a"))
    }

    @Test fun onlyUrlLevelStatusesCountAsStale() {
        for (code in listOf(403, 404, 410)) assertTrue("$code", isStaleStreamHttpStatus(code))
        for (code in listOf(200, 206, 400, 429, 500, 502, 503)) assertFalse("$code", isStaleStreamHttpStatus(code))
    }
}
