package com.daydreamin.app.ui.components

import com.daydreamin.app.platform.donateViaUpi
import com.daydreamin.app.platform.openUrl

/**
 * Daydreamin is free, with no ads and nothing extracted from you — this is the optional, entirely
 * unrelated way to support it if you'd like to. Shown in Settings → About.
 */
object Support {
    const val BUY_ME_A_COFFEE_USERNAME = "theankitverse"
    private const val URL = "https://www.buymeacoffee.com/$BUY_ME_A_COFFEE_USERNAME"

    const val UPI_ID = "ankitkrchaurasiya2024@okicici"

    fun open() = openUrl(URL, "No browser found to open that link")

    /** Pays [UPI_ID] from the person's UPI app — see [donateViaUpi]. */
    fun openUpi() = donateViaUpi(UPI_ID)
}
