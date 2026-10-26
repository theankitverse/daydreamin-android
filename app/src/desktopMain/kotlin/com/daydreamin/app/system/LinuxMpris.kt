package com.daydreamin.app.system

import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.player.RepeatMode
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

private const val PATH = "/org/mpris/MediaPlayer2"
private const val ROOT = "org.mpris.MediaPlayer2"
private const val PLAYER = "org.mpris.MediaPlayer2.Player"

@DBusInterfaceName(ROOT)
interface MprisRoot : DBusInterface {
    fun Raise()
    fun Quit()
}

@DBusInterfaceName(PLAYER)
interface MprisPlayer : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    /** Relative, in microseconds. */
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)
}

/**
 * MPRIS — how every Linux desktop talks to media players: GNOME's and KDE's media controls, the
 * media keys, `playerctl`. Daydreamin appears on the session bus as org.mpris.MediaPlayer2.daydreamin.
 */
class LinuxMpris : MediaBridge, MprisRoot, MprisPlayer, Properties {
    // The Unix-socket transport is found through a ServiceLoader; point it at this app's own classes,
    // not whichever class loader the starting thread happens to have (it found nothing in some setups).
    private val connection: DBusConnection = DBusConnectionBuilder.forSessionBus()
        .transportConfig().withServiceLoaderClassLoader(LinuxMpris::class.java.classLoader).back()
        .build()
    @Volatile private var state: NowPlaying? = null

    init {
        connection.requestBusName("org.mpris.MediaPlayer2.daydreamin")
        connection.exportObject(PATH, this)
    }

    override fun getObjectPath() = PATH

    // ---- commands

    override fun Raise() {}
    override fun Quit() {}
    override fun Next() = MediaCommands.next()
    override fun Previous() = MediaCommands.previous()
    override fun Pause() = MediaCommands.pause()
    override fun PlayPause() = MediaCommands.toggle()
    override fun Stop() = MediaCommands.pause()
    override fun Play() = MediaCommands.play()
    override fun Seek(offset: Long) = MediaCommands.seekTo(PlayerController.progress.value.positionMs + offset / 1000)
    override fun SetPosition(trackId: DBusPath, position: Long) {
        if (trackId.path == trackPath(state)?.path) MediaCommands.seekTo(position / 1000)
    }
    override fun OpenUri(uri: String) {}

    // ---- properties

    private fun trackPath(s: NowPlaying?): DBusPath? =
        s?.let { DBusPath("/com/daydreamin/track/" + it.song.playId.replace(Regex("[^A-Za-z0-9_]"), "_").ifBlank { "none" }) }

    private fun metadata(s: NowPlaying?): Variant<*> {
        val map = LinkedHashMap<String, Variant<*>>()
        if (s != null) {
            map["mpris:trackid"] = Variant(trackPath(s)!!)
            if (s.durationMs > 0) map["mpris:length"] = Variant(s.durationMs * 1000)
            map["xesam:title"] = Variant(s.song.title)
            map["xesam:artist"] = Variant(listOf(s.song.artist), "as")
            if (s.song.album.isNotBlank() && s.song.album != "Single") map["xesam:album"] = Variant(s.song.album)
            s.artworkUrl?.let { map["mpris:artUrl"] = Variant(com.daydreamin.app.ui.theme.artworkModel(it)) }
        } else {
            map["mpris:trackid"] = Variant(DBusPath("/org/mpris/MediaPlayer2/TrackList/NoTrack"))
        }
        return Variant(map, "a{sv}")
    }

    private fun playerProperties(): Map<String, Variant<*>> {
        val s = state
        val meta = PlayerController.meta.value
        return linkedMapOf(
            "PlaybackStatus" to Variant(when { s == null -> "Stopped"; s.playing -> "Playing"; else -> "Paused" }),
            "LoopStatus" to Variant(when (meta.repeatMode) { RepeatMode.OFF -> "None"; RepeatMode.ALL -> "Playlist"; RepeatMode.ONE -> "Track" }),
            "Rate" to Variant(1.0),
            "Shuffle" to Variant(meta.shuffle),
            "Metadata" to metadata(s),
            "Volume" to Variant(1.0),
            "Position" to Variant(PlayerController.progress.value.positionMs * 1000),
            "MinimumRate" to Variant(1.0),
            "MaximumRate" to Variant(1.0),
            "CanGoNext" to Variant(true),
            "CanGoPrevious" to Variant(true),
            "CanPlay" to Variant(s != null),
            "CanPause" to Variant(s != null),
            "CanSeek" to Variant(s != null && s.durationMs > 0),
            "CanControl" to Variant(true),
        )
    }

    private fun rootProperties(): Map<String, Variant<*>> = linkedMapOf(
        "CanQuit" to Variant(false),
        "CanRaise" to Variant(false),
        "HasTrackList" to Variant(false),
        "Identity" to Variant("Daydreamin"),
        "DesktopEntry" to Variant("daydreamin"),
        "SupportedUriSchemes" to Variant(emptyList<String>(), "as"),
        "SupportedMimeTypes" to Variant(emptyList<String>(), "as"),
    )

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A {
        val all = if (interfaceName == ROOT) rootProperties() else playerProperties()
        return all[propertyName] as A
    }

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {
        // Volume, rate, shuffle and loop are changed in the app itself.
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> =
        if (interfaceName == ROOT) rootProperties() else playerProperties()

    // ---- telling the desktop

    override fun update(state: NowPlaying?) {
        this.state = state
        val changed = playerProperties().filterKeys { it != "Position" } // Position is read when asked, not announced
        connection.sendMessage(Properties.PropertiesChanged(PATH, PLAYER, changed, emptyList()))
    }

    override fun close() {
        runCatching { connection.releaseBusName("org.mpris.MediaPlayer2.daydreamin") }
        connection.close()
    }
}
