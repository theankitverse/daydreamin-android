package com.daydreamin.app.player

import androidx.media3.common.MediaItem
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Wraps the real ExoPlayer for the [MediaSession] so that "skip to next/previous" — however it
 * arrives (a Bluetooth remote's buttons, the lock-screen/notification transport controls, a
 * wired headset, Android Auto) — always goes through [PlayerController]'s own queue logic
 * instead of ExoPlayer's raw internal timeline.
 *
 * This matters because [PlayerController] trims consumed items from the player's own playlist
 * as it advances (so it never grows unbounded — see `onGaplessAdvance`), which means ExoPlayer's
 * own `hasPreviousMediaItem()` is almost always false and its `seekToPrevious()` has no real
 * "previous song" to fall back on. [PlayerController.previous] instead tracks real playback
 * history in its own back-stack, and [PlayerController.next] knows how to take the pre-buffered
 * "gapless" path when one is ready. Routing external commands through those functions, rather
 * than letting them hit the underlying player directly, keeps every control surface consistent.
 *
 * Deliberately only overrides `seekToNext()`/`seekToPrevious()` — the "smart skip" commands the
 * system maps standard transport button presses to — and *not* their `...MediaItem()` cousins,
 * which [PlayerController] itself calls internally for the gapless-buffered transition; wrapping
 * those too would recurse back into this class through the MediaSession.
 */
class QueueAwarePlayer(player: Player, private val library: AutoLibrary) : ForwardingPlayer(player) {

    // A song picked in Android Auto arrives as a new playlist. It has no stream yet and its list is
    // the queue, so it goes through the same path as a tap in the app. The app's own songs pass by.
    private fun tookOver(items: List<MediaItem>, startIndex: Int = 0): Boolean {
        val picked = items.getOrNull(startIndex.coerceAtLeast(0))?.takeIf(library::owns) ?: return false
        library.play(picked)
        return true
    }

    // An empty player told to start ends at once, and the app takes that for the end of the song and
    // moves on. That's what a car (or a Bluetooth button after a cold start) would cause, sending
    // prepare and play before the song is in: the song is on its way, so those wait for it.
    override fun prepare() {
        if (mediaItemCount > 0) super.prepare()
    }

    override fun play() {
        if (mediaItemCount > 0) super.play() else PlayerController.playOnceRestored()
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        if (!tookOver(mediaItems)) super.setMediaItems(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        if (!tookOver(mediaItems)) super.setMediaItems(mediaItems, resetPosition)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        if (!tookOver(mediaItems, startIndex)) super.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun seekToNext() {
        PlayerController.next()
    }

    override fun seekToPrevious() {
        PlayerController.previous()
    }

    /** Keep the transport controls enabled even when the underlying player's own trimmed
     *  timeline has nothing before/after — [PlayerController] is the real source of truth for
     *  whether a previous/next song exists, not this player's own (deliberately short) window. */
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_PREVIOUS || super.isCommandAvailable(command)
}
