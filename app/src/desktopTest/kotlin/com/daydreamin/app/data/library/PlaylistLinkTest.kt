package com.daydreamin.app.data.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaylistLinkTest {
    private val yt = PlaylistLink.YouTube("https://www.youtube.com/playlist?list=PLabc-1_2")

    @Test fun `every form of a YouTube playlist link is the same playlist`() {
        assertEquals(yt, PlaylistLink.parse("https://www.youtube.com/playlist?list=PLabc-1_2"))
        assertEquals(yt, PlaylistLink.parse("https://music.youtube.com/playlist?list=PLabc-1_2&si=xyz"))
        assertEquals(yt, PlaylistLink.parse("https://youtube.com/watch?v=abc&list=PLabc-1_2&index=3"))
        assertEquals(yt, PlaylistLink.parse("https://youtu.be/abc?list=PLabc-1_2"))
    }

    @Test fun `a single video isn't a playlist`() {
        assertNull(PlaylistLink.parse("https://www.youtube.com/watch?v=abc"))
    }

    @Test fun `Deezer links, with a language or short`() {
        assertEquals(PlaylistLink.Deezer("123"), PlaylistLink.parse("https://www.deezer.com/en/playlist/123?utm=1"))
        assertEquals(PlaylistLink.Deezer("123"), PlaylistLink.parse("https://deezer.com/playlist/123"))
        assertEquals(PlaylistLink.DeezerShort("https://link.deezer.com/s/abc"), PlaylistLink.parse("https://link.deezer.com/s/abc"))
    }

    @Test fun `Spotify is recognised, in the words apps share it with`() {
        assertEquals(PlaylistLink.Spotify, PlaylistLink.parse("Check this out: https://open.spotify.com/playlist/37i9?si=1"))
        assertEquals(PlaylistLink.Spotify, PlaylistLink.parse("https://spotify.link/abc"))
    }

    @Test fun `services that keep their doors shut are named, anything else is nothing`() {
        assertEquals(PlaylistLink.Closed("Apple Music"), PlaylistLink.parse("https://music.apple.com/in/playlist/x/pl.1"))
        assertNull(PlaylistLink.parse("https://example.com/playlist/1"))
        assertNull(PlaylistLink.parse("not a link"))
    }
}
