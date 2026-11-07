package com.daydreamin.app.data.share

import com.daydreamin.app.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareLinksTest {
    @Test fun `a song's link is ours, with no YouTube address in it`() {
        val link = ShareLinks.forSong(Song(id = "1", title = "T", artist = "A", videoId = "dQw4w9WgXcQ"))
        assertEquals("https://theankitverse.github.io/s/?v=dQw4w9WgXcQ&t=T&a=A", link)
        assertFalse("youtube" in link)
    }

    @Test fun `the sender's title and artist survive the trip`() {
        val link = ShareLinks.forSong(Song(id = "1", title = "Tum Hi Ho & More", artist = "Arijit Singh", videoId = "dQw4w9WgXcQ"))
        assertEquals(SharedLink.Video("dQw4w9WgXcQ", "Tum Hi Ho & More", "Arijit Singh"), ShareLinks.parse("Listen: $link"))
    }

    @Test fun `the shared message has our link and no YouTube one`() {
        val song = Song(id = "1", title = "T", artist = "A", videoId = "dQw4w9WgXcQ")
        val message = com.daydreamin.app.ui.components.shareText(com.daydreamin.app.ui.components.ShareItem.OfSong(song), ShareLinks.forSong(song))
        assertTrue("theankitverse.github.io/s/" in message)
        assertFalse("youtu" in message)
        assertFalse("github.com" in message)
    }

    @Test fun `a song with no video travels as a search`() {
        val link = ShareLinks.forSong(Song(id = "123", title = "Tum Hi Ho", artist = "Arijit Singh"))
        assertEquals(SharedLink.Search("Arijit Singh Tum Hi Ho"), ShareLinks.parse(link))
    }

    @Test fun `links read back from a message, from either address`() {
        assertEquals(SharedLink.Video("dQw4w9WgXcQ"), ShareLinks.parse("Listen: https://theankitverse.github.io/s/?v=dQw4w9WgXcQ nice"))
        assertEquals(SharedLink.Video("dQw4w9WgXcQ"), ShareLinks.parse("https://theankitverse.github.io/s?v=dQw4w9WgXcQ"))
        assertEquals(SharedLink.Video("dQw4w9WgXcQ"), ShareLinks.parse("daydreamin://song?v=dQw4w9WgXcQ"))
        assertEquals(SharedLink.App, ShareLinks.parse("https://theankitverse.github.io/s/"))
    }

    @Test fun `anything else is left alone`() {
        assertNull(ShareLinks.parse("https://music.youtube.com/playlist?list=PL123"))
        assertNull(ShareLinks.parse("https://evil.example/s/?v=dQw4w9WgXcQ"))
        assertEquals(SharedLink.App, ShareLinks.parse("https://theankitverse.github.io/s/?v=not-valid"))
    }

    @Test fun `a malformed link doesn't crash`() {
        assertTrue(ShareLinks.parse("https://theankitverse.github.io/s/?q=%zz") == SharedLink.App)
    }
}
