package com.daydreamin.app.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Daydreamin is free, with no ads and nothing extracted from you — this is the optional, entirely
 * unrelated way to support it if you'd like to. Shown in Settings → About.
 */
object Support {
    const val BUY_ME_A_COFFEE_USERNAME = "theankitverse"
    private const val URL = "https://www.buymeacoffee.com/$BUY_ME_A_COFFEE_USERNAME"

    const val UPI_ID = "ankitkrchaurasiya2024@okhdfcbank"

    fun open(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toaster.show("No browser found to open that link")
        }
    }

    /**
     * Copies the UPI ID and opens the person's UPI app (GPay, PhonePe, Paytm, …) on its home screen,
     * so they pay to the ID from inside the app — any amount.
     *
     * Not a `upi://pay` link: UPI apps and banks cap payments that start from a link in another app
     * and go to a personal UPI ID (around ₹2,000, and some apps refuse them outright). A normal
     * "pay to UPI ID" started inside the UPI app has no such cap.
     */
    fun openUpi(context: Context) {
        copyUpiId(context, quiet = true)
        Toaster.show("UPI ID copied. In your UPI app, tap Pay → UPI ID, paste it and send any amount", durationMs = 7_000)
        val pm = context.packageManager
        val upiApps = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay")), 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .mapNotNull { pm.getLaunchIntentForPackage(it) }
        if (upiApps.isEmpty()) return // the ID is copied; the message says what to do with it
        val intent = if (upiApps.size == 1) upiApps.first() else {
            Intent.createChooser(upiApps.first(), "Pay with").putExtra(Intent.EXTRA_INITIAL_INTENTS, upiApps.drop(1).toTypedArray())
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Already copied, with instructions showing.
        }
    }

    fun copyUpiId(context: Context, quiet: Boolean = false) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", UPI_ID))
        if (!quiet) Toaster.show("UPI ID copied")
    }
}
