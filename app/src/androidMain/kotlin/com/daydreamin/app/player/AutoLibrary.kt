package com.daydreamin.app.player

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.model.Song
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ROOT = "root"
private const val MIX = "mix"
private const val LIKED = "liked"
private const val RECENT = "recent"
private const val PLAYLISTS = "playlists"
private const val PLAYLIST = "playlist/"
private const val SEARCH = "search"
private const val PICK_GRACE_MS = 2_000L
/** A song in a list: "song|<list>|<place in it>". */
private const val SONG = "song|"

/**
 * What Android Auto (and anything else that browses media) sees: your mix, liked songs, recently
 * played and playlists, plus search and voice. Picking a song doesn't go to the player directly:
 * [QueueAwarePlayer] hands it to [play], so it starts through [PlayerController] with its list as
 * the queue, like a tap in the app.
 */
class AutoLibrary(private val scope: CoroutineScope) : MediaLibrarySession.Callback {
    private var searchResults: List<Song> = emptyList()
    private var pickedAtMs = 0L

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Daydreamin"), params))

    override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> =
        answer { LibraryResult.ofItem(itemFor(mediaId) ?: folder(mediaId, mediaId), null) }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = answer {
        LibraryResult.ofItemList(paged(children(parentId), page, pageSize), params)
    }

    override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
        scope.launch {
            searchResults = AppGraph.repository.smartSearch(query).getOrNull().orEmpty().take(30)
            session.notifySearchResultChanged(browser, query, searchResults.size, params)
        }
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        Futures.immediateFuture(LibraryResult.ofItemList(paged(songItems(SEARCH, searchResults), page, pageSize), params))

    /** What was picked, or asked for by voice ("play Kesariya on Daydreamin"), made ready for [QueueAwarePlayer]. */
    override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
        val result = SettableFuture.create<MutableList<MediaItem>>()
        scope.launch {
            result.set(mediaItems.mapNotNullTo(mutableListOf()) { item ->
                val query = item.requestMetadata.searchQuery
                when {
                    item.mediaId.startsWith(SONG) -> item.buildUpon().setUri(Uri.parse("daydreamin://$SONG")).build()
                    !query.isNullOrBlank() -> {
                        searchResults = AppGraph.repository.smartSearch(query).getOrNull().orEmpty().take(30)
                        searchResults.firstOrNull()?.let { songItem(SEARCH, 0, it).buildUpon().setUri(Uri.parse("daydreamin://$SONG")).build() }
                    }
                    else -> item // the app's own controller: the real thing, with its stream
                }
            })
        }
        return result
    }

    /** Whether [item] is a song picked from this library, for [QueueAwarePlayer]. */
    fun owns(item: MediaItem): Boolean = item.mediaId.startsWith(SONG)

    /**
     * Right after a pick the car sends prepare and play too. Daydreamin starts the song itself once
     * it's loaded; those would only bring back the last song for a moment.
     */
    override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
        val justPicked = SystemClock.elapsedRealtime() - pickedAtMs < PICK_GRACE_MS
        val starting = playerCommand == Player.COMMAND_PLAY_PAUSE || playerCommand == Player.COMMAND_PREPARE
        return if (justPicked && starting && controller.packageName != session.token.packageName) SessionError.ERROR_NOT_SUPPORTED else SessionResult.RESULT_SUCCESS
    }

    /** Plays the picked song, with the rest of its list as the queue. */
    fun play(item: MediaItem) {
        pickedAtMs = SystemClock.elapsedRealtime()
        val (_, list, place) = item.mediaId.split('|')
        scope.launch {
            val songs = songsIn(list)
            withContext(Dispatchers.Main) { PlayerController.playFromList(songs, place.toInt()) }
        }
    }

    private suspend fun children(parentId: String): List<MediaItem> = when (parentId) {
        ROOT -> listOf(folder(MIX, "Your Daydream Mix"), folder(LIKED, "Liked songs"), folder(RECENT, "Recently played"), folder(PLAYLISTS, "Playlists"))
        PLAYLISTS -> AppGraph.prefs.playlists.first().map { folder(PLAYLIST + it.id, it.name, it.coverUrl ?: it.songs.firstOrNull()?.artworkUrl) }
        else -> songItems(parentId, songsIn(parentId))
    }

    private suspend fun songsIn(list: String): List<Song> = when {
        list == MIX -> AppGraph.homeFeed.feed.value?.heroMix.orEmpty()
        list == LIKED -> AppGraph.prefs.likedSongs.first()
        list == RECENT -> AppGraph.prefs.history.first()
        list == SEARCH -> searchResults
        list.startsWith(PLAYLIST) -> AppGraph.prefs.playlists.first().firstOrNull { it.id == list.removePrefix(PLAYLIST) }?.songs.orEmpty()
        else -> emptyList()
    }

    private suspend fun itemFor(mediaId: String): MediaItem? {
        if (!mediaId.startsWith(SONG)) return null
        val (_, list, place) = mediaId.split('|')
        return songsIn(list).getOrNull(place.toInt())?.let { songItem(list, place.toInt(), it) }
    }

    private fun songItems(list: String, songs: List<Song>) = songs.mapIndexed { i, s -> songItem(list, i, s) }

    private fun songItem(list: String, place: Int, song: Song): MediaItem =
        MediaItem.Builder()
            .setMediaId("$SONG$list|$place")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.artworkUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            )
            .build()

    private fun folder(id: String, title: String, art: String? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtworkUri(art?.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build(),
            )
            .build()

    private fun paged(items: List<MediaItem>, page: Int, pageSize: Int): ImmutableList<MediaItem> {
        val from = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
        return ImmutableList.copyOf(items.subList(from, (from + pageSize).coerceAtMost(items.size)))
    }

    private fun <T : Any> answer(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> {
        val result = SettableFuture.create<LibraryResult<T>>()
        scope.launch {
            val answer: LibraryResult<T> = try { block() } catch (e: Exception) { LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN) }
            result.set(answer)
        }
        return result
    }
}
