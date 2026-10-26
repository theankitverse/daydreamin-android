package com.daydreamin.app.player

enum class EqPreset(val label: String) {
    OFF("Off"),
    BASS_BOOST("Bass Boost"),
    VOCAL_BOOST("Vocal Boost"),
    TREBLE_BOOST("Treble Boost");

    companion object {
        fun fromLabel(label: String): EqPreset = entries.firstOrNull { it.label == label } ?: OFF
    }
}
