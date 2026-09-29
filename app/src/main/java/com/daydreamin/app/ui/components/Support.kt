package com.daydreamin.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Daydreamin is free, with no ads and nothing extracted from you — this is the optional, entirely
 * unrelated way to support it if you'd like to. Shown in Settings → About.
 */
object Support {
    // TODO(Ankit): set this to your real Buy Me a Coffee username before announcing it widely —
    // https://www.buymeacoffee.com/ — then this page goes live automatically, nothing else to change.
    const val BUY_ME_A_COFFEE_USERNAME = "ankitchaurasiya"
    private const val URL = "https://www.buymeacoffee.com/$BUY_ME_A_COFFEE_USERNAME"

    fun open(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toaster.show("No browser found to open that link")
        }
    }
}
