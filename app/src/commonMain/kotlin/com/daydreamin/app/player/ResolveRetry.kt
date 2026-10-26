package com.daydreamin.app.player

import kotlinx.coroutines.delay
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

/**
 * Whether retrying [error] could plausibly succeed. Network-level failures (no route, DNS,
 * timeouts, connection resets — all [IOException]) and YouTube rate-limiting ([ReCaptchaException],
 * which NewPipe raises on HTTP 429) are worth another try after a short pause. Anything else —
 * "this video has no audio stream", "no YouTube match found", a video that's been taken down —
 * is a property of the song, not of the moment, and a second attempt just doubles how long the
 * user waits before the app moves on.
 *
 * Walks the cause chain rather than checking the top-level type, because the extractor wraps
 * whatever went wrong in its own `IllegalStateException("Extraction failed: ...")`.
 */
fun isTransientFailure(error: Throwable?): Boolean {
    var current = error
    var depth = 0
    while (current != null && depth < 8) {
        if (current is IOException || current is ReCaptchaException) return true
        current = current.cause
        depth++
    }
    return false
}

/**
 * Runs [block], and if it fails with a *transient* error (see [isTransientFailure]) waits
 * [backoffMs] and tries again, up to [maxRetries] extra times. Non-transient failures are
 * returned immediately, unretried. Default backoff is 400ms, then 800ms, ... — [backoffMs]
 * receives the zero-based index of the retry about to happen.
 */
suspend fun <T> retryTransient(
    maxRetries: Int = 1,
    backoffMs: (retryIndex: Int) -> Long = { 400L * (1L shl it) },
    block: suspend () -> Result<T>,
): Result<T> {
    var retries = 0
    while (true) {
        val result = block()
        if (result.isSuccess) return result
        if (retries >= maxRetries || !isTransientFailure(result.exceptionOrNull())) return result
        delay(backoffMs(retries))
        retries++
    }
}
