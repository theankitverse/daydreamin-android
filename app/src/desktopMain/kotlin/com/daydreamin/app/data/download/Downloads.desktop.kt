package com.daydreamin.app.data.download

import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.youtube.YouTubeExtractorService
import com.daydreamin.app.platform.Log
import com.daydreamin.app.player.HttpByteSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "Downloads"
private const val PARALLEL = 2

@Serializable
private data class StoredDownload(val song: Song, val file: String, val bytes: Long)

/**
 * Songs kept on this computer: each one's audio saved as its own file under the app's data folder
 * (downloads/), listed in downloads/index.json. What's kept follows the same choices as on
 * Android — songs you downloaded, downloaded playlists, and liked songs when that's turned on.
 */
actual object Downloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val dir: File get() = File(AppGraph.filesDir, "downloads").apply { mkdirs() }
    /** Where the songs are kept (Settings opens it). */
    val folder: File get() = dir
    private val indexFile: File get() = File(dir, "index.json")
    private val indexLock = Mutex()
    private val running = HashMap<String, Job>()
    private val kick = MutableStateFlow(0)

    private val _items = MutableStateFlow<Map<String, DownloadInfo>>(emptyMap())
    actual val items: StateFlow<Map<String, DownloadInfo>> = _items

    /** Called once at startup: loads what's on disk and keeps downloads in step with your choices. */
    fun init() {
        val stored = runCatching { json.decodeFromString(ListSerializer(StoredDownload.serializer()), indexFile.readText()) }.getOrDefault(emptyList())
        _items.value = stored.filter { File(dir, it.file).isFile }.associate { it.song.playId to DownloadInfo(it.song, DownloadStatus.DONE, 100f, it.bytes) }
        scope.launch { keepInStep() }
    }

    /** The downloaded audio for [playId], if it's here. */
    fun fileFor(playId: String): File? =
        _items.value[playId]?.takeIf { it.status == DownloadStatus.DONE }?.let { fileName(it.song) }?.takeIf { it.isFile }

    actual fun isDownloaded(playId: String): Boolean = fileFor(playId) != null
    actual fun downloadedSong(playId: String): Song? = _items.value[playId]?.takeIf { it.status == DownloadStatus.DONE }?.song
    actual fun bytesUsed(): Long = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".webm") }?.sumOf { it.length() } ?: 0L

    actual fun download(song: Song): Job = scope.launch { AppGraph.prefs.setSongDownloaded(song, true) }

    actual suspend fun remove(song: Song): String? {
        val prefs = AppGraph.prefs
        prefs.setSongDownloaded(song, false)
        if (prefs.downloadLiked.first() && prefs.likedSongs.first().any { it.playId == song.playId }) return "Liked songs"
        val ids = prefs.downloadedPlaylists.first()
        return prefs.playlists.first().firstOrNull { it.id in ids && it.songs.any { s -> s.playId == song.playId } }?.name
    }

    actual fun retryFailed() { kick.value++ }

    actual suspend fun removeAll() {
        AppGraph.prefs.clearAllDownloads()
    }

    @OptIn(FlowPreview::class)
    private suspend fun keepInStep() {
        val prefs = AppGraph.prefs
        combine(
            combine(prefs.downloadedSongs, prefs.downloadLiked, prefs.likedSongs) { manual, likedOn, liked -> Triple(manual, likedOn, liked) },
            combine(prefs.downloadedPlaylists, prefs.playlists) { ids, playlists -> playlists.filter { it.id in ids }.flatMap { it.songs } },
            kick,
        ) { (manual, likedOn, liked), playlistSongs, _ ->
            val songs = LinkedHashMap<String, Song>()
            manual.forEach { songs[it.playId] = it }
            if (likedOn) liked.forEach { songs.putIfAbsent(it.playId, it) }
            playlistSongs.forEach { songs.putIfAbsent(it.playId, it) }
            songs
        }.debounce(600).collect { wanted -> runCatching { reconcile(wanted) }.onFailure { Log.w(TAG, "couldn't update downloads", it) } }
    }

    private suspend fun reconcile(wanted: Map<String, Song>) {
        // No longer wanted: stop it if it's downloading, and delete the file.
        for ((playId, info) in _items.value) {
            if (playId in wanted) continue
            synchronized(running) { running.remove(playId) }?.cancel()
            fileName(info.song).delete()
            update(playId, null)
        }
        saveIndex()
        val gate = Semaphore(PARALLEL)
        for (song in wanted.values) {
            val status = _items.value[song.playId]?.status
            if (status == DownloadStatus.DONE || status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED) continue
            update(song.playId, DownloadInfo(song, DownloadStatus.QUEUED, 0f, 0))
            val job = scope.launch { gate.withPermit { fetch(song) } }
            synchronized(running) { running[song.playId] = job }
        }
    }

    private suspend fun fetch(song: Song) {
        try {
            val videoId = song.videoId?.takeIf { it.isNotBlank() }
                ?: AppGraph.repository.resolveStream(song).getOrNull()?.videoId
                ?: throw IllegalStateException("couldn't find it on YouTube")
            val stored = song.copy(videoId = videoId)
            val stream = YouTubeExtractorService.resolveVideoId(videoId)
            val source = HttpByteSource(stream.streamUrl)
            val target = fileName(stored)
            val partial = File(dir, target.name + ".part")
            withContext(Dispatchers.IO) {
                val total = source.size
                var done = 0L
                var lastShown = 0L
                source.open(0).use { input ->
                    partial.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (done - lastShown > 256 * 1024) {
                                lastShown = done
                                update(song.playId, DownloadInfo(stored, DownloadStatus.DOWNLOADING, done * 100f / total, done))
                            }
                        }
                    }
                }
                replaceFile(partial, target)
                update(song.playId, DownloadInfo(stored, DownloadStatus.DONE, 100f, target.length()))
            }
            saveIndex()
            Log.d(TAG, "downloaded '${song.title}'")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "download failed for '${song.title}': ${e.message}")
            update(song.playId, DownloadInfo(song, DownloadStatus.FAILED, 0f, 0))
        } finally {
            synchronized(running) { running.remove(song.playId) }
        }
    }

    /** Moves [from] over [to], replacing it (a plain rename can't replace a file on Windows). */
    private fun replaceFile(from: File, to: File) {
        java.nio.file.Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun fileName(song: Song) = File(dir, song.playId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".webm")

    private fun update(playId: String, info: DownloadInfo?) {
        _items.value = _items.value.toMutableMap().apply { if (info == null) remove(playId) else put(playId, info) }
    }

    private suspend fun saveIndex() = indexLock.withLock {
        val done = _items.value.values.filter { it.status == DownloadStatus.DONE }
            .map { StoredDownload(it.song, fileName(it.song).name, it.bytes) }
        withContext(Dispatchers.IO) {
            val tmp = File(dir, "index.json.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(StoredDownload.serializer()), done))
            replaceFile(tmp, indexFile)
        }
    }
}
