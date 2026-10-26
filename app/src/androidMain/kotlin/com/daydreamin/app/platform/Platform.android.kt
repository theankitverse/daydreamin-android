package com.daydreamin.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Composable
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.ui.components.Toaster

actual object Log {
    actual fun d(tag: String, msg: String) { android.util.Log.d(tag, msg) }
    actual fun i(tag: String, msg: String) { android.util.Log.i(tag, msg) }
    actual fun w(tag: String, msg: String, tr: Throwable?) { android.util.Log.w(tag, msg, tr) }
    actual fun e(tag: String, msg: String, tr: Throwable?) { android.util.Log.e(tag, msg, tr) }
}

actual fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

actual fun isOnline(): Boolean {
    val cm = DaydreaminApp.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    @Suppress("DEPRECATION")
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return cm.activeNetworkInfo?.isConnected == true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

actual fun openUrl(url: String, failMessage: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        DaydreaminApp.instance.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toaster.show(failMessage)
    }
}

actual fun donateViaUpi(upiId: String) = com.daydreamin.app.ui.components.openUpiApp(DaydreaminApp.instance, upiId)

actual val supportsRenderEffectBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

actual val isDesktop: Boolean = false

actual fun androidx.compose.ui.Modifier.onSecondaryClick(onClick: () -> Unit): androidx.compose.ui.Modifier = this
