package com.daydreamin.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * UI-only choreography state shared by the mini player and Now Playing, so opening the player
 * reads as the mini player's artwork lifting off and expanding into the full screen (and closing
 * as it flying back) rather than one screen replacing another. Nothing here touches playback.
 */
object PlayerSheet {
    /** Where the mini player's artwork currently sits, in window coordinates. */
    var miniArtworkBounds by mutableStateOf<Rect?>(null)

    /** 0 = collapsed into the mini player, 1 = Now Playing fully open. Drives the bottom chrome's fade. */
    var expansion by mutableFloatStateOf(0f)

    /** True from the mini player tap until Now Playing has fully opened — the bottom chrome stays
     *  composed (fading out) during that flight even though the route has already changed. */
    var morphInProgress by mutableStateOf(false)
        private set

    /** Set when the app was opened from the player notification (or the Dynamic Island); Home opens Now Playing. */
    var openRequested by mutableStateOf(false)

    /** Called by the mini player right before it opens Now Playing. */
    fun requestMorphOpen() {
        morphInProgress = miniArtworkBounds != null
    }

    /** Whether the Now Playing that's about to enter should grow out of the mini player. */
    val isMorphPending: Boolean get() = morphInProgress

    fun finishMorphOpen() {
        morphInProgress = false
    }

    /**
     * The page Now Playing opened over, as it last looked: drawn behind the player as a card that
     * sinks back while the player is open and comes forward as it's pulled down (the page itself
     * leaves composition once the player has opened). Null when there was nothing to keep.
     */
    var backdrop by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)

    /** True while the page under Now Playing has been kept as [backdrop] (so it isn't re-recorded with the player on top). */
    var backdropHeld by mutableStateOf(false)

    fun releaseBackdrop() {
        backdrop = null
        backdropHeld = false
    }
}

/**
 * "Open Now Playing" for any screen, provided once by the navigation host — so tapping the song
 * that's already playing opens the player everywhere instead of restarting it.
 */
val LocalOpenPlayer = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }
