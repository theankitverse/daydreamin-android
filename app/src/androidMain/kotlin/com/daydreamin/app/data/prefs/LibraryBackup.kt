package com.daydreamin.app.data.prefs

import com.daydreamin.app.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val BACKUP_APP_ID = "daydreamin"
const val BACKUP_VERSION = 1
const val HISTORY_LIMIT = 200

/**
 * A portable snapshot of everything the user has built up in the app — liked songs, playlists
 * and play history — as a plain JSON file they can keep anywhere. Everything else this app
 * stores is either trivially re-set (theme, EQ) or rebuilt on its own (queue, audio cache).
 *
 * [app] and [version] deliberately have no defaults: a JSON file that lacks them isn't one of
 * ours, and defaulting them would let an arbitrary `{}` "import" successfully as an empty
 * library instead of being rejected.
 */
@Serializable
data class LibraryBackup(
    val app: String,
    val version: Int,
    val exportedAtMs: Long = 0L,
    val likedSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val history: List<Song> = emptyList(),
    /** Who the user is and how they set the app up. Optional: backups made before it existed simply don't have it. */
    val profile: ProfileBackup? = null,
)

/**
 * The personal side of a backup. Every field is optional so a partial one still restores what it
 * has; [avatarJpegBase64] is the profile picture itself (a small JPEG — the app keeps it ≤ 512 px).
 */
@Serializable
data class ProfileBackup(
    val name: String? = null,
    val accent: String? = null,
    val equalizer: String? = null,
    val loudnessBoost: Boolean? = null,
    val offlineOnWifiOnly: Boolean? = null,
    val avatarJpegBase64: String? = null,
)

/** What an import actually changed — shown to the user, and what makes "nothing new" detectable. */
data class ImportSummary(
    val likedAdded: Int,
    val playlistsAdded: Int,
    val playlistSongsAdded: Int,
    val historyAdded: Int,
) {
    val isEmpty get() = likedAdded == 0 && playlistsAdded == 0 && playlistSongsAdded == 0 && historyAdded == 0
}

/** A backup file that can't be used, with a message fit to show the user as-is. */
class BackupFormatException(message: String) : Exception(message)

// ignoreUnknownKeys: a backup written by a newer app version that added fields should still
// import everything this version understands, rather than failing outright.
private val backupJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

fun emptyBackup() = LibraryBackup(app = BACKUP_APP_ID, version = BACKUP_VERSION)

fun LibraryBackup.toJson(): String = backupJson.encodeToString(this)

fun parseLibraryBackup(text: String): LibraryBackup {
    if (text.isBlank()) throw BackupFormatException("That file is empty.")
    val backup = try {
        backupJson.decodeFromString<LibraryBackup>(text)
    } catch (e: IllegalArgumentException) { // SerializationException (bad JSON, missing fields) is a subclass
        throw BackupFormatException("That doesn't look like a Daydreamin library backup.")
    }
    if (backup.app != BACKUP_APP_ID) throw BackupFormatException("That doesn't look like a Daydreamin library backup.")
    if (backup.version > BACKUP_VERSION) {
        throw BackupFormatException("That backup was made by a newer version of Daydreamin — update the app to import it.")
    }
    return backup
}

/**
 * Folds [incoming] into [current] without ever removing or reordering anything already there —
 * an import must never be able to destroy what's on the device, and importing the same file
 * twice must change nothing the second time.
 *  - Liked songs: new ones are appended after the existing ones.
 *  - Playlists: matched by id; an unknown id is added whole, a known one just gains the songs it
 *    was missing.
 *  - History (most recent first): existing entries stay ahead of imported ones, capped at [limit].
 * Songs are identified by [Song.playId]; one with no usable id can't be de-duplicated or
 * queued later, so it's dropped rather than imported broken. A song only appears once in any list.
 */
fun mergeLibraries(current: LibraryBackup, incoming: LibraryBackup, limit: Int = HISTORY_LIMIT): Pair<LibraryBackup, ImportSummary> {
    fun List<Song>.usable() = filter { it.playId.isNotBlank() }.distinctBy { it.playId }

    val currentLiked = current.likedSongs.usable()
    val likedIds = currentLiked.map { it.playId }.toSet()
    val newLiked = incoming.likedSongs.usable().filter { it.playId !in likedIds }

    var playlistsAdded = 0
    var playlistSongsAdded = 0
    val mergedPlaylists = current.playlists.toMutableList()
    for (incomingPlaylist in incoming.playlists) {
        val incomingSongs = incomingPlaylist.songs.usable()
        val at = mergedPlaylists.indexOfFirst { it.id == incomingPlaylist.id }
        if (at == -1) {
            mergedPlaylists.add(incomingPlaylist.copy(songs = incomingSongs))
            playlistsAdded++
        } else {
            val existing = mergedPlaylists[at]
            val have = existing.songs.map { it.playId }.toSet()
            val missing = incomingSongs.filter { it.playId !in have }
            if (missing.isNotEmpty()) {
                mergedPlaylists[at] = existing.copy(songs = existing.songs + missing)
                playlistSongsAdded += missing.size
            }
        }
    }

    val currentHistory = current.history.usable()
    val historyIds = currentHistory.map { it.playId }.toSet()
    val newHistory = incoming.history.usable().filter { it.playId !in historyIds }
    val mergedHistory = (currentHistory + newHistory).take(limit)
    // Counted against what survives the cap, not what was offered — otherwise a full history
    // would report imports that were immediately truncated away.
    val historyAdded = mergedHistory.size - currentHistory.take(limit).size

    val merged = current.copy(
        // The profile is applied separately (only onto a device that hasn't been set up yet) —
        // merging never changes who you are on a device you're already using.
        likedSongs = currentLiked + newLiked,
        playlists = mergedPlaylists,
        history = mergedHistory,
    )
    return merged to ImportSummary(newLiked.size, playlistsAdded, playlistSongsAdded, historyAdded)
}
