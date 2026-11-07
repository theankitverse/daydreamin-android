package com.daydreamin.app.data.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaylistFileTest {
    @Test fun `reads an Exportify CSV`() {
        val csv = """
            Track URI,Track Name,Artist Name(s),Album Name,Album Image URL,Track Duration (ms)
            spotify:track:1,"Tum Hi Ho","Mithoon;Arijit Singh","Aashiqui 2",https://i.scdn.co/a.jpg,262000
            spotify:track:2,"Say ""Hello"", Again",Adele,25,,295000
        """.trimIndent()
        val p = PlaylistFile.parse("road_trip.csv", csv).single()
        assertEquals("road trip", p.name)
        assertEquals(2, p.songs.size)
        assertEquals("Mithoon, Arijit Singh", p.songs[0].artist)
        assertEquals("https://i.scdn.co/a.jpg", p.songs[0].cover)
        assertEquals(262, p.songs[0].duration)
        assertEquals("Say \"Hello\", Again", p.songs[1].title)
    }

    @Test fun `splits a TuneMyMusic file into its playlists`() {
        val csv = "Track name,Artist name,Album,Playlist name,Type,ISRC\nA,X,Al,Gym,Playlist,1\nB,Y,Al,Chill,Playlist,2\nC,Z,Al,Gym,Playlist,3\n"
        val lists = PlaylistFile.parse("export.csv", csv)
        assertEquals(listOf("Gym", "Chill"), lists.map { it.name })
        assertEquals(2, lists[0].songs.size)
    }

    @Test fun `reads M3U and counts what it can't`() {
        val m3u = "#EXTM3U\n#EXTINF:215,Coldplay - Yellow\n/music/yellow.mp3\n#EXTINF:100,no separator here\nfile.mp3\n"
        val p = PlaylistFile.parse("mix.m3u8", m3u).single()
        assertEquals("Yellow", p.songs.single().title)
        assertEquals("Coldplay", p.songs.single().artist)
        assertEquals(215, p.songs.single().duration)
        assertEquals(1, p.unreadable)
    }

    @Test fun `reads a plain list, and a BOM doesn't get in the way`() {
        val p = PlaylistFile.parse("list.txt", "﻿Coldplay - Yellow\nAdele - Hello\n").single()
        assertEquals(2, p.songs.size)
    }

    @Test fun `the same song in a file is kept once, with the same id every time`() {
        val a = PlaylistFile.parse("a.txt", "Adele - Hello\nadele - hello\n").single()
        assertEquals(1, a.songs.size)
        assertEquals(a.songs[0].id, PlaylistFile.parse("b.txt", "ADELE - HELLO\n").single().songs[0].id)
    }

    @Test fun `the wrong file is refused, not read as songs`() {
        // What the start of an app looks like as text, with lines that happen to hold " - ".
        val binary = "PK\u0003\u0004\u0000\u0000 - x\nabc - def\n" + "�".repeat(500)
        assertFailsWith<PlaylistFileException> { PlaylistFile.parse("daydreamin-v1.3.0.apk", binary) }
    }

    @Test fun `a file far too big is refused without reading it all`() {
        val huge = object : java.io.InputStream() { override fun read() = 'a'.code }
        assertFailsWith<PlaylistFileException> { PlaylistFile.read(huge) }
    }

    @Test fun `a file with no songs says so`() {
        assertFailsWith<PlaylistFileException> { PlaylistFile.parse("x.txt", "just some notes\nnothing here\n") }
        assertFailsWith<PlaylistFileException> { PlaylistFile.parse("x.csv", "a,b,c\n1,2,3\n") }
    }
}
