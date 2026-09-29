package com.daydreamin.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.repository.toSong
import com.daydreamin.app.data.taste.TasteProfile
import com.daydreamin.app.data.youtube.ResolvedStream
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "PlaybackTiming"

/** How many upcoming songs stay fully resolved and buffered inside the player's own playlist
 *  at all times — not just the very next one. One is enough for "let it play through and
 *  transition naturally", but real usage skips ahead multiple times in a row while browsing,
 *  and only the first of those skips was ever actually instant before; the rest still paid the
 *  full resolve-then-buffer cost. Two covers a quick double-skip without over-fetching. */
private const val PREPARE_AHEAD = 2

/** Skipping a song you've heard at least this much of, but less than [EARLY_SKIP_MAX_MS], counts against it in recommendations. */
private const val EARLY_SKIP_MIN_MS = 1_500L
private const val EARLY_SKIP_MAX_MS = 30_000L

@Serializable
enum class RepeatMode { OFF, ALL, ONE }

/** Everything needed to resume exactly where playback left off after process death or a
 *  fresh app launch — the current track, the rest of the queue, playback position, and the
 *  shuffle/repeat toggles. Persisted as JSON via [com.daydreamin.app.data.prefs.AppPreferences]. */
@Serializable
data class PlaybackSnapshot(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
)

/**
 * Everything that changes rarely — song, transport state, queue. Split out from
 * [PlaybackProgress] so screens that don't render a scrubber (Home, Search, Library,
 * the bottom bar) don't recompose twice a second along with it.
 */
data class PlaybackMeta(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Song> = emptyList(),
    val error: String? = null,
    /** e.g. "Opus · 160 kbps" — the actual stream quality in use, for transparency. */
    val streamQuality: String? = null,
)

