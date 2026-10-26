package com.daydreamin.app.player

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ResolveRetryTest {

    // --- classification ---

    @Test fun ioFailuresAreTransient() {
        assertTrue(isTransientFailure(IOException("boom")))
        assertTrue(isTransientFailure(UnknownHostException("youtubei.googleapis.com")))
        assertTrue(isTransientFailure(SocketTimeoutException("timeout")))
    }

    @Test fun rateLimitingIsTransient() {
        assertTrue(isTransientFailure(ReCaptchaException("reCaptcha challenge requested", "https://youtube.com")))
    }

    @Test fun extractorWrapperAroundNetworkErrorIsStillTransient() {
        // The extractor rewraps everything as IllegalStateException("Extraction failed: ...").
        val wrapped = IllegalStateException("Extraction failed: UnknownHostException", UnknownHostException("host"))
        assertTrue(isTransientFailure(wrapped))
    }

    @Test fun propertiesOfTheSongAreNotTransient() {
        assertFalse(isTransientFailure(IllegalStateException("YouTube video abc has no audio stream.")))
        assertFalse(isTransientFailure(IllegalStateException("Extraction failed: ISE", IllegalStateException("no audio stream"))))
        assertFalse(isTransientFailure(IllegalStateException("No YouTube match found for \"a - b\".")))
        assertFalse(isTransientFailure(null))
    }

    @Test fun selfReferentialCauseChainDoesNotLoopForever() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        assertFalse(isTransientFailure(a))
    }

    // --- retry behaviour ---

    @Test fun successFirstTimeMakesOneCall() = runBlocking {
        var calls = 0
        val result = retryTransient<String>(backoffMs = { 1L }) { calls++; Result.success("ok") }
        assertEquals("ok", result.getOrNull())
        assertEquals(1, calls)
    }

    @Test fun transientFailureThenSuccessIsRescued() = runBlocking {
        var calls = 0
        val result = retryTransient<String>(backoffMs = { 1L }) {
            calls++
            if (calls == 1) Result.failure(UnknownHostException("blip")) else Result.success("recovered")
        }
        assertEquals("recovered", result.getOrNull())
        assertEquals(2, calls)
    }

    @Test fun permanentFailureIsNotRetried() = runBlocking {
        var calls = 0
        val result = retryTransient<String>(backoffMs = { 1L }) {
            calls++
            Result.failure(IllegalStateException("YouTube video abc has no audio stream."))
        }
        assertTrue(result.isFailure)
        assertEquals("a permanent failure must not burn a second attempt", 1, calls)
    }

    @Test fun persistentTransientFailureGivesUpAfterTheConfiguredRetries() = runBlocking {
        var calls = 0
        val result = retryTransient<String>(maxRetries = 1, backoffMs = { 1L }) {
            calls++
            Result.failure(IOException("still down"))
        }
        assertTrue(result.isFailure)
        assertEquals(2, calls)
    }

    @Test fun backoffGrowsAcrossRetries() = runBlocking {
        val seen = mutableListOf<Int>()
        retryTransient<String>(maxRetries = 3, backoffMs = { idx -> seen.add(idx); 1L }) {
            Result.failure(IOException("down"))
        }
        assertEquals(listOf(0, 1, 2), seen)
    }

    @Test fun shippedDefaultBackoffActuallyWaitsAboutFourHundredMs() = runBlocking {
        var calls = 0
        val startedAt = System.nanoTime()
        retryTransient<String> { // no backoffMs override: exercises the real default
            calls++
            if (calls == 1) Result.failure(IOException("blip")) else Result.success("ok")
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertEquals(2, calls)
        assertTrue("expected the default ~400ms pause before the retry, waited ${elapsedMs}ms", elapsedMs >= 390)
        assertTrue("default pause should stay short, waited ${elapsedMs}ms", elapsedMs < 2000)
    }
}
