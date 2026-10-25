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
    const val BUY_ME_A_COFFEE_USERNAME = "theankitverse"
    private const val URL = "https://www.buymeacoffee.com/$BUY_ME_A_COFFEE_USERNAME"

    const val UPI_ID = "ankitkrchaurasiya2024@okicici"
    private const val UPI_PAYEE_NAME = "Ankit"

    fun open(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toaster.show("No browser found to open that link")
        }
    }

    /**
     * Opens the person's UPI app (GPay, PhonePe, Paytm, …) on its payment screen with the UPI ID
     * already filled in; they choose the amount.
     */
    fun openUpi(context: Context) {
        // The standard UPI link, written out plainly (an encoded "@" trips up some UPI apps).
        val uri = Uri.parse("upi://pay?pa=$UPI_ID&pn=$UPI_PAYEE_NAME&tn=Donation%20to%20Daydreamin&cu=INR")
        // Straight to Android, no "is there a UPI app?" check first: Android 11+ hides other apps from
        // such checks unless the manifest names them exactly, and a check that got that wrong is why
        // this button once did nothing. Opening the link isn't restricted that way. With several UPI
        // apps, Android asks which one to use.
        val pay = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(pay)
        } catch (e: ActivityNotFoundException) {
            Toaster.show("No UPI app found on this phone. Install GPay, PhonePe or Paytm to donate")
        }
    }
}