/** Ticks every 500ms during playback — kept separate from [PlaybackMeta] on purpose. */
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * The single owner of playback state for the whole app. Screens read [meta] / [progress]
 * and call these functions instead of touching [MediaController] directly — it resolves a
 * playable stream URL on-device before handing anything to the player, prefetches the next
 * queue item's stream while the current song plays so skipping forward feels instant, and
 * manages the "Up Next" queue itself (the service only ever holds one media item at a time;
 * queue items are resolved on demand, or already warm from prefetch, as they're reached).
 */
object PlayerController {

    private val app get() = DaydreaminApp.instance
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private val backStack = ArrayDeque<Song>()
    private var tickerJob: Job? = null
    private var connecting = false
    private var restored = false

    // The next [PREPARE_AHEAD] songs already resolved and appended to the player's own
    // internal playlist (right after the current item, in queue order), so ExoPlayer starts
    // buffering them in the background *while the current song is still playing* — that's
    // what makes transitions instant instead of a fresh resolve-then-buffer pause, including
    // for a second/third quick skip, not just the first one.
    private val preparedAheadPlayIds = mutableListOf<String>()
    private val preparedResolved = mutableMapOf<String, ResolvedStream>()
    // When each entry above was resolved — a prepared item can sit unused for a long time (a
    // long current song, a long pause), and the googlevideo URL baked into its MediaItem at
    // prepare time expires the same as any other resolve, regardless of how "ready" it looks.
    // Without this we'd only discover that at the exact moment it's finally needed — see
    // refreshNearExpiryPreparedItems, which uses this to catch it before then.
    private val preparedAtMs = mutableMapOf<String, Long>()
    private var prepareUpcomingRequestId = 0

    // How many full playlist swaps (playSong / restore) are between "we've decided to replace the
    // player's playlist" and "setMediaItem actually did". Their resolve step can take seconds,
    // and until it lands the *old* playlist is still live but about to be wiped: a queue change
    // in that window used to make prepareUpcoming() append songs to the doomed playlist and
    // record them as prepared — then the swap wiped them, leaving bookkeeping that claimed two
    // prepared songs the player didn't have, and the next skip silently lost its gapless
    // transition (measured: first audio 1.8s after Next instead of instant). While this is
    // non-zero prepareUpcoming() stands down; each swap's own tail call to it reconciles against
    // the then-latest queue, so nothing is lost by waiting.
    private var pendingPlaylistSwaps = 0

    // Some individual YouTube videos genuinely have no usable audio stream (no adaptive audio
    // rendition, taken down, region-locked) — that's a property of that one video, not a reason
    // to kill the whole listening session. This tracks a run of back-to-back failures so a
    // real outage (every song failing) still surfaces a clear error instead of silently
    // cascading through the entire queue.
    /** The song most recently re-resolved in place after its stream URL failed (see
     *  [recoverCurrentSong]); cleared once it's audibly playing again. Guards against looping. */
    private var recoveredPlayId: String? = null
    private var consecutiveResolveFailures = 0
    private const val MAX_CONSECUTIVE_RESOLVE_FAILURES = 3
    // The last song that actually, successfully started playing — what [handlePlaybackFailure]
    // reverts the displayed "current song" to when a resolve failure gives up with nothing left
    // to fall back on, since the player itself never actually left this song.
    private var lastKnownGoodSong: Song? = null

    // Startup-latency instrumentation: which mediaId we most recently asked the player to
    // start, and when, so the listener callbacks below can log how long buffering/first-audio
    // actually took — without this we'd be guessing at startup performance instead of measuring it.
    private var pendingStartMediaId: String? = null
    private var pendingStartRequestedAtMs: Long = 0L
    private var pendingStartIsRestore = false

    private val _meta = MutableStateFlow(PlaybackMeta())
    val meta: StateFlow<PlaybackMeta> = _meta

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress

    /** Safe to call from anywhere, any number of times — the Service calls this itself on
     *  creation (so playback ownership, including restoration, never depends on any UI ever
     *  having opened), and the UI calls it too when it does open; whichever runs first wins. */
    fun ensureConnected(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context.applicationContext, ComponentName(context.applicationContext, PlaybackService::class.java))
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        future.addListener({
            controller = future.get()
            attachListener()
            startTicker()
            connecting = false
            restoreIfNeeded()
        }, MoreExecutors.directExecutor())
    }

    private fun attachListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _meta.update { it.copy(isPlaying = isPlaying) }
                // The song is audibly playing again — its next URL failure gets a fresh in-place attempt.
                if (isPlaying && controller?.currentMediaItem?.mediaId == recoveredPlayId) recoveredPlayId = null
                // A pause (user, audio-focus loss, headphones out) is exactly when the process
                // is most likely to be killed next, and the ticker only saves while playing —
                // so save the exact pause position instead of one up to ~5s stale.
                if (!isPlaying) {
                    val c = controller
                    // Only trust the player's position if it's still on the song we think is current
                    // (mid-swap it can briefly report the previous song's position).
                    if (c != null && c.currentMediaItem?.mediaId == _meta.value.currentSong?.playId) {
                        _progress.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0)) }
                    }
                    persistSnapshotSoon()
                }
                if (isPlaying && pendingStartMediaId != null && controller?.currentMediaItem?.mediaId == pendingStartMediaId) {
                    Log.d(TAG, "first audio at +${SystemClock.elapsedRealtime() - pendingStartRequestedAtMs}ms")
                    pendingStartMediaId = null
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _meta.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
                _progress.update { it.copy(durationMs = controller?.duration?.coerceAtLeast(0) ?: 0L) }
                if (playbackState == Player.STATE_READY && pendingStartMediaId != null && controller?.currentMediaItem?.mediaId == pendingStartMediaId) {
                    Log.d(TAG, "buffered/ready at +${SystemClock.elapsedRealtime() - pendingStartRequestedAtMs}ms")
                    // A restore deliberately never calls play(), so there's no later "first
                    // audio" event to log — this is the last timing signal we'll get for it.
                    if (pendingStartIsRestore) pendingStartMediaId = null
                }
                if (playbackState == Player.STATE_ENDED) {
                    scope.launch { advance(userInitiated = false) }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // AUTO = the player itself moved on to the pre-buffered next item (song ended
                // naturally); SEEK = we called seekToNextMediaItem() for an instant manual skip.
                // Either way the transition already happened gaplessly — this just syncs our
                // state to match. A transition from our own setMediaItem() swap (e.g. tapping an
                // unrelated song) reports PLAYLIST_CHANGED instead, which playSong() already
                // handles itself, so it's ignored here.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    onGaplessAdvance(mediaItem)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Distinct from a resolve-time failure (caught before the player is ever
                // touched): this is the stream URL itself failing once ExoPlayer actually tries
                // to fetch audio from it — expired googlevideo URL, a mid-buffer network drop,
                // a server-side error. The player really did stop this time, so (unlike a
                // resolve failure) there's no "still actually playing" song to just redisplay.
                val song = _meta.value.currentSong ?: return
                Log.e(TAG, "player error on song='${song.title}': ${error.errorCodeName} ${error.message}", error)
                if (shouldRecoverInPlace(error.errorCode, song.playId, recoveredPlayId)) {
                    recoverCurrentSong(song, controller?.currentPosition?.coerceAtLeast(0) ?: 0L, controller?.playWhenReady ?: true)
                } else {
                    handlePlaybackFailure(song, error.message ?: error.errorCodeName, revertOnGiveUp = false)
                }
            }
        })
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var tick = 0
            while (isActive) {
                tick++
                val c = controller
                if (c != null && c.isPlaying) {
                    _progress.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0), durationMs = c.duration.coerceAtLeast(0)) }
                    // Position changes constantly but doesn't need disk-write-per-tick durability
                    // — every ~5s is enough to survive a crash without losing more than a few
                    // seconds of "where you were", at a fraction of the I/O.
                    if (tick % 10 == 0) persistSnapshotNow()
                }
                // Deliberately outside the isPlaying check — a long pause is exactly the case
                // where a prepared-ahead item's URL can go stale before it's ever used.
                if (tick % 120 == 0) refreshNearExpiryPreparedItems()
                delay(500)
            }
        }
    }

    /** Prepared-ahead items can sit unused for a long time — a long current song, or the user
     *  just leaves it paused — long enough for the googlevideo URL already baked into their
     *  MediaItem to expire before they're ever actually needed, discovered only as a hard
     *  player error at the exact moment of transition. Checked every ~60s regardless of play
     *  state and refreshes anything within [YouTubeExtractorService.STREAM_CACHE_TTL_MS] of
     *  expiring, in place, via [Player.replaceMediaItem] — the queue position and everything
     *  else about the player's state is untouched; only that one not-yet-reached item's
     *  underlying URL changes underneath it. */
    private fun refreshNearExpiryPreparedItems() {
        if (preparedAheadPlayIds.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val refreshMarginMs = 3 * 60 * 1000L
        preparedAheadPlayIds.toList().forEachIndexed { index, playId ->
            val preparedAt = preparedAtMs[playId] ?: return@forEachIndexed
            if (now - preparedAt < YouTubeExtractorService.STREAM_CACHE_TTL_MS - refreshMarginMs) return@forEachIndexed
            val song = _meta.value.queue.getOrNull(index)?.takeIf { it.playId == playId } ?: return@forEachIndexed
            scope.launch {
                val fresh = resolveStreamWithRetry(song).getOrNull() ?: return@launch
                val cur = controller ?: return@launch
                // Re-verify this song is still prepared at a real position — the queue may have
                // changed (reorder, skip, radio mix landing) while this resolve was in flight.
                val liveIndex = preparedAheadPlayIds.indexOf(playId)
                if (liveIndex == -1) return@launch
                val playerIndex = cur.currentMediaItemIndex + 1 + liveIndex
                if (playerIndex >= cur.mediaItemCount) return@launch
                cur.replaceMediaItem(playerIndex, buildMediaItem(song, fresh))
                preparedResolved[playId] = fresh
                preparedAtMs[playId] = SystemClock.elapsedRealtime()
                Log.d(TAG, "refreshed near-expiry prepared item '${song.title}' before it was ever needed")
            }
        }
    }

    /** Restores the last-known playback state (song, queue, position, shuffle, repeat) from
     *  disk — runs at most once per process lifetime, the first time a [MediaController]
     *  successfully connects, regardless of whether that connection was initiated by
     *  [PlaybackService] itself or later by the UI. Lands paused (never auto-plays) so a cold
     *  restore never surprises the user with audio; they choose when to resume. */
    private fun restoreIfNeeded() {
        if (restored) return
        restored = true
        scope.launch {
            val json = app.prefs.playbackSnapshotJson.first() ?: return@launch
            val snapshot = runCatching { Json.decodeFromString<PlaybackSnapshot>(json) }.getOrNull() ?: return@launch
            val song = snapshot.currentSong ?: return@launch
            if (_meta.value.currentSong != null) return@launch // something already started playing before this landed — don't clobber it

            Log.d(TAG, "restoring song='${song.title}' positionMs=${snapshot.positionMs} queueSize=${snapshot.queue.size}")
            pendingStartMediaId = song.playId
            pendingStartRequestedAtMs = SystemClock.elapsedRealtime()
            pendingStartIsRestore = true

            _meta.update {
                it.copy(
                    currentSong = song,
                    queue = snapshot.queue,
                    shuffle = snapshot.shuffle,
                    repeatMode = snapshot.repeatMode,
                    isBuffering = true,
                )
            }
            _progress.update { PlaybackProgress(positionMs = snapshot.positionMs, durationMs = 0L) }

            // Same window as playSong(): the resolve below can take seconds, during which a
            // queue change must not make prepareUpcoming() build on a playlist we're about to replace.
            pendingPlaylistSwaps++
            var swapReleased = false
            fun releaseSwap() { if (!swapReleased) { swapReleased = true; pendingPlaylistSwaps-- } }
            try {
                val result = resolveStreamWithRetry(song)
                val resolved = result.getOrNull()
                if (resolved == null) {
                    Log.w(TAG, "restore failed song='${song.title}' error=${result.exceptionOrNull()?.message}")
                    _meta.update { it.copy(isBuffering = false, error = "Couldn't restore \"${song.title}\": ${result.exceptionOrNull()?.message ?: "unknown error"}") }
                    pendingStartMediaId = null
                    return@launch
                }
                // setMediaItem(item, startPositionMs) sets both atomically — a separate seekTo()
                // call right after prepare() raced the session's command-availability sync in
                // testing and silently landed at position 0 instead of the restored position.
                controller?.setMediaItem(buildMediaItem(song, resolved), snapshot.positionMs)
                releaseSwap()
                controller?.prepare()
                lastKnownGoodSong = song
                val qualityLabel = "${resolved.codec.uppercase().removePrefix("WEBMA_").removePrefix("M4A_")} · ${resolved.bitrateKbps} kbps"
                _meta.update { it.copy(isBuffering = false, streamQuality = qualityLabel) }
                prepareUpcoming()
            } finally {
                releaseSwap()
            }
        }
    }

    /** [app.repository.resolveStream] fails transiently often enough (a flaky network blip,
     *  YouTube briefly rate-limiting) that surfacing an error to the user on the very first
     *  failure is premature — one silent retry after a short backoff clears most of them
     *  without the user ever noticing. Deliberately *only* for transient failures: see
     *  [isTransientFailure]. */
    private suspend fun resolveStreamWithRetry(song: Song, maxRetries: Int = 1): Result<ResolvedStream> {
        var attempt = 0
        return retryTransient(maxRetries) {
            attempt++
            val startedAt = SystemClock.elapsedRealtime()
            val result = app.repository.resolveStream(song)
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            if (result.isSuccess) {
                Log.d(TAG, "resolve ok song='${song.title}' attempt=$attempt took=${elapsedMs}ms")
            } else {
                val error = result.exceptionOrNull()
                // Only transient failures (network, rate limit) get another attempt — a video
                // with no audio stream fails the same way twice, so retrying just delays the skip.
                Log.w(TAG, "resolve failed song='${song.title}' attempt=$attempt took=${elapsedMs}ms transient=${isTransientFailure(error)} error=${error?.message}")
            }
            result
        }
    }

    /** Writes the current song/queue/position/shuffle/repeat to disk so playback can resume
     *  after process death — cheap enough (small JSON blob) to call on every meaningful state
     *  change rather than only periodically. */
    private suspend fun persistSnapshotNow() {
        val m = _meta.value
        val song = m.currentSong
        if (song == null) {
            app.prefs.clearPlaybackSnapshot()
            return
        }
        val snapshot = PlaybackSnapshot(
            currentSong = song,
            queue = m.queue,
            positionMs = _progress.value.positionMs,
            shuffle = m.shuffle,
            repeatMode = m.repeatMode,
        )
        val json = runCatching { Json.encodeToString(snapshot) }.getOrNull() ?: return
        app.prefs.savePlaybackSnapshotJson(json)
    }

    /** Fire-and-forget version of [persistSnapshotNow] for call sites that aren't already
     *  inside a coroutine (most of the public queue/transport functions below). */
    private fun persistSnapshotSoon() {
        scope.launch { persistSnapshotNow() }
    }

    /** Play [song] now. If [queueContext] is empty, "Up Next" is auto-filled from YouTube's related tracks — resolved on-device, in the same call that finds the stream. */
    fun playSong(song: Song, queueContext: List<Song> = emptyList(), autoFillQueue: Boolean = true) {
        // Same one-entry-per-song invariant as addToQueue — a playlist containing the same song
        // twice would otherwise put a duplicate straight into the queue.
        val queueContext = queueContext.distinctBy { it.playId }
        val previous = _meta.value.currentSong
        if (previous != null && previous.playId != song.playId) backStack.addLast(previous)

        // We're establishing a brand new "current" song via a full swap below — any items we'd
        // previously pre-buffered as "next" no longer apply.
        preparedAheadPlayIds.clear()
        prepareUpcomingRequestId++ // kill any in-flight prepare loop still aimed at the playlist about to be replaced
        pendingPlaylistSwaps++
        pendingStartMediaId = song.playId
        pendingStartRequestedAtMs = SystemClock.elapsedRealtime()
        pendingStartIsRestore = false
        Log.d(TAG, "playSong requested song='${song.title}'")

        _meta.update { it.copy(currentSong = song, isBuffering = true, queue = queueContext, error = null) }
        _progress.update { PlaybackProgress(positionMs = 0L, durationMs = 0L) }
        recordStart(song)
        persistSnapshotSoon()

        scope.launch {
            var swapReleased = false
            fun releaseSwap() { if (!swapReleased) { swapReleased = true; pendingPlaylistSwaps-- } }
            try {
            val result = resolveStreamWithRetry(song)
            val resolved = result.getOrNull()
            if (resolved == null) {
                releaseSwap()
                handlePlaybackFailure(song, result.exceptionOrNull()?.message ?: "unknown error", revertOnGiveUp = true)
                return@launch
            }
            consecutiveResolveFailures = 0
            lastKnownGoodSong = song
            app.appScope.launch { TasteProfile.rememberVideoId(song, resolved.videoId) }
            controller?.setMediaItem(buildMediaItem(song, resolved))
            releaseSwap() // the old playlist is gone — prepareUpcoming() may work on the new one again
            controller?.prepare()
            controller?.play()
            Log.d(TAG, "prepare() called song='${song.title}' at +${SystemClock.elapsedRealtime() - pendingStartRequestedAtMs}ms")

            val qualityLabel = "${resolved.codec.uppercase().removePrefix("WEBMA_").removePrefix("M4A_")} · ${resolved.bitrateKbps} kbps"
            _meta.update { it.copy(streamQuality = qualityLabel) }

            val effectiveQueue = if (queueContext.isEmpty() && autoFillQueue) {
                val upNext = resolved.related.map { it.toSong() }.filterNot { it.playId == song.playId }
                _meta.update { it.copy(queue = upNext) }

                // Upgrade to YouTube Music's actual radio mix in the background — same
                // algorithm behind "Start Radio", much better than plain related videos.
                // Doesn't block playback, and only lands if we're still on this song.
                scope.launch { autoFillQueueFor(song, resolved) }

                upNext
            } else {
                queueContext
            }

            // Start buffering the next couple of songs *now*, inside the player's own queue,
            // while this one plays — this is what makes transitions gapless instead of a fresh
            // resolve-then-buffer pause, even across more than one quick skip.
            prepareUpcoming()
            persistSnapshotNow()
            } finally {
                releaseSwap() // covers cancellation or an unexpected throw — never leave prepareUpcoming() disabled
            }
        }
    }

    /**
     * The stream URL for [song] failed at the player level with an HTTP error (see
     * [isStaleStreamError]) — most often a URL that expired while paused, or one YouTube handed
     * out that doesn't actually work. Rather than skipping a song the user is listening to,
     * fetch a fresh URL and put it back at [positionMs], keeping play/pause as it was. Follows the
     * same playlist-swap protocol as [playSong] since `setMediaItem` replaces the player's whole
     * playlist (including the pre-buffered next items, which [prepareUpcoming] restocks).
     * If the fresh URL fails too, the normal skip logic runs ([recoveredPlayId] blocks a second try).
     */
    private fun recoverCurrentSong(song: Song, positionMs: Long, playWhenReady: Boolean) {
        recoveredPlayId = song.playId
        Log.w(TAG, "stale stream for '${song.title}' — re-resolving and resuming at ${positionMs}ms (playWhenReady=$playWhenReady)")
        YouTubeExtractorService.invalidateStream(song.artist, song.title, song.videoId)
        preparedAheadPlayIds.clear()
        prepareUpcomingRequestId++
        pendingPlaylistSwaps++
        scope.launch {
            var swapReleased = false
            fun releaseSwap() { if (!swapReleased) { swapReleased = true; pendingPlaylistSwaps-- } }
            try {
                val resolved = resolveStreamWithRetry(song).getOrNull()
                if (_meta.value.currentSong?.playId != song.playId) return@launch // user moved on while we resolved
                if (resolved == null) {
                    releaseSwap()
                    handlePlaybackFailure(song, "couldn't refresh the stream", revertOnGiveUp = false)
                    return@launch
                }
                controller?.setMediaItem(buildMediaItem(song, resolved), positionMs)
                releaseSwap()
                controller?.prepare()
                if (playWhenReady) controller?.play()
                Log.d(TAG, "recovered '${song.title}' with a fresh stream at ${positionMs}ms")
                prepareUpcoming()
            } finally {
                releaseSwap()
            }
        }
    }

    /** A song failing to play — whether it never resolved (no audio stream on that particular
     *  video, taken down, region-locked, already retried once in [resolveStreamWithRetry]) or
     *  it resolved fine but then errored out at the actual player level (an expired stream URL,
     *  a mid-buffer network drop) — used to just show an error and leave playback dead in the
     *  water. Now it behaves like every other music app: skip it and keep the session going, as
     *  long as there's something else queued and this isn't turning into a real outage (every
     *  song failing back to back).
     *
     *  [revertOnGiveUp] distinguishes the two cases when there's nothing left to skip to: a
     *  resolve failure never touched the player at all, so whatever played last is *still*
     *  genuinely playing — the display should revert to it rather than show a song that never
     *  loaded. A player-level error means the player really did stop; there's nothing to revert
     *  the display *to* (reverting would just recreate this same class of bug), so the failed
     *  song stays shown, honestly, alongside the error. */
    private fun handlePlaybackFailure(song: Song, reason: String, revertOnGiveUp: Boolean) {
        pendingStartMediaId = null
        consecutiveResolveFailures++
        val nextInQueue = _meta.value.queue.firstOrNull()
        if (nextInQueue != null && consecutiveResolveFailures <= MAX_CONSECUTIVE_RESOLVE_FAILURES) {
            Log.w(TAG, "unplayable song='${song.title}' ($reason) — skipping to next in queue")
            _meta.update { it.copy(error = "Skipped \"${song.title}\" — couldn't play it") }
            val remaining = _meta.value.queue.drop(1)
            playSong(nextInQueue, queueContext = remaining, autoFillQueue = remaining.isEmpty())
        } else {
            Log.e(TAG, "giving up: song='${song.title}' ($reason), consecutiveFailures=$consecutiveResolveFailures, queueEmpty=${nextInQueue == null}")
            consecutiveResolveFailures = 0
            _meta.update {
                it.copy(
                    currentSong = if (revertOnGiveUp) lastKnownGoodSong else it.currentSong,
                    isBuffering = false,
                    isPlaying = if (revertOnGiveUp) (controller?.isPlaying ?: false) else false,
                    error = "Couldn't play \"${song.title}\": $reason",
                )
            }
        }
    }

    private fun buildMediaItem(song: Song, resolved: ResolvedStream): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.playId)
            .setUri(Uri.parse(resolved.streamUrl))
            // The disk cache (see AudioDiskCache / PlaybackService) needs a cache key that
            // stays stable across re-resolves — the stream URL itself is a signed, expiring
            // one-time link that's different every time, so keying by URL would never hit on a
            // later play of the same song. Folding in bitrate+codec, not just the video id,
            // means that if YouTube ever actually serves a different encoding for the same
            // video later, the key changes too and we safely re-fetch instead of risking
            // stitching mismatched cached bytes onto a differently-encoded stream.
            .setCustomCacheKey("${song.videoId ?: song.playId}_${resolved.bitrateKbps}_${resolved.codec}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.artworkUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .build()
            )
            .build()

    /** Resolves [song]'s radio mix (blended with a few different-artist liked-song mixes for
     *  variety) and lands it as "Up Next", replacing the transient related-tracks list. Runs in
     *  the background so it never blocks playback starting. */
    private suspend fun autoFillQueueFor(song: Song, resolved: ResolvedStream) {
        val recentTitles = app.prefs.history.first().take(15)
            .map { YouTubeExtractorService.normalizeTitleForDedup(it.title) }
            .toSet()

        val primaryMix = YouTubeExtractorService
            .fetchRadioMix(resolved.videoId, song.title, song.artist, recentTitles)
            .map { it.toSong() }
            .filterNot { it.playId == song.playId }

        // Blend in radios from a few DIFFERENT-artist liked songs — real variety, and what
        // actually rescues a collab track (like a KR$NA x Seedhe Maut song) whose own radio
        // mix otherwise stays narrowly within those same one or two artists. A single blend
        // seed wasn't enough to counter that.
        val blendSeeds = app.prefs.likedSongs.first()
            .filterNot { it.playId == song.playId }
            .filterNot { it.artist.trim().equals(song.artist.trim(), ignoreCase = true) }
            .distinctBy { it.artist.trim().lowercase() }
            .shuffled()
            .take(3)

        val secondaryMixes = coroutineScope {
            blendSeeds.map { seed ->
                async {
                    val seedVideoId = seed.videoId
                        ?: runCatching { YouTubeExtractorService.resolveForSong(seed.artist, seed.title).videoId }.getOrNull()
                    seedVideoId?.let {
                        YouTubeExtractorService.fetchRadioMix(it, seed.title, seed.artist, recentTitles)
                            .map { t -> t.toSong() }
                            .filterNot { s -> s.playId == song.playId }
                    }.orEmpty()
                }
            }.awaitAll()
        }

        // No more than a few tracks per artist in the final queue, no matter how narrow the
        // underlying mixes were — this is the actual fix for "up next is just the same one or
        // two artists", applied uniformly to every song.
        val radioTracks = interleaveMany(primaryMix, secondaryMixes)
            .distinctBy { YouTubeExtractorService.normalizeTitleForDedup(it.title) }
            .capPerArtist(max = 3)

        if (radioTracks.isNotEmpty() && _meta.value.currentSong?.playId == song.playId) {
            _meta.update { it.copy(queue = radioTracks) }
            prepareUpcoming()
            persistSnapshotNow()
        }
    }

    /** Keeps the player's own playlist stocked with the next [PREPARE_AHEAD] queue items,
     *  fully resolved and appended (not just a cache-warm), so ExoPlayer is actually buffering
     *  their audio in the background *while the current song plays* — the mechanism behind
     *  gapless transitions, deep enough to survive more than one quick skip in a row.
     *
     *  Reuses whatever's already correctly prepared (the common prefix between what's prepared
     *  and what's now wanted), trims the rest if the queue changed underneath it (a reorder, a
     *  new radio mix landing, a removal), and resolves the remaining slots one at a time, in
     *  order, so a stale in-flight resolve can never land in the wrong player position. A newer
     *  call always supersedes an older one still in flight (tracked via [prepareUpcomingRequestId]). */
    private fun prepareUpcoming() {
        val c = controller ?: return
        if (pendingPlaylistSwaps > 0) return // see pendingPlaylistSwaps — the swap's own tail call will reconcile
        val desired = _meta.value.queue.take(PREPARE_AHEAD)

        var keep = 0
        while (keep < preparedAheadPlayIds.size && keep < desired.size && preparedAheadPlayIds[keep] == desired[keep].playId) keep++
        if (keep < preparedAheadPlayIds.size) {
            val from = c.currentMediaItemIndex + 1 + keep
            while (c.mediaItemCount > from) c.removeMediaItem(c.mediaItemCount - 1)
            preparedAheadPlayIds.subList(keep, preparedAheadPlayIds.size).clear()
        }

        val missing = desired.drop(keep)
        if (missing.isEmpty()) return

        val requestId = ++prepareUpcomingRequestId
        scope.launch {
            for (song in missing) {
                if (requestId != prepareUpcomingRequestId) return@launch // superseded by a newer call
                val resolved = resolveStreamWithRetry(song).getOrNull() ?: continue
                val cur = controller ?: return@launch
                if (requestId != prepareUpcomingRequestId) return@launch
                // The slot this song belongs at must still be exactly where we expect it —
                // avoids racing a user who skipped elsewhere, or another queue change, while
                // this resolve was in flight.
                if (_meta.value.queue.getOrNull(preparedAheadPlayIds.size)?.playId != song.playId) return@launch
                cur.addMediaItem(buildMediaItem(song, resolved))
                preparedAheadPlayIds.add(song.playId)
                preparedResolved[song.playId] = resolved
                preparedAtMs[song.playId] = SystemClock.elapsedRealtime()
                Log.d(TAG, "prepared ahead song='${song.title}' depth=${preparedAheadPlayIds.size}/$PREPARE_AHEAD")
            }
        }
    }

    /** The player itself just advanced to the item [prepareUpcoming] pre-buffered — either the
     *  song ended naturally or we called seekToNextMediaItem() for a manual skip. Either way the
     *  audio transition already happened with no gap; this only syncs our own state to match. */
    private fun onGaplessAdvance(mediaItem: MediaItem?) {
        val previousSong = _meta.value.currentSong
        val newCurrent = _meta.value.queue.firstOrNull() ?: return
        if (mediaItem?.mediaId != newCurrent.playId) return
        Log.d(TAG, "gapless advance to '${newCurrent.title}' (pre-buffered, no resolve/buffer wait)")
        lastKnownGoodSong = newCurrent

        val newQueue = _meta.value.queue.drop(1)
        val resolved = preparedResolved.remove(newCurrent.playId)
        preparedAtMs.remove(newCurrent.playId)
        if (previousSong != null && previousSong.playId != newCurrent.playId) backStack.addLast(previousSong)

        val qualityLabel = resolved?.let { "${it.codec.uppercase().removePrefix("WEBMA_").removePrefix("M4A_")} · ${it.bitrateKbps} kbps" }
        _meta.update {
            it.copy(
                currentSong = newCurrent,
                queue = newQueue,
                isBuffering = false,
                streamQuality = qualityLabel ?: it.streamQuality,
            )
        }
        _progress.update { PlaybackProgress(positionMs = 0L, durationMs = controller?.duration?.coerceAtLeast(0) ?: 0L) }
        recordStart(newCurrent)
        resolved?.let { r -> app.appScope.launch { TasteProfile.rememberVideoId(newCurrent, r.videoId) } }
        if (preparedAheadPlayIds.isNotEmpty()) preparedAheadPlayIds.removeAt(0)

        // Drop whatever's already been played from the player's own list — otherwise it just
        // keeps growing by one item per song for the entire session instead of always staying
        // "current + up to PREPARE_AHEAD prepared".
        controller?.let { c -> repeat(c.currentMediaItemIndex) { c.removeMediaItem(0) } }

        if (newQueue.isNotEmpty()) {
            // Tops back up to PREPARE_AHEAD — reuses whatever's already prepared (the item that
            // used to be "2 ahead" is now "1 ahead") and only resolves the newly-opened slot.
            prepareUpcoming()
        } else if (resolved != null) {
            // Queue ran dry — refill exactly like a fresh playSong() would, from this song's
            // own related tracks/radio mix, so a long listening session never stalls out.
            _meta.update { it.copy(queue = resolved.related.map { t -> t.toSong() }.filterNot { s -> s.playId == newCurrent.playId }) }
            scope.launch { autoFillQueueFor(newCurrent, resolved) }
        }
        persistSnapshotSoon()
    }

    fun playFromList(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val target = songs[startIndex.coerceIn(songs.indices)]
        val rest = songs.subList(startIndex + 1, songs.size)
        playSong(target, queueContext = rest, autoFillQueue = rest.isEmpty())
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    /** Used by [SleepTimer] — pauses only if something is actually playing. */
    fun togglePlayPauseIfPlaying() {
        controller?.takeIf { it.isPlaying }?.pause()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _progress.update { it.copy(positionMs = ms) }
        persistSnapshotSoon()
    }

    /** History first, then the play count — in one coroutine, so the two never disagree about order. */
    private fun recordStart(song: Song) {
        app.appScope.launch {
            app.prefs.pushHistory(song)
            TasteProfile.recordPlay(song)
        }
    }

    /** A song you started hearing and skipped within seconds is a "not this" — a buffering song you gave up on isn't. */
    private fun noteEarlySkip() {
        val song = _meta.value.currentSong ?: return
        val position = controller?.currentPosition ?: _progress.value.positionMs
        val duration = controller?.duration?.takeIf { it > 0 } ?: _progress.value.durationMs
        if (position in EARLY_SKIP_MIN_MS until EARLY_SKIP_MAX_MS && (duration <= 0 || duration > 60_000)) {
            app.appScope.launch { TasteProfile.recordSkip(song) }
        }
    }

    fun next() {
        noteEarlySkip()
        val c = controller
        if (c != null && preparedAheadPlayIds.isNotEmpty() && c.mediaItemCount > c.currentMediaItemIndex + 1) {
            c.seekToNextMediaItem() // already buffered — instant, onMediaItemTransition syncs state
        } else {
            scope.launch { advance(userInitiated = true) }
        }
    }

    private fun advance(userInitiated: Boolean) {
        val s = _meta.value
        when {
            s.repeatMode == RepeatMode.ONE && !userInitiated -> {
                controller?.seekTo(0)
                controller?.play()
            }
            s.queue.isNotEmpty() -> {
                val nextSong = s.queue.first()
                val remaining = s.queue.drop(1)
                playSong(nextSong, queueContext = remaining, autoFillQueue = remaining.isEmpty())
            }
            s.repeatMode == RepeatMode.ALL && s.currentSong != null -> {
                playSong(s.currentSong, autoFillQueue = true)
            }
            else -> controller?.pause()
        }
    }

    fun previous() {
        val c = controller
        if (c != null && c.currentPosition > 3000) {
            c.seekTo(0)
            return
        }
        val prevSong = backStack.removeLastOrNull() ?: return
        val current = _meta.value.currentSong
        val requeue = if (current != null) listOf(current) + _meta.value.queue else _meta.value.queue
        // playSong() pushes `current` back onto backStack; pop it again so previous() doesn't grow the stack.
        playSong(prevSong, queueContext = requeue, autoFillQueue = false)
        backStack.removeLastOrNull()
    }

    fun toggleShuffle() {
        _meta.update { it.copy(shuffle = !it.shuffle, queue = if (!it.shuffle) it.queue.shuffled() else it.queue) }
        prepareUpcoming()
        persistSnapshotSoon()
    }

    fun cycleRepeat() {
        _meta.update {
            val next = when (it.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            it.copy(repeatMode = next)
        }
        persistSnapshotSoon()
    }

    /** Moves the queue item at [from] to [to] (e.g. a drag-reorder in the Queue screen) and
     *  reconciles the gapless pre-buffer if the reorder changed what's actually next — the
     *  player's own playlist otherwise wouldn't know the "next" song changed. */
    fun moveQueueItem(from: Int, to: Int) {
        val current = _meta.value.queue
        if (from !in current.indices || to !in current.indices || from == to) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        _meta.update { it.copy(queue = reordered) }
        prepareUpcoming()
        persistSnapshotSoon()
    }

    /** A song can be in the queue only once: the prepared-ahead bookkeeping, the resolve maps
     *  and [onGaplessAdvance]'s media-id matching are all keyed by playId, and the Queue screen
     *  keys its list the same way — a duplicate here crashed the app outright (LazyColumn:
     *  `Key "..." was already used`). Adding something already queued is simply a no-op. */
    fun addToQueue(song: Song) {
        if (_meta.value.queue.any { it.playId == song.playId }) return
        _meta.update { it.copy(queue = it.queue + song) }
        prepareUpcoming()
        persistSnapshotSoon()
    }

    fun removeFromQueue(song: Song) {
        _meta.update { it.copy(queue = it.queue.filterNot { q -> q.playId == song.playId }) }
        prepareUpcoming()
        persistSnapshotSoon()
    }

    fun clearQueue() {
        _meta.update { it.copy(queue = emptyList()) }
        prepareUpcoming()
        persistSnapshotSoon()
    }

    fun playQueueItemAt(index: Int) {
        val q = _meta.value.queue
        if (index !in q.indices) return
        val target = q[index]
        val remaining = q.toMutableList().apply { removeAt(index) }
        playSong(target, queueContext = remaining, autoFillQueue = remaining.isEmpty())
    }

    fun consumeError() {
        _meta.update { it.copy(error = null) }
    }

    /** Weaves each list in [secondaries] into [primary] every third slot instead of just appending them, so the blend doesn't read as "one song's radio, then a random tacked-on chunk". */
    private fun interleaveMany(primary: List<Song>, secondaries: List<List<Song>>): List<Song> {
        if (secondaries.all { it.isEmpty() }) return primary
        val leftovers = secondaries.map { ArrayDeque(it) }
        val result = mutableListOf<Song>()
        primary.forEachIndexed { index, song ->
            result.add(song)
            if ((index + 1) % 3 == 0) {
                leftovers.forEach { queue -> if (queue.isNotEmpty()) result.add(queue.removeFirst()) }
            }
        }
        leftovers.forEach { result.addAll(it) }
        return result.distinctBy { it.playId }
    }

    /** Same artist (or artist-component, for collab credits like "KR$NA, Seedhe Maut") never
     *  appears more than [max] times — the actual fix for "recommendations are just the same
     *  one or two artists", regardless of how narrow the underlying radio mix was. */
    private fun List<Song>.capPerArtist(max: Int): List<Song> {
        val counts = mutableMapOf<String, Int>()
        return filter { song ->
            val components = song.artist.lowercase()
                .split(Regex("""[,&/]|\bx\b|\bfeat\.?\b|\bft\.?\b|\bwith\b"""))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (components.isEmpty()) return@filter true
            if (components.any { (counts[it] ?: 0) >= max }) return@filter false
            components.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            true
        }
    }
}
