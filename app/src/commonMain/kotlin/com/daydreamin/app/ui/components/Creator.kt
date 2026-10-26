package com.daydreamin.app.ui.components

import com.daydreamin.app.platform.openUrl

/** Who made Daydreamin, and where to find him — shown in Settings → About and the side panel. */
object Creator {
    const val NAME = "Ankit"
    const val INSTAGRAM_HANDLE = "ankitchaurasiya.o_o"
    private const val INSTAGRAM_URL = "https://www.instagram.com/$INSTAGRAM_HANDLE/"

    /** Opens the profile — in the Instagram app when it's installed (it claims these links), otherwise the browser. */
    fun openInstagram() = openUrl(INSTAGRAM_URL, "No app found to open Instagram")
}
