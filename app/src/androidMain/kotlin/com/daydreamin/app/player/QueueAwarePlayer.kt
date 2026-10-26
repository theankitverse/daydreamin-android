package com.daydreamin.app.player

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
class QueueAwarePlayer(player: Player) : ForwardingPlayer(player) {

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
