package com.daydreamin.app.data.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import com.daydreamin.app.player.MediaDatabase
import com.daydreamin.app.player.NetworkWatch
import com.daydreamin.app.player.streamDataSourceFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors


/**
 * Songs kept on the phone for listening offline.
 *
 * Built on Media3's DownloadManager, so downloads run in the background, resume after a dropped
 * connection or a restart, and show progress. The audio goes into its own store under the app's
 * files — never evicted, and never cleared by Android the way a cache can be. That's the difference
 * from the streaming cache, which forgets old songs to make room and is keyed by details only an
 * online lookup knows. A download is keyed by the song alone ("dl:<playId>"), so it plays with no
 * connection at all.
 *
 * YouTube's stream links expire, so a download never stores one: its address is
 * `daydreamin://stream/<videoId>`, turned into a live link each time the download (re)starts.
 *
 * What should be downloaded is worked out from what you chose — single songs, liked songs, whole
 * playlists — and kept in step: like a song and it downloads; remove a playlist's download and its
 * songs go, unless something else still wants them.
 */
@UnstableApi
actual object Downloads {
    private const val TAG = "Downloads"
    const val SCHEME = "daydreamin"
    private const val PARALLEL = 3

    fun uriFor(videoId: String): Uri = Uri.parse("$SCHEME://stream/$videoId")
    fun cacheKeyFor(playId: String) = "dl:$playId"

    private lateinit var app: Context
    private lateinit var scope: CoroutineScope
    @Volatile private var cache: SimpleCache? = null
    @Volatile private var manager: DownloadManager? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow<Map<String, DownloadInfo>>(emptyMap())
    /** Every download, by the song's playId. */
    actual val items: StateFlow<Map<String, DownloadInfo>> = _items

    /** Songs being looked up before their download can be queued — shown as queued straight away. */
    private val pending = ConcurrentHashMap<String, Song>()
    private val ready = CompletableDeferred<Unit>()
    private var poller: Job? = null
    private val kick = MutableStateFlow(0)

    /** Called once from DaydreaminApp.onCreate, on the main thread. */
    fun init(context: Context, appScope: CoroutineScope) {
        app = context.applicationContext
        scope = appScope
        manager(app).resumeDownloads() // in case Android's daily limit paused them last time
        appScope.launch(Dispatchers.Main) { keepInStep() }
    }

    fun cache(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(File(context.applicationContext.filesDir, "downloads"), NoOpCacheEvictor(), MediaDatabase.get(context))
                .also { cache = it }
        }

    /**
     * Fetches download addresses from the network: `daydreamin://stream/<id>` becomes a live stream
     * link. The first attempt takes a recently looked-up link (a song you just played is ready
     * instantly); a retry or resume asks YouTube for a fresh one, since a failure usually means the
     * old link expired or the network changed.
     */
    fun upstream(context: Context): DataSource.Factory {
        val attempts = ConcurrentHashMap<String, Long>()
        return ResolvingDataSource.Factory(streamDataSourceFactory(context)) { spec ->
            if (spec.uri.scheme != SCHEME) return@Factory spec
            val videoId = spec.uri.lastPathSegment ?: return@Factory spec
            val now = System.currentTimeMillis()
            val retry = attempts.put(videoId, now)?.let { now - it < 10 * 60_000L } == true
            val stream = runBlocking { YouTubeExtractorService.resolveVideoId(videoId, maxAgeMs = if (retry) 0L else 10 * 60_000L) }
            spec.withUri(Uri.parse(stream.streamUrl))
        }
    }

    fun manager(context: Context): DownloadManager =
        manager ?: synchronized(this) {
            manager ?: DownloadManager(
                context.applicationContext,
                MediaDatabase.get(context),
                cache(context),
                upstream(context),
                Executors.newFixedThreadPool(PARALLEL),
            ).apply {
                maxParallelDownloads = PARALLEL
                minRetryCount = 10 // rides out a signal drop or a network switch mid-download
                addListener(object : DownloadManager.Listener {
                    override fun onInitialized(downloadManager: DownloadManager) {
                        refresh()
                        ready.complete(Unit)
                    }

                    override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                        if (download.state == Download.STATE_FAILED) Log.w(TAG, "download failed: ${download.request.id}", finalException)
                        if (download.state == Download.STATE_COMPLETED) Log.d(TAG, "downloaded ${download.request.id} (${download.bytesDownloaded} bytes)")
                        refresh()
                    }

                    override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) = refresh()
                })
            }.also { manager = it }
        }

    /** Downloads know what's on the phone only once their index has loaded; playback waits briefly for it. */
    suspend fun awaitReady() { withTimeoutOrNull(3_000) { ready.await() } }

    actual fun isDownloaded(playId: String): Boolean = _items.value[playId]?.status == DownloadStatus.DONE

    /** The song as stored with its download (it always has a videoId). */
    actual fun downloadedSong(playId: String): Song? = _items.value[playId]?.takeIf { it.status == DownloadStatus.DONE }?.song

    /** Space the downloads take, in bytes. */
    actual fun bytesUsed(): Long = runCatching { cache?.cacheSpace ?: 0L }.getOrDefault(0L)

    // ---- what you ask for

    actual fun download(song: Song) = scope.launch { DaydreaminApp.instance.prefs.setSongDownloaded(song, true) }

    /** Stops keeping [song] by itself. Returns what else still keeps it downloaded, if anything. */
    actual suspend fun remove(song: Song): String? {
        val prefs = DaydreaminApp.instance.prefs
        prefs.setSongDownloaded(song, false)
        if (prefs.downloadLiked.first() && prefs.likedSongs.first().any { it.playId == song.playId }) return "Liked songs"
        val ids = prefs.downloadedPlaylists.first()
        return prefs.playlists.first().firstOrNull { it.id in ids && it.songs.any { s -> s.playId == song.playId } }?.name
    }

    actual fun retryFailed() { kick.value++ }

    /** The app came to the front: carry on with anything unfinished, with its notification, and retry failures. */
    fun onAppVisible() {
        val m = manager ?: return
        m.resumeDownloads()
        if (m.currentDownloads.isNotEmpty()) startService()
        retryFailed()
    }

    actual suspend fun removeAll() {
        DaydreaminApp.instance.prefs.clearAllDownloads()
        withContext(Dispatchers.Main) { manager?.removeAllDownloads() }
    }

    // ---- keeping downloads in step with what you chose

    @OptIn(FlowPreview::class)
    private suspend fun keepInStep() {
        val prefs = DaydreaminApp.instance.prefs
        val wanted = combine(
            combine(prefs.downloadedSongs, prefs.downloadLiked, prefs.likedSongs) { manual, likedOn, liked -> Triple(manual, likedOn, liked) },
            combine(prefs.downloadedPlaylists, prefs.playlists) { ids, playlists -> playlists.filter { it.id in ids }.flatMap { it.songs } },
            prefs.downloadWifiOnly,
            kick,
        ) { (manual, likedOn, liked), playlistSongs, wifiOnly, _ ->
            val songs = LinkedHashMap<String, Song>()
            manual.forEach { songs[it.playId] = it }
            if (likedOn) liked.forEach { songs.putIfAbsent(it.playId, it) }
            playlistSongs.forEach { songs.putIfAbsent(it.playId, it) }
            songs to wifiOnly
        }
        awaitReady()
        wanted.debounce(600).collect { (songs, wifiOnly) ->
            runCatching { reconcile(songs, wifiOnly) }.onFailure { Log.w(TAG, "couldn't update downloads", it) }
        }
    }

    private suspend fun reconcile(wanted: Map<String, Song>, wifiOnly: Boolean) {
        val m = manager(app)
        m.requirements = Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
        val have = _items.value
        have.keys.filter { it !in wanted && !pending.containsKey(it) }.forEach { m.removeDownload(it) }
        val missing = wanted.values.filter { song ->
            val status = have[song.playId]?.status
            (status == null || status == DownloadStatus.FAILED) && !pending.containsKey(song.playId)
        }
        if (missing.isEmpty()) return
        Log.d(TAG, "queueing ${missing.size} download(s)")
        missing.forEach { pending[it.playId] = it }
        refresh()
        startService()
        val gate = Semaphore(PARALLEL)
        missing.map { song ->
            scope.launch {
                gate.withPermit {
                    try {
                        val request = requestFor(song)
                        if (request != null) withContext(Dispatchers.Main) { m.addDownload(request) }
                    } finally {
                        pending.remove(song.playId)
                    }
                }
                refresh()
            }
        }
    }

    /** The download for [song]; looks its video up first if it came from a chart or search without one. */
    private suspend fun requestFor(song: Song): DownloadRequest? {
        val videoId = song.videoId?.takeIf { it.isNotBlank() }
            ?: withContext(Dispatchers.IO) {
                if (!NetworkWatch.isOnline(app)) null
                else DaydreaminApp.instance.repository.resolveStream(song).getOrNull()?.videoId
            }
            ?: return null.also { Log.w(TAG, "couldn't find '${song.title}' to download") }
        val stored = song.copy(videoId = videoId)
        return DownloadRequest.Builder(song.playId, uriFor(videoId))
            .setCustomCacheKey(cacheKeyFor(song.playId))
            .setData(json.encodeToString(Song.serializer(), stored).toByteArray())
            .build()
    }

    /** Runs downloads in a service with a progress notification, so they carry on with the app closed. */
    fun startService() {
        // Allowed only while the app is in the foreground; otherwise downloads still run while the
        // app's process does, and the service picks them up next time the app is opened.
        runCatching { DownloadService.start(app, DaydreaminDownloadService::class.java) }
    }

    // ---- state for the screens

    private fun refresh() {
        val m = manager ?: return
        scope.launch(Dispatchers.Main) {
            val map = LinkedHashMap<String, DownloadInfo>()
            runCatching {
                m.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) cursor.download.toInfo()?.let { map[it.song.playId] = it }
                }
            }
            // The index lags in-progress numbers; the live list is current.
            m.currentDownloads.forEach { d -> d.toInfo()?.let { map[it.song.playId] = it } }
            pending.values.forEach { song -> if (song.playId !in map) map[song.playId] = DownloadInfo(song, DownloadStatus.QUEUED, 0f, 0) }
            _items.value = map
            if (map.values.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }) pollProgress()
        }
    }

    /** Download progress isn't announced as it changes, so it's read twice a second while anything is downloading. */
    private fun pollProgress() {
        if (poller?.isActive == true) return
        poller = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(500)
                val m = manager ?: break
                val live = m.currentDownloads.mapNotNull { it.toInfo() }
                if (live.isEmpty() && pending.isEmpty()) { refresh(); break }
                _items.value = _items.value.toMutableMap().apply { live.forEach { put(it.song.playId, it) } }
            }
        }
    }

    private fun Download.toInfo(): DownloadInfo? {
        val song = runCatching { json.decodeFromString(Song.serializer(), String(request.data)) }.getOrNull() ?: return null
        val status = when (state) {
            Download.STATE_COMPLETED -> DownloadStatus.DONE
            Download.STATE_DOWNLOADING -> DownloadStatus.DOWNLOADING
            Download.STATE_FAILED -> DownloadStatus.FAILED
            Download.STATE_REMOVING -> return null
            else -> DownloadStatus.QUEUED // queued, stopped, restarting, waiting for a connection
        }
        val percent = if (status == DownloadStatus.DONE) 100f else percentDownloaded.takeIf { it >= 0 } ?: 0f
        return DownloadInfo(song, status, percent, bytesDownloaded)
    }
}
