package com.daydreamin.app.system

import com.daydreamin.app.data.model.Song

/**
 * Starts the MPRIS service with a song showing, for checking it on Linux with playerctl:
 * `dbus-run-session -- sh -c 'java -cp … com.daydreamin.app.system.LinuxMprisCheckKt & sleep 5; playerctl metadata'`.
 */
fun main() {
    val bridge = LinuxMpris()
    val song = Song(id = "abc", title = "Kesariya", artist = "Arijit Singh", album = "Brahmastra", videoId = "BddP6PYo2gs")
    bridge.update(NowPlaying(song, playing = true, positionMs = 42_000, durationMs = 268_000, artworkUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"))
    println("MPRIS READY")
    Thread.sleep(8_000)
    bridge.update(NowPlaying(song, playing = false, positionMs = 50_000, durationMs = 268_000, artworkUrl = null))
    println("MPRIS PAUSED")
    Thread.sleep(30_000)
}
