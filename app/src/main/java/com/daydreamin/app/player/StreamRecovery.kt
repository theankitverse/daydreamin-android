package com.daydreamin.app.player

import androidx.media3.common.PlaybackException

/**
 * A resolved googlevideo URL that answers with an HTTP error status (403 expired/invalid
 * signature, 404/410 gone) is a property of that *URL*, not of the song — asking YouTube for a
 * fresh one usually works. Network drops and decoder errors are different failures and don't
 * qualify.
 */
fun isStaleStreamError(errorCode: Int): Boolean = errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

/**
 * Whether to re-resolve [playId] and resume it in place rather than skipping it. Only one
 * in-place attempt per song ([alreadyRecoveredPlayId] is the one currently being/just retried):
 * if the fresh URL fails too, the song really is unplayable right now and the normal skip logic
 * takes over instead of looping.
 */
fun shouldRecoverInPlace(errorCode: Int, playId: String, alreadyRecoveredPlayId: String?): Boolean =
    isStaleStreamError(errorCode) && playId != alreadyRecoveredPlayId

/** HTTP statuses that mean "this URL is bad" rather than "the server/network is having a moment". */
fun isStaleStreamHttpStatus(status: Int): Boolean = status == 403 || status == 404 || status == 410
