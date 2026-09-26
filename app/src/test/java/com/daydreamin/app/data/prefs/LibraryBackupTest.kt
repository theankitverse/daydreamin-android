package com.daydreamin.app.data.prefs

import com.daydreamin.app.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LibraryBackupTest {

    private fun song(id: String, title: String = "Song $id") = Song(id = id, title = title, artist = "Artist")
    private fun lib(
        liked: List<Song> = emptyList(),
        playlists: List<Playlist> = emptyList(),
        history: List<Song> = emptyList(),
    ) = emptyBackup().copy(likedSongs = liked, playlists = playlists, history = history)

    private fun rejects(text: String): String {
        try {
            parseLibraryBackup(text)
        } catch (e: BackupFormatException) {
            return e.message!!
        }
        fail("expected a BackupFormatException for: ${text.take(40)}")
        return ""
    }

    // --- file format ---

    @Test fun exportThenParseRoundTripsEverything() {
        val original = lib(
            liked = listOf(song("1"), Song(id = "2", title = "Tum Se Hi", artist = "Mohit", videoId = "abc", cover = "http://c")),
            playlists = listOf(Playlist(id = "p1", name = "Gym", songs = listOf(song("3")))),
            history = listOf(song("4")),
        ).copy(exportedAtMs = 1234L)
        assertEquals(original, parseLibraryBackup(original.toJson()))
    }

    @Test fun rejectsAnythingThatIsNotOurFile() {
        rejects("")
        rejects("   \n")
        rejects("not json at all")
        rejects("[1,2,3]")
        rejects("{}")                                             // valid JSON, but no app/version
        rejects("""{"app":"someone-else","version":1}""")          // right shape, wrong app
        rejects("""{"app":"daydreamin"}""")                        // version missing
    }

    @Test fun rejectsBackupsFromANewerAppVersionWithAHelpfulMessage() {
        val message = rejects("""{"app":"daydreamin","version":${BACKUP_VERSION + 1}}""")
        assertTrue("should tell the user to update, was: $message", message.contains("newer version"))
    }

    @Test fun ignoresFieldsAddedByAFutureVersionInsteadOfFailing() {
        val text = """{"app":"daydreamin","version":1,"someFutureField":{"x":1},
            "likedSongs":[{"id":"7","title":"T","artist":"A","brandNewSongField":true}]}"""
        val parsed = parseLibraryBackup(text)
        assertEquals(listOf("7"), parsed.likedSongs.map { it.id })
    }

    // --- merge ---

    @Test fun mergeAppendsNewLikedSongsAfterExistingOnesWithoutReordering() {
        val (merged, summary) = mergeLibraries(lib(liked = listOf(song("a"), song("b"))), lib(liked = listOf(song("c"), song("a"), song("d"))))
        assertEquals(listOf("a", "b", "c", "d"), merged.likedSongs.map { it.id })
        assertEquals(2, summary.likedAdded)
    }

    @Test fun importingTheSameFileTwiceChangesNothingTheSecondTime() {
        val current = lib(
            liked = listOf(song("a")),
            playlists = listOf(Playlist("p1", "Gym", listOf(song("x")))),
            history = listOf(song("h1")),
        )
        val incoming = lib(
            liked = listOf(song("b")),
            playlists = listOf(Playlist("p1", "Gym", listOf(song("y"))), Playlist("p2", "Chill", listOf(song("z")))),
            history = listOf(song("h2")),
        )
        val (once, first) = mergeLibraries(current, incoming)
        val (twice, second) = mergeLibraries(once, incoming)
        assertFalse("first import should have changed something", first.isEmpty)
        assertTrue("second import must be a no-op, was $second", second.isEmpty)
        assertEquals(once, twice)
    }

    @Test fun mergeNeverRemovesAnythingAlreadyOnTheDevice() {
        val current = lib(liked = listOf(song("a"), song("b")), history = listOf(song("h1")), playlists = listOf(Playlist("p", "Mine", listOf(song("x")))))
        val (merged, _) = mergeLibraries(current, emptyBackup())
        assertEquals(current, merged)
    }

    @Test fun knownPlaylistOnlyGainsMissingSongsAndKeepsItsName() {
        val current = lib(playlists = listOf(Playlist("p1", "My Name", listOf(song("x")))))
        val incoming = lib(playlists = listOf(Playlist("p1", "Their Name", listOf(song("x"), song("y")))))
        val (merged, summary) = mergeLibraries(current, incoming)
        assertEquals("My Name", merged.playlists.single().name)
        assertEquals(listOf("x", "y"), merged.playlists.single().songs.map { it.id })
        assertEquals(0, summary.playlistsAdded)
        assertEquals(1, summary.playlistSongsAdded)
    }

    @Test fun unknownPlaylistIsAddedWholeAndDeDuplicated() {
        val incoming = lib(playlists = listOf(Playlist("new", "New", listOf(song("x"), song("x"), song("y")))))
        val (merged, summary) = mergeLibraries(emptyBackup(), incoming)
        assertEquals(listOf("x", "y"), merged.playlists.single().songs.map { it.id })
        assertEquals(1, summary.playlistsAdded)
    }

    @Test fun historyKeepsExistingEntriesFirstAndRespectsTheCap() {
        val current = lib(history = listOf(song("h1"), song("h2")))
        val incoming = lib(history = listOf(song("h3"), song("h4"), song("h1")))
        val (merged, summary) = mergeLibraries(current, incoming, limit = 3)
        assertEquals(listOf("h1", "h2", "h3"), merged.history.map { it.id })
        assertEquals(1, summary.historyAdded)   // h4 was offered but cut by the cap — must not be reported as imported
    }

    @Test fun aFullHistoryReportsNoImportsRatherThanPhantomOnes() {
        val current = lib(history = (1..3).map { song("h$it") })
        val (merged, summary) = mergeLibraries(current, lib(history = listOf(song("new"))), limit = 3)
        assertEquals(current.history, merged.history)
        assertEquals(0, summary.historyAdded)
    }

    @Test fun songsWithNoUsableIdAreDroppedInsteadOfImportedBroken() {
        val noId = Song(id = "", title = "Ghost", artist = "?", videoId = null)
        val (merged, summary) = mergeLibraries(emptyBackup(), lib(liked = listOf(noId, song("ok"))))
        assertEquals(listOf("ok"), merged.likedSongs.map { it.id })
        assertEquals(1, summary.likedAdded)
    }

    @Test fun aSongIdentifiedOnlyByVideoIdIsStillKeyedByIt() {
        val byVideo = Song(id = "", title = "T", artist = "A", videoId = "vid1")
        val (merged, summary) = mergeLibraries(lib(liked = listOf(byVideo)), lib(liked = listOf(byVideo.copy(title = "renamed"))))
        assertEquals(1, merged.likedSongs.size)
        assertEquals(0, summary.likedAdded)
    }
}
