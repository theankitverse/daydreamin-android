package com.daydreamin.app.data

import java.util.Locale

/**
 * Where you are, for charts and YouTube results. Each platform works it out at startup (Android:
 * the SIM's country, then the network's, then the device locale); [init] falls back to the locale.
 */
object Region {
    /** ISO 3166 alpha-2, upper case — "IN", "US". */
    var country: String = "US"
        private set

    fun init(detected: String?) {
        country = listOf(detected, Locale.getDefault().country).firstOrNull { it != null && it.length == 2 }?.uppercase() ?: "US"
    }

    /** "India", "United States" — for section titles. */
    val displayName: String get() = Locale("", country).getDisplayCountry(Locale.ENGLISH).ifBlank { country }
}
