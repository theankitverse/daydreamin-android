package com.daydreamin.app.data

import android.content.Context
import android.telephony.TelephonyManager

/** The SIM's country (where the phone actually lives, even with the language set to something else), then the network's. */
fun detectCountry(context: Context): String? {
    val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    return listOf(
        runCatching { telephony?.simCountryIso }.getOrNull(),
        runCatching { telephony?.networkCountryIso }.getOrNull(),
    ).firstOrNull { it != null && it.length == 2 }
}
