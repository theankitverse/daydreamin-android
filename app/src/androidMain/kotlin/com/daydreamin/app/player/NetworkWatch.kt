package com.daydreamin.app.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.delay

/**
 * Whether there's a connection, waiting for one, and noticing when the phone moves to a different
 * one (mobile data ↔ Wi-Fi). Stream URLs are signed for the address that fetched them, so a move
 * makes every one of them stale at once.
 */
internal object NetworkWatch {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            return cm.activeNetworkInfo?.isConnected == true
        }
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Returns as soon as there's a connection, or false once [timeoutMs] passes without one. */
    suspend fun awaitOnline(context: Context, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (isOnline(context)) return true
            if (SystemClock.elapsedRealtime() >= deadline) return false
            delay(500)
        }
    }

    private var watching = false
    private var current: Network? = null

    /** Calls [onChanged] on the main thread whenever a *different* network becomes the default. Registers once. */
    fun watchForChanges(context: Context, onChanged: () -> Unit) {
        if (watching || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val main = Handler(Looper.getMainLooper())
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val previous = current
                    current = network
                    if (previous != null && previous != network) main.post(onChanged)
                }
            })
            watching = true
        }
    }
}
