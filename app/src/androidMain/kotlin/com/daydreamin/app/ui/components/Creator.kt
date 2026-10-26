package com.daydreamin.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Who made Daydreamin, and where to find him — shown in Settings → About and the side panel. */
object Creator {
    const val NAME = "Ankit"
    const val INSTAGRAM_HANDLE = "ankitchaurasiya.o_o"
    private const val INSTAGRAM_URL = "https://www.instagram.com/$INSTAGRAM_HANDLE/"

    /** Opens the profile — in the Instagram app when it's installed (it claims these links), otherwise the browser. */
    fun openInstagram(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(INSTAGRAM_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toaster.show("No app found to open Instagram")
        }
    }
}
