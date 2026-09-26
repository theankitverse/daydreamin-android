package com.daydreamin.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.daydreamin.app.data.model.Song

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "daydreamin_prefs")

/**
 * Everything Daydreamin persists on-device: theme choices and the local-only "Library"
 * (liked songs, history, playlists). There is no backend of ours to sync with — search,
 * lyrics and playback all resolve directly against public APIs / YouTube on-device — so
 * this is the only place any of this state lives.
 */
class AppPreferences(private val context: Context) {

    private object Keys {
        val ACCENT_NAME = stringPreferencesKey("accent_name")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val DYNAMIC_THEMING = booleanPreferencesKey("dynamic_theming")
        val BACKGROUND_STYLE = stringPreferencesKey("background_style")
        val NOW_PLAYING_STYLE = stringPreferencesKey("now_playing_style")
        val STREAMING_QUALITY = stringPreferencesKey("streaming_quality")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val AUDIO_NORMALIZATION = booleanPreferencesKey("audio_normalization")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val LIKED_IDS = stringSetPreferencesKey("liked_ids")
        val LIKED_SONGS_JSON = stringSetPreferencesKey("liked_songs_json")
        val HISTORY_JSON = stringSetPreferencesKey("history_json")
        val PLAYLISTS_JSON = stringPreferencesKey("playlists_json")
        val PLAYBACK_SNAPSHOT_JSON = stringPreferencesKey("playback_snapshot_json")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
    }

