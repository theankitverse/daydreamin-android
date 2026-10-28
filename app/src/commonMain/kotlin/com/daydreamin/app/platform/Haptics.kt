package com.daydreamin.app.platform

import androidx.compose.runtime.Composable

/** The kinds of touch feedback the app gives: a light tap for buttons, firmer ones for things that land. */
enum class Haptic {
    /** A button doing its thing: play/pause, skip. */
    Tap,
    /** Something saved or done: a like, a song added. */
    Confirm,
    /** A switch turned on or off (shuffle, repeat), or a like taken back. */
    Toggle,
    /** A small step: a tab changing, a seek starting or landing. */
    Tick,
    /** A gesture crossing the point where letting go acts (pull to refresh, swipe to remove). */
    Threshold,
}

/** Gives [Haptic] feedback through the phone's vibration motor (respecting its touch-feedback setting); nothing on a computer. */
@Composable
expect fun rememberHaptics(): (Haptic) -> Unit
