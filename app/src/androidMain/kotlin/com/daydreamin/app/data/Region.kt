package com.daydreamin.app.data

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Where you are, for charts and YouTube results. The SIM's country first (it's where the phone
 * actually lives, even with the language set to something else), then the network's, then the
 * device locale.
 */
object Region {
    /** ISO 3166 alpha-2, upper case — "IN", "US". */
    var country: String = "US"
        private set

    fun init(context: Context) {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        country = listOf(
            runCatching { telephony?.simCountryIso }.getOrNull(),
            runCatching { telephony?.networkCountryIso }.getOrNull(),
            Locale.getDefault().country,
        ).firstOrNull { it != null && it.length == 2 }?.uppercase() ?: "US"
    }

    /** "India", "United States" — for section titles. */
    val displayName: String get() = Locale("", country).getDisplayCountry(Locale.ENGLISH).ifBlank { country }
}