    val accentName: Flow<String> = context.dataStore.data.map { it[Keys.ACCENT_NAME] ?: "Violet" }
    suspend fun setAccentName(name: String) = edit { it[Keys.ACCENT_NAME] = name }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.DARK_MODE] ?: true }
    suspend fun setDarkMode(value: Boolean) = edit { it[Keys.DARK_MODE] = value }

    val dynamicTheming: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_THEMING] ?: true }
    suspend fun setDynamicTheming(value: Boolean) = edit { it[Keys.DYNAMIC_THEMING] = value }

    val backgroundStyle: Flow<String> = context.dataStore.data.map { it[Keys.BACKGROUND_STYLE] ?: "Default" }
    suspend fun setBackgroundStyle(value: String) = edit { it[Keys.BACKGROUND_STYLE] = value }

    val nowPlayingStyle: Flow<String> = context.dataStore.data.map { it[Keys.NOW_PLAYING_STYLE] ?: "Classic" }
    suspend fun setNowPlayingStyle(value: String) = edit { it[Keys.NOW_PLAYING_STYLE] = value }

    val streamingQuality: Flow<String> = context.dataStore.data.map { it[Keys.STREAMING_QUALITY] ?: "Auto" }
    suspend fun setStreamingQuality(value: String) = edit { it[Keys.STREAMING_QUALITY] = value }

    val crossfadeSeconds: Flow<Int> = context.dataStore.data.map { it[Keys.CROSSFADE_SECONDS] ?: 3 }
    suspend fun setCrossfadeSeconds(value: Int) = edit { it[Keys.CROSSFADE_SECONDS] = value }

    val audioNormalization: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUDIO_NORMALIZATION] ?: true }
    suspend fun setAudioNormalization(value: Boolean) = edit { it[Keys.AUDIO_NORMALIZATION] = value }

    val downloadWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.DOWNLOAD_WIFI_ONLY] ?: true }
    suspend fun setDownloadWifiOnly(value: Boolean) = edit { it[Keys.DOWNLOAD_WIFI_ONLY] = value }

    /** One of [com.daydreamin.app.player.EqPreset]'s names — stored as a plain string (not the
     *  enum directly) so this data layer doesn't need to depend on the player package's types. */
    val equalizerPreset: Flow<String> = context.dataStore.data.map { it[Keys.EQUALIZER_PRESET] ?: "Off" }
    suspend fun setEqualizerPreset(value: String) = edit { it[Keys.EQUALIZER_PRESET] = value }

    val likedIds: Flow<Set<String>> = context.dataStore.data.map { it[Keys.LIKED_IDS] ?: emptySet() }

    val likedSongs: Flow<List<Song>> = context.dataStore.data.map { prefs ->
        (prefs[Keys.LIKED_SONGS_JSON] ?: emptySet())
            .mapNotNull { runCatching { Json.decodeFromString<Song>(it) }.getOrNull() }
    }

    suspend fun toggleLiked(song: Song) {
        context.dataStore.edit { prefs ->
            val ids = (prefs[Keys.LIKED_IDS] ?: emptySet()).toMutableSet()
            val songsJson = (prefs[Keys.LIKED_SONGS_JSON] ?: emptySet()).toMutableSet()
            if (song.playId in ids) {
                ids.remove(song.playId)
                songsJson.removeAll { runCatching { Json.decodeFromString<Song>(it).playId }.getOrNull() == song.playId }
            } else {
                ids.add(song.playId)
                songsJson.add(Json.encodeToString(song))
            }
            prefs[Keys.LIKED_IDS] = ids
            prefs[Keys.LIKED_SONGS_JSON] = songsJson
        }
    }

    val history: Flow<List<Song>> = context.dataStore.data.map { prefs ->
        (prefs[Keys.HISTORY_JSON] ?: emptySet())
            .mapNotNull { runCatching { Json.decodeFromString<Song>(it) }.getOrNull() }
    }

    suspend fun pushHistory(song: Song) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.HISTORY_JSON] ?: emptySet())
                .mapNotNull { runCatching { Json.decodeFromString<Song>(it) }.getOrNull() }
                .filterNot { it.playId == song.playId }
            val updated = (listOf(song) + current).take(HISTORY_LIMIT)
            prefs[Keys.HISTORY_JSON] = updated.map { Json.encodeToString(it) }.toSet()
        }
    }

    suspend fun clearHistory() = edit { it[Keys.HISTORY_JSON] = emptySet() }

    val playlists: Flow<List<Playlist>> = context.dataStore.data.map { prefs ->
        prefs[Keys.PLAYLISTS_JSON]?.let {
            runCatching { Json.decodeFromString<List<Playlist>>(it) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun savePlaylists(playlists: List<Playlist>) = edit {
        it[Keys.PLAYLISTS_JSON] = Json.encodeToString(playlists)
    }

    /**
     * Raw JSON for the playback-restoration snapshot (current track, queue, position, shuffle,
     * repeat) — kept as an opaque string here so this data layer doesn't need to know the
     * player package's types; [com.daydreamin.app.player.PlayerController] owns the shape and
     * (de)serialization.
     */
    val playbackSnapshotJson: Flow<String?> = context.dataStore.data.map { it[Keys.PLAYBACK_SNAPSHOT_JSON] }

    suspend fun savePlaybackSnapshotJson(json: String) = edit { it[Keys.PLAYBACK_SNAPSHOT_JSON] = json }

    suspend fun clearPlaybackSnapshot() = edit { it.remove(Keys.PLAYBACK_SNAPSHOT_JSON) }

    /** Everything the user has built up, read in one consistent snapshot of the store. */
    suspend fun exportLibrary(): LibraryBackup = readLibrary(context.dataStore.data.first())

    /**
     * Merges [incoming] into the library in a single DataStore edit — liked ids, liked songs,
     * history and playlists all change together or not at all, so an import interrupted partway
     * (or racing a like/unlike) can't leave them out of step with each other. See
     * [mergeLibraries] for the rules; nothing already here is ever removed.
     */
    suspend fun importLibrary(incoming: LibraryBackup): ImportSummary {
        var summary = ImportSummary(0, 0, 0, 0)
        context.dataStore.edit { prefs ->
            val (merged, result) = mergeLibraries(readLibrary(prefs), incoming)
            writeLibrary(prefs, merged)
            summary = result
        }
        return summary
    }

    private fun readLibrary(prefs: Preferences): LibraryBackup {
        fun decodeSongs(key: androidx.datastore.preferences.core.Preferences.Key<Set<String>>): List<Song> =
            (prefs[key] ?: emptySet()).mapNotNull { runCatching { Json.decodeFromString<Song>(it) }.getOrNull() }
        return emptyBackup().copy(
            exportedAtMs = System.currentTimeMillis(),
            likedSongs = decodeSongs(Keys.LIKED_SONGS_JSON),
            history = decodeSongs(Keys.HISTORY_JSON),
            playlists = prefs[Keys.PLAYLISTS_JSON]?.let { runCatching { Json.decodeFromString<List<Playlist>>(it) }.getOrNull() } ?: emptyList(),
        )
    }

    private fun writeLibrary(prefs: androidx.datastore.preferences.core.MutablePreferences, library: LibraryBackup) {
        prefs[Keys.LIKED_IDS] = library.likedSongs.map { it.playId }.toSet()
        prefs[Keys.LIKED_SONGS_JSON] = library.likedSongs.map { Json.encodeToString(it) }.toSet()
        prefs[Keys.HISTORY_JSON] = library.history.map { Json.encodeToString(it) }.toSet()
        prefs[Keys.PLAYLISTS_JSON] = Json.encodeToString(library.playlists)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}

@kotlinx.serialization.Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList(),
)
