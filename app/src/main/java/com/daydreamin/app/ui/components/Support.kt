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
    private const val UPI_PAYEE_NAME = "Ankit"

    fun open(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toaster.show("No browser found to open that link")
        }
    }

    /** Opens whatever UPI app is installed (GPay, PhonePe, Paytm, …) with the payee already filled in. */
    fun openUpi(context: Context) {
        val uri = Uri.parse("upi://pay?pa=$UPI_ID&pn=$UPI_PAYEE_NAME&cu=INR")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            copyUpiId(context)
            Toaster.show("No UPI app found — ID copied instead")
        }
    }

    fun copyUpiId(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", UPI_ID))
        Toaster.show("UPI ID copied")
    }
}
