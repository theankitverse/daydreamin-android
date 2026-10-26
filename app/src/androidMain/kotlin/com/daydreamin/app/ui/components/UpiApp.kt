package com.daydreamin.app.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Copies the UPI ID and opens the person's UPI app (GPay, PhonePe, Paytm, …) on its home screen,
 * where they pay it with "Pay UPI ID" — any amount.
 *
 * Not a upi://pay link: GPay refuses those for a personal UPI ID ("you have exceeded the bank
 * limit", even for ₹1, or a ₹2,000 cap), and only a business UPI ID gets past that. An ID
 * pasted into the app by hand has no such limit.
 */
fun openUpiApp(context: Context, upiId: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", upiId))
    Toaster.show("UPI ID copied. In your UPI app, tap “Pay UPI ID” and paste it", durationMs = 8_000)

    // The installed UPI apps (the manifest's <queries> lets us see them), opened normally.
    val pm = context.packageManager
    val apps = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay")), 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .mapNotNull { pm.getLaunchIntentForPackage(it) }
    if (apps.isEmpty()) {
        Toaster.show("UPI ID copied: $upiId. Paste it in GPay, PhonePe or Paytm to donate", durationMs = 8_000)
        return
    }
    val open = if (apps.size == 1) apps[0] else
        Intent.createChooser(apps[0], "Donate with").putExtra(Intent.EXTRA_INITIAL_INTENTS, apps.drop(1).toTypedArray())
    try {
        context.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toaster.show("UPI ID copied: $upiId. Paste it in your UPI app to donate", durationMs = 8_000)
    }
}
