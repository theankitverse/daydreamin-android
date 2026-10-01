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
        val SEARCH_HISTORY_JSON = stringPreferencesKey("search_history_json")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val USER_NAME = stringPreferencesKey("user_name")
        val HAS_AVATAR = booleanPreferencesKey("has_avatar")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val ASKED_NOTIFICATIONS = booleanPreferencesKey("asked_notifications")
        val AVATAR_STAMP = androidx.datastore.preferences.core.longPreferencesKey("avatar_stamp")
        val AUTO_BACKUP_URI = stringPreferencesKey("auto_backup_uri")
        val AUTO_BACKUP_AT = androidx.datastore.preferences.core.longPreferencesKey("auto_backup_at")
        val DISMISSED_UPDATE_VERSION = intPreferencesKey("dismissed_update_version")
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

    /** Off unless you turn it on: most of what plays is mastered loud already, and boosting it costs clarity. */
    val audioNormalization: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUDIO_NORMALIZATION] ?: false }
    suspend fun setAudioNormalization(value: Boolean) = edit { it[Keys.AUDIO_NORMALIZATION] = value }

    val downloadWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.DOWNLOAD_WIFI_ONLY] ?: true }
    suspend fun setDownloadWifiOnly(value: Boolean) = edit { it[Keys.DOWNLOAD_WIFI_ONLY] = value }

    /** One of [com.daydreamin.app.player.EqPreset]'s names — stored as a plain string (not the
     *  enum directly) so this data layer doesn't need to depend on the player package's types. */
    val equalizerPreset: Flow<String> = context.dataStore.data.map { it[Keys.EQUALIZER_PRESET] ?: "Off" }
    suspend fun setEqualizerPreset(value: String) = edit { it[Keys.EQUALIZER_PRESET] = value }

    /** What the app calls you. Blank until you've told it. */
    val userName: Flow<String> = context.dataStore.data.map { it[Keys.USER_NAME].orEmpty() }
    suspend fun setUserName(value: String) = edit { it[Keys.USER_NAME] = value.trim().take(40) }

    /** The picture itself lives at [avatarFile]; this flips (and so re-emits) whenever it changes. */
    val avatarVersion: Flow<Long> = context.dataStore.data.map { if (it[Keys.HAS_AVATAR] == true) it[Keys.AVATAR_STAMP] ?: 1L else 0L }
    val avatarFile: java.io.File get() = java.io.File(context.filesDir, "avatar.jpg")
    suspend fun setAvatar(jpeg: ByteArray?) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (jpeg == null) avatarFile.delete() else avatarFile.writeBytes(jpeg)
        }
        edit { it[Keys.HAS_AVATAR] = jpeg != null; it[Keys.AVATAR_STAMP] = System.currentTimeMillis() }
    }

    /** First-run setup finished (or skipped). `null` until the store has been read. */
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] == true }
    suspend fun setOnboarded() = edit { it[Keys.ONBOARDED] = true }

    /** The notification permission is asked for once, ever — never again after an answer. */
    suspend fun claimNotificationAsk(): Boolean {
        var first = false
        context.dataStore.edit { if (it[Keys.ASKED_NOTIFICATIONS] != true) { first = true; it[Keys.ASKED_NOTIFICATIONS] = true } }
        return first
    }

    /** Where the automatic backup file lives (a MediaStore or document URI), and when it was last written. */
    val autoBackupUri: Flow<String?> = context.dataStore.data.map { it[Keys.AUTO_BACKUP_URI] }
    val autoBackupAt: Flow<Long> = context.dataStore.data.map { it[Keys.AUTO_BACKUP_AT] ?: 0L }
    suspend fun setAutoBackup(uri: String?, atMs: Long) = edit {
        if (uri == null) it.remove(Keys.AUTO_BACKUP_URI) else it[Keys.AUTO_BACKUP_URI] = uri
        it[Keys.AUTO_BACKUP_AT] = atMs
    }

    /** The versionCode of the update banner you last dismissed on Home — see [com.daydreamin.app.data.update.UpdateChecker]. */
    val dismissedUpdateVersion: Flow<Int> = context.dataStore.data.map { it[Keys.DISMISSED_UPDATE_VERSION] ?: 0 }
    suspend fun setDismissedUpdateVersion(code: Int) = edit { it[Keys.DISMISSED_UPDATE_VERSION] = code }

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

    /** What you've searched for, newest first — only searches you made, never the ones the app runs for itself. */
    val searchHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.SEARCH_HISTORY_JSON]?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()
    }

    suspend fun addSearch(query: String) = edit { prefs ->
        val current = prefs[Keys.SEARCH_HISTORY_JSON]?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()
        prefs[Keys.SEARCH_HISTORY_JSON] = Json.encodeToString(withSearch(current, query))
    }

    suspend fun removeSearch(query: String) = edit { prefs ->
        val current = prefs[Keys.SEARCH_HISTORY_JSON]?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()
        prefs[Keys.SEARCH_HISTORY_JSON] = Json.encodeToString(current.filterNot { it.equals(query, ignoreCase = true) })
    }

    suspend fun clearSearchHistory() = edit { it.remove(Keys.SEARCH_HISTORY_JSON) }

    /** Everything the user has built up, read in one consistent snapshot of the store. */
    suspend fun exportLibrary(): LibraryBackup {
        val prefs = context.dataStore.data.first()
        val avatar = if (prefs[Keys.HAS_AVATAR] == true) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { android.util.Base64.encodeToString(avatarFile.readBytes(), android.util.Base64.NO_WRAP) }.getOrNull()
            }
        } else null
        return readLibrary(prefs).copy(
            profile = ProfileBackup(
                name = prefs[Keys.USER_NAME]?.takeIf { it.isNotBlank() },
                accent = prefs[Keys.ACCENT_NAME],
                equalizer = prefs[Keys.EQUALIZER_PRESET],
                loudnessBoost = prefs[Keys.AUDIO_NORMALIZATION],
                offlineOnWifiOnly = prefs[Keys.DOWNLOAD_WIFI_ONLY],
                avatarJpegBase64 = avatar,
            ),
        )
    }

    /**
     * Applies a backup's profile — but only onto a device that hasn't been set up with its own
     * yet (no name), so importing someone's backup never overwrites who you are here.
     * Returns whether it was applied.
     */
    suspend fun applyProfileIfFresh(profile: ProfileBackup?): Boolean {
        if (profile == null) return false
        val current = context.dataStore.data.first()
        if (!current[Keys.USER_NAME].isNullOrBlank()) return false
        val avatar = profile.avatarJpegBase64?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }.getOrNull() }
        if (avatar != null) setAvatar(avatar)
        edit { p ->
            profile.name?.takeIf { it.isNotBlank() }?.let { p[Keys.USER_NAME] = it.take(40) }
            profile.accent?.let { p[Keys.ACCENT_NAME] = it }
            profile.equalizer?.let { p[Keys.EQUALIZER_PRESET] = it }
            profile.loudnessBoost?.let { p[Keys.AUDIO_NORMALIZATION] = it }
            profile.offlineOnWifiOnly?.let { p[Keys.DOWNLOAD_WIFI_ONLY] = it }
        }
        return true
    }

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
    /** Set when this was saved from somewhere else (a YouTube playlist, your Home mix) rather than made here. */
    val sourceUrl: String? = null,
    /** Who made it, for a saved playlist ("YouTube Music", a channel name). */
    val author: String? = null,
    val coverUrl: String? = null,
) {
    val isSaved: Boolean get() = sourceUrl != null
}

/** [query] moved to the front of [history]: trimmed, no case-insensitive repeats, at most [max] kept. */
internal fun withSearch(history: List<String>, query: String, max: Int = 15): List<String> {
    val q = query.trim().replace(Regex("\\s+"), " ")
    if (q.isEmpty()) return history
    return (listOf(q) + history.filterNot { it.equals(q, ignoreCase = true) }).take(max)
}
