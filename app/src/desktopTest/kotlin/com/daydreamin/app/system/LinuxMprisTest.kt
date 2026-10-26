package com.daydreamin.app.system

import com.daydreamin.app.data.model.Song
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Linux only, on a session bus (`dbus-run-session -- ./gradlew :app:desktopTest`): another program
 * on the bus — as GNOME, KDE or playerctl would — reads what's playing and controls it.
 */
class LinuxMprisTest {
    @Test fun desktopSeesTheSongAndControlsIt() {
        if (!System.getProperty("os.name").lowercase().contains("linux") || System.getenv("DBUS_SESSION_BUS_ADDRESS") == null) return
        val got = CopyOnWriteArrayList<String>()
        MediaCommands.onCommand = { got += it }
        val mpris = LinuxMpris()
        try {
            val song = Song(id = "abc", title = "Kesariya", artist = "Arijit Singh")
            mpris.update(NowPlaying(song, playing = true, positionMs = 42_000, durationMs = 268_000, artworkUrl = null))
            DBusConnectionBuilder.forSessionBus().build().use { other ->
                val props = other.getRemoteObject("org.mpris.MediaPlayer2.daydreamin", "/org/mpris/MediaPlayer2", Properties::class.java)
                assertEquals("Playing", props.Get<String>("org.mpris.MediaPlayer2.Player", "PlaybackStatus"))
                @Suppress("UNCHECKED_CAST")
                val metadata = props.Get<Map<String, Variant<*>>>("org.mpris.MediaPlayer2.Player", "Metadata")
                assertEquals("Kesariya", metadata["xesam:title"]?.value)
                assertEquals(268_000_000L, metadata["mpris:length"]?.value)
                val player = other.getRemoteObject("org.mpris.MediaPlayer2.daydreamin", "/org/mpris/MediaPlayer2", MprisPlayer::class.java)
                player.PlayPause(); player.Next(); player.Previous()
                Thread.sleep(300)
                assertEquals(listOf("toggle", "next", "previous"), got.toList())
                println("[linux-test] MPRIS: status, metadata and commands work: $got")
            }
        } finally {
            mpris.close()
            MediaCommands.onCommand = null
        }
    }
}
