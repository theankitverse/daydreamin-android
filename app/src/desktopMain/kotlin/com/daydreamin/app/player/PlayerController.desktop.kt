package com.daydreamin.app.player

import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.download.Downloads
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.repository.toSong
import com.daydreamin.app.data.taste.TasteProfile
import com.daydreamin.app.data.youtube.ResolvedStream
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import com.daydreamin.app.platform.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.Executors

private const val TAG = "PlaybackTiming"
private const val EARLY_SKIP_MIN_MS = 1_500L
private const val EARLY_SKIP_MAX_MS = 30_000L

/** A song ready to play: its audio and, when it came from YouTube, the stream details. */
private class Loaded(val deck: Deck, val resolved: ResolvedStream?, val quality: String)

/**
 * The desktop player. Same behaviour as Android's — Up Next filled from the song's radio, history
 * and play counts, likes and skips teaching the recommender, picking up where you left off, the
 * next song lined up while this one plays (gapless, or crossfaded when a song ends on its own),
 * and a song whose connection drops carrying on from the same spot once it's back — with
 * [AudioEngine] in place of Media3.
 *
 * All of the state below is only touched on one thread ([scope]); the engine reports in from its
 * mixer thread by posting to it.
 */
actual object PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor { Thread(it, "player").apply { isDaemon = true } }.asCoroutineDispatcher())

    private val _meta = MutableStateFlow(PlaybackMeta())
    actual val meta: StateFlow<PlaybackMeta> = _meta
    private val _progress = MutableStateFlow(PlaybackProgress())
    actual val progress: StateFlow<PlaybackProgress> = _progress

    private val engine by lazy { AudioEngine(engineListener) }
    private var started = false

    private val backStack = ArrayDeque<Song>()
    /** Bumped by every new song; work still in flight for an older one checks it and gives up. */
    private var playRequest = 0
    /** The playing deck's song, by deck key. */
    private val deckSongs = HashMap<String, Song>()
    private var deckCounter = 0L
    /** The song lined up after this one, and what it took to load it. */
    private var preparedFor: String? = null
    private var preparedQuality: String? = null
    private var prepareRequest = 0
    /** Restored or stopped: where to start when play is pressed (there's no deck yet). */
    private var pendingStartMs: Long? = null
    private var consecutiveFailures = 0

    private val json = Json { ignoreUnknownKeys = true }

    // ---- volume (desktop only: phones have their own volume buttons)

    private val settings = java.util.prefs.Preferences.userRoot().node("daydreamin")
    private val _volume = MutableStateFlow(settings.getFloat("volume", 1f).coerceIn(0f, 1f))
    /** The volume slider's position, 0–1. */
    val volume: StateFlow<Float> = _volume

    fun setVolume(level: Float) {
        val v = level.coerceIn(0f, 1f)
        _volume.value = v
        // Loudness is heard on a curve: squaring the slider makes its middle sound like the middle.
        engine.volume = v * v
        settings.putFloat("volume", v)
    }

    actual fun ensureConnected() {
        scope.launch {
            if (started) return@launch
            started = true
            val prefs = AppGraph.prefs
            launch { prefs.crossfadeSeconds.collect { engine.crossfadeMs = it * 1000L } }
            launch { prefs.equalizerPreset.collect { engine.eqPreset = EqPreset.fromLabel(it) } }
            launch { prefs.audioNormalization.collect { engine.boost = it } }
            engine.volume = _volume.value * _volume.value
            restore()
            tick()
        }
    }

    /** The app is closing: save where playback is (waits up to a second for it), then stop the sound. */
    actual fun disconnect() {
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(1_000) { kotlinx.coroutines.withContext(scope.coroutineContext) { persistNow() } }
        }
        engine.stop()
    }

    actual suspend fun warmUp(song: Song) {
        // Resolving it now means tapping it starts without the lookup.
        if (Downloads.isDownloaded(song.playId)) return
        runCatching { AppGraph.repository.resolveStream(song) }
    }

    // ---- choosing what plays

    actual fun playSong(song: Song, queueContext: List<Song>, autoFillQueue: Boolean) {
        scope.launch { startSong(song, queueContext.distinctBy { it.playId }.filterNot { it.playId == song.playId }, autoFillQueue, startMs = 0L) }
    }

    private suspend fun startSong(song: Song, queue: List<Song>, autoFillQueue: Boolean, startMs: Long, recordPlay: Boolean = true) {
        val previous = _meta.value.currentSong
        if (previous != null && previous.playId != song.playId) backStack.addLast(previous)
        val request = ++playRequest
        // The song you're leaving stops now, not when the new one has loaded.
        engine.stop()
        dropPrepared()
        pendingStartMs = null
        _meta.update { it.copy(currentSong = song, isBuffering = true, isPlaying = true, queue = queue, error = null, streamQuality = null) }
        _progress.value = PlaybackProgress(positionMs = startMs, durationMs = song.durationMs())
        if (recordPlay) recordStart(song)
        persistSoon()

        val loaded = load(song, startMs, request) ?: return
        if (request != playRequest) { loaded.deck.close(); return }
        consecutiveFailures = 0
        engine.play(loaded.deck)
        // Loaded: from here the engine itself reports any wait for audio (a slow connection).
        _meta.update { it.copy(streamQuality = loaded.quality, isPlaying = true, isBuffering = false) }
        _progress.update { it.copy(durationMs = loaded.deck.durationMs) }

        if (queue.isEmpty() && autoFillQueue) {
            val upNext = loaded.resolved?.related.orEmpty().map { it.toSong() }.filterNot { it.playId == song.playId }
            if (upNext.isNotEmpty()) _meta.update { it.copy(queue = upNext) }
            fillWithRadio(song, loaded.resolved?.videoId ?: Downloads.downloadedSong(song.playId)?.videoId)
        }
        prepareNext()
        persistNow()
    }

    /** Up Next from YouTube Music's radio for [song], in the background; lands only if it's still playing. */
    private fun fillWithRadio(song: Song, videoId: String?) {
        if (videoId == null) {
            // A downloaded song with no connection: carry on with the other downloads.
            val others = Downloads.items.value.values.map { it.song }.filter { it.playId != song.playId && Downloads.isDownloaded(it.playId) }.shuffled()
            if (others.isNotEmpty()) _meta.update { it.copy(queue = others) }
            return
        }
        scope.launch {
            val radio = runCatching { buildRadioQueue(song, videoId) }.getOrDefault(emptyList())
            Log.d(TAG, "up next for '${song.title}': ${radio.size} tracks from its radio")
            if (radio.isNotEmpty() && _meta.value.currentSong?.playId == song.playId) {
                _meta.update { it.copy(queue = radio) }
                prepareNext()
                persistNow()
            }
        }
    }

    /**
     * Gets [song]'s audio ready from [startMs]: the downloaded file if there is one, otherwise a
     * fresh YouTube stream. Waits out a dropped connection rather than failing the song; returns
     * null (having dealt with it) if this request was overtaken or the song can't be played.
     */
    private suspend fun load(song: Song, startMs: Long, request: Int): Loaded? {
        var waitedForNetwork = false
        while (true) {
            if (request != playRequest) return null
            try {
                return openDeck(song, startMs)
            } catch (e: Throwable) {
                if (request != playRequest) return null
                if (isNetworkError(e)) {
                    // Not this song's fault — skipping would just fail the next one too. Keep trying.
                    if (!waitedForNetwork) {
                        Log.w(TAG, "no connection for '${song.title}': ${e.message} — waiting")
                        _meta.update { it.copy(error = "Waiting for a connection…", isBuffering = true) }
                        waitedForNetwork = true
                    }
                    if (e is StreamExpiredException) YouTubeExtractorService.invalidateAllStreams()
                    delay(3_000)
                    continue
                }
                failed(song, e)
                return null
            }
        }
    }

    private suspend fun openDeck(song: Song, startMs: Long): Loaded {
        val file = Downloads.fileFor(song.playId)
        if (file != null) {
            val source = FileByteSource(file)
            val header = withContext(Dispatchers.IO) { readWebmHeader(source) }
            return Loaded(newDeck(song, source, header, startMs), null, "Downloaded")
        }
        val resolved = AppGraph.repository.resolveStream(song).getOrThrow()
        AppGraph.appScope.launch { TasteProfile.rememberVideoId(song, resolved.videoId) }
        val source = HttpByteSource(resolved.streamUrl)
        val header = withContext(Dispatchers.IO) { readWebmHeader(source) }
        val codec = resolved.codec.uppercase().removePrefix("WEBMA_").removePrefix("M4A_").replace("WEBM ", "")
        return Loaded(newDeck(song, source, header, startMs), resolved, "$codec · ${resolved.bitrateKbps} kbps")
    }

    private fun newDeck(song: Song, source: ByteSource, header: WebmHeader, startMs: Long): Deck {
        val key = "${++deckCounter}"
        synchronized(deckSongs) { deckSongs[key] = song }
        return Deck(key, source, header, startMs)
    }

    private fun songOf(deck: Deck): Song? = synchronized(deckSongs) { deckSongs[deck.key] }

    private fun isNetworkError(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { it is IOException || it is UnknownHostException } &&
            generateSequence(e) { it.cause }.none { it.message?.contains("no audio", ignoreCase = true) == true }

    /** The song itself can't be played (removed, region-locked, no audio): say so and move on — but not in an endless loop. */
    private fun failed(song: Song, e: Throwable) {
        Log.w(TAG, "can't play '${song.title}': ${e.message}")
        _meta.update { it.copy(error = "Couldn't play \"${song.title}\" right now", isBuffering = false) }
        if (++consecutiveFailures >= 3 || _meta.value.queue.isEmpty()) {
            _meta.update { it.copy(isPlaying = false) }
            return
        }
        scope.launch { delay(1_500); advance(userInitiated = false) }
    }

    /** Lines up the next song in the queue (audio and all) so it follows with no gap. */
    private fun prepareNext() {
        val want = if (_meta.value.repeatMode == RepeatMode.ONE) null else _meta.value.queue.firstOrNull()
        if (want?.playId == preparedFor) return
        dropPrepared()
        if (want == null) return
        val request = ++prepareRequest
        preparedFor = want.playId
        scope.launch {
            val loaded = runCatching { openDeck(want, 0L) }.getOrNull()
            if (loaded == null || request != prepareRequest || preparedFor != want.playId) { loaded?.deck?.close(); return@launch }
            preparedQuality = loaded.quality
            engine.setNext(loaded.deck)
            Log.d(TAG, "prepared ahead song='${want.title}'")
        }
    }

    private fun dropPrepared() {
        prepareRequest++
        preparedFor = null
        preparedQuality = null
        engine.setNext(null)
    }

    // ---- what the engine reports

    private val engineListener = object : EngineListener {
        override fun onTransition(from: Deck, to: Deck) { scope.launch { transitioned(from, to) } }
        override fun onEnded(deck: Deck) { scope.launch { if (songOf(deck)?.playId == _meta.value.currentSong?.playId) advance(userInitiated = false) } }
        override fun onError(deck: Deck, error: Throwable) { scope.launch { recover(deck, error) } }
        override fun onBuffering(buffering: Boolean) { _meta.update { it.copy(isBuffering = buffering) } }
    }

    /** The lined-up song took over (it was in the queue's first slot). */
    private fun transitioned(from: Deck, to: Deck) {
        val newSong = songOf(to) ?: return
        val previous = songOf(from)
        if (previous != null && previous.playId != newSong.playId) backStack.addLast(previous)
        playRequest++
        val queue = _meta.value.queue
        val at = queue.indexOfFirst { it.playId == newSong.playId }
        val remaining = if (at >= 0) queue.drop(at + 1) else queue
        _meta.update { it.copy(currentSong = newSong, queue = remaining, isBuffering = false, isPlaying = true, error = null, streamQuality = preparedQuality) }
        _progress.value = PlaybackProgress(0L, to.durationMs)
        preparedFor = null
        preparedQuality = null
        recordStart(newSong)
        if (remaining.isEmpty()) fillWithRadio(newSong, newSong.videoId ?: Downloads.downloadedSong(newSong.playId)?.videoId)
        prepareNext()
        persistSoon()
        Log.d(TAG, "gapless advance to '${newSong.title}'")
    }

    /**
     * The playing song's audio stopped coming: a dropped connection, a network change, or a URL
     * that expired. Fetch it again and carry on from the same spot; only a song that's actually
     * broken gets skipped.
     */
    private suspend fun recover(deck: Deck, error: Throwable) {
        val song = songOf(deck) ?: return
        if (song.playId != _meta.value.currentSong?.playId) return
        val position = deck.decodedPositionMs
        Log.w(TAG, "playback of '${song.title}' stopped at ${position}ms: ${error.javaClass.simpleName}: ${error.message}")
        if (!isNetworkError(error)) { failed(song, error); return }
        if (error is StreamExpiredException) YouTubeExtractorService.invalidateAllStreams()
        val request = ++playRequest
        dropPrepared()
        _meta.update { it.copy(isBuffering = true) }
        val loaded = load(song, position, request) ?: return
        if (request != playRequest) { loaded.deck.close(); return }
        engine.play(loaded.deck)
        _meta.update { it.copy(error = null, streamQuality = loaded.quality, isPlaying = true, isBuffering = false) }
        prepareNext()
    }

    // ---- transport

    actual fun playFromList(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val start = startIndex.coerceIn(songs.indices)
        val rest = songs.subList(start + 1, songs.size)
        playSong(songs[start], rest, autoFillQueue = rest.isEmpty())
    }

    actual fun togglePlayPause() {
        scope.launch {
            when {
                engine.current == null -> {
                    // Restored from last time (or stopped): load it and start from where it was.
                    val song = _meta.value.currentSong ?: return@launch
                    val at = pendingStartMs ?: _progress.value.positionMs
                    startSong(song, _meta.value.queue, autoFillQueue = _meta.value.queue.isEmpty(), startMs = at, recordPlay = false)
                }
                engine.playing -> { engine.pause(); _meta.update { it.copy(isPlaying = false) }; persistNow() }
                else -> { engine.resume(); _meta.update { it.copy(isPlaying = true) } }
            }
        }
    }

    actual fun togglePlayPauseIfPlaying() {
        scope.launch { if (engine.playing) { engine.pause(); _meta.update { it.copy(isPlaying = false) }; persistNow() } }
    }

    actual fun seekTo(ms: Long) {
        scope.launch {
            _progress.update { it.copy(positionMs = ms) }
            val playing = engine.current ?: run { pendingStartMs = ms; persistNow(); return@launch }
            val song = songOf(playing) ?: return@launch
            // Same file, new starting point: the cues take it straight to the right place.
            val deck = newDeck(song, playing.source, playing.header, ms.coerceIn(0, (playing.durationMs - 500).coerceAtLeast(0)))
            engine.replaceCurrent(deck)
            persistSoon()
        }
    }

    actual fun next() {
        scope.launch {
            noteEarlySkip()
            val want = _meta.value.queue.firstOrNull()
            if (want != null && preparedFor == want.playId && engine.skipToNext()) return@launch // already loaded: instant
            advance(userInitiated = true)
        }
    }

    private suspend fun advance(userInitiated: Boolean) {
        val s = _meta.value
        when {
            s.repeatMode == RepeatMode.ONE && !userInitiated && s.currentSong != null ->
                startSong(s.currentSong, s.queue, autoFillQueue = false, startMs = 0L)
            s.queue.isNotEmpty() -> {
                val remaining = s.queue.drop(1)
                startSong(s.queue.first(), remaining, autoFillQueue = remaining.isEmpty(), startMs = 0L)
            }
            s.repeatMode == RepeatMode.ALL && s.currentSong != null -> startSong(s.currentSong, emptyList(), autoFillQueue = true, startMs = 0L)
            else -> {
                engine.stop()
                pendingStartMs = 0L
                _meta.update { it.copy(isPlaying = false, isBuffering = false) }
                _progress.update { it.copy(positionMs = 0L) }
            }
        }
    }

    actual fun previous() {
        scope.launch {
            if (engine.current != null && engine.positionMs > 3_000) { seekTo(0); return@launch }
            val prev = backStack.removeLastOrNull() ?: run { seekTo(0); return@launch }
            val current = _meta.value.currentSong
            val requeue = if (current != null) listOf(current) + _meta.value.queue else _meta.value.queue
            startSong(prev, requeue, autoFillQueue = false, startMs = 0L)
            backStack.removeLastOrNull() // startSong put the song we left back on the stack; going back shouldn't grow it
        }
    }

    actual fun toggleShuffle() = edit { it.copy(shuffle = !it.shuffle, queue = if (!it.shuffle) it.queue.shuffled() else it.queue) }

    actual fun cycleRepeat() = edit {
        it.copy(repeatMode = when (it.repeatMode) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF })
    }

    actual fun moveQueueItem(from: Int, to: Int) = edit { m ->
        if (from !in m.queue.indices || to !in m.queue.indices || from == to) m
        else m.copy(queue = m.queue.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Each song is in the queue at most once (the Queue screen keys its list by song). */
    actual fun addToQueue(song: Song) = edit { m -> if (m.queue.any { it.playId == song.playId }) m else m.copy(queue = m.queue + song) }

    actual fun removeFromQueue(song: Song) = edit { m -> m.copy(queue = m.queue.filterNot { it.playId == song.playId }) }

    actual fun clearQueue() = edit { it.copy(queue = emptyList()) }

    actual fun playQueueItemAt(index: Int) {
        scope.launch {
            val q = _meta.value.queue
            if (index !in q.indices) return@launch
            val remaining = q.toMutableList().apply { removeAt(index) }
            startSong(q[index], remaining, autoFillQueue = remaining.isEmpty(), startMs = 0L)
        }
    }

    actual fun consumeError() { _meta.update { it.copy(error = null) } }

    /** A queue change: apply it, re-line-up the next song if that changed, and remember it. */
    private fun edit(change: (PlaybackMeta) -> PlaybackMeta) {
        scope.launch {
            _meta.update(change)
            prepareNext()
            persistSoon()
        }
    }

    // ---- remembering

    private fun recordStart(song: Song) {
        AppGraph.appScope.launch {
            AppGraph.prefs.pushHistory(song)
            TasteProfile.recordPlay(song)
            AppGraph.prefs.countPlay()
        }
    }

    /** A song you started hearing and skipped within seconds is a "not this" — a buffering song you gave up on isn't. */
    private fun noteEarlySkip() {
        val song = _meta.value.currentSong ?: return
        val position = engine.positionMs
        val duration = _progress.value.durationMs
        if (engine.current != null && position in EARLY_SKIP_MIN_MS until EARLY_SKIP_MAX_MS && (duration <= 0 || duration > 60_000)) {
            AppGraph.appScope.launch { TasteProfile.recordSkip(song) }
        }
    }

    /** Twice a second: where the song is; every few seconds while playing, saved for next time. */
    private suspend fun tick() {
        var n = 0
        while (true) {
            delay(500)
            if (engine.current != null) {
                _progress.update { it.copy(positionMs = engine.positionMs, durationMs = engine.current?.durationMs ?: it.durationMs) }
                if (engine.playing && ++n % 10 == 0) persistNow()
            }
        }
    }

    private suspend fun restore() {
        val saved = AppGraph.prefs.playbackSnapshotJson.first() ?: return
        val snapshot = runCatching { json.decodeFromString(PlaybackSnapshot.serializer(), saved) }.getOrNull() ?: return
        val song = snapshot.currentSong ?: return
        if (_meta.value.currentSong != null) return
        _meta.update { it.copy(currentSong = song, queue = snapshot.queue, shuffle = snapshot.shuffle, repeatMode = snapshot.repeatMode, isPlaying = false) }
        _progress.value = PlaybackProgress(snapshot.positionMs, song.durationMs())
        pendingStartMs = snapshot.positionMs
    }

    private suspend fun persistNow() {
        val m = _meta.value
        val song = m.currentSong ?: run { AppGraph.prefs.clearPlaybackSnapshot(); return }
        val position = if (engine.current != null) engine.positionMs else pendingStartMs ?: _progress.value.positionMs
        val snapshot = PlaybackSnapshot(song, m.queue, position, m.shuffle, m.repeatMode)
        runCatching { json.encodeToString(PlaybackSnapshot.serializer(), snapshot) }.getOrNull()?.let { AppGraph.prefs.savePlaybackSnapshotJson(it) }
    }

    private fun persistSoon() { scope.launch { persistNow() } }

    private fun Song.durationMs(): Long = duration * 1000L
}
