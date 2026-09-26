package com.daydreamin.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.daydreamin.app.ui.navigation.AppNavHost
import com.daydreamin.app.ui.theme.DaydreaminTheme
import com.daydreamin.app.ui.theme.accentByName

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The launch screen (logo on the app's background) is the only splash. It stays up for a
        // moment while Home's songs finish loading — they're requested the instant the process
        // starts (DaydreaminApp.chartPrefetch) — so Home usually opens already filled in. Capped,
        // so a slow network never holds the app back: past the cap, Home shows its loading shimmer.
        installSplashScreen().setKeepOnScreenCondition {
            val app = DaydreaminApp.instance
            !app.chartPrefetch.isCompleted && SystemClock.elapsedRealtime() - app.processStartAtMs < MAX_SPLASH_HOLD_MS
        }
        super.onCreate(savedInstanceState)
        // Transparent, light-icon system bars on every API level (Android 15+ enforces
        // edge-to-edge anyway; this makes older versions behave the same).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val accentName by DaydreaminApp.instance.prefs.accentName.collectAsState(initial = "Violet")
            DaydreaminTheme(accent = accentByName(accentName)) {
                AppNavHost()
            }
        }
    }
}

/** Longest the launch screen waits for Home's songs, measured from process start. */
private const val MAX_SPLASH_HOLD_MS = 1_500L
