package com.daydreamin.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import com.daydreamin.app.ui.screens.intro.LaunchIntro
import com.daydreamin.app.ui.navigation.AppNavHost
import com.daydreamin.app.ui.theme.DaydreaminTheme
import com.daydreamin.app.ui.theme.accentByName

class MainActivity : ComponentActivity() {

    /** The launch icon as the system drew it, and where — handed from the system launch screen to [LaunchIntro]. */
    private var introLogo by mutableStateOf<ImageBitmap?>(null)
    private var introLogoBounds by mutableStateOf<IntRect?>(null)

    /**
     * The launcher icon drawn the way the launch screen draws it: the adaptive icon (background +
     * foreground, clipped to this device's icon shape) filling the icon view. Drawn ourselves
     * rather than copied from the view, which on Android 12+ may be a surface we can't read.
     */
    private fun renderLauncherIcon(width: Int, height: Int): ImageBitmap? = runCatching {
        val drawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher) ?: return null
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(android.graphics.Canvas(bitmap))
        bitmap.asImageBitmap()
    }.getOrNull()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system launch screen can only show the still icon, so it hands over as soon as the
        // app can draw: we note exactly where it drew the icon and draw the same icon there
        // ourselves, then LaunchIntro animates the wordmark in beneath it and waits for Home's
        // songs (requested the instant the process started — DaydreaminApp.chartPrefetch).
        val splash = installSplashScreen()
        splash.setOnExitAnimationListener { provider ->
            val icon = provider.iconView
            if (icon.width > 0 && icon.height > 0) {
                val at = IntArray(2).also { icon.getLocationInWindow(it) }
                introLogoBounds = IntRect(at[0], at[1], at[0] + icon.width, at[1] + icon.height)
                introLogo = renderLauncherIcon(icon.width, icon.height)
            }
            // Let our copy of the icon draw underneath first (two frames), so there's never a frame
            // between the system screen leaving and the intro's icon appearing.
            window.decorView.postOnAnimation { window.decorView.postOnAnimation { provider.remove() } }
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

        // Only a fresh launch plays the intro — not a rotation or a return from the background.
        val coldStart = savedInstanceState == null
        setContent {
            val accentName by DaydreaminApp.instance.prefs.accentName.collectAsState(initial = "Violet")
            var showIntro by rememberSaveable { mutableStateOf(coldStart) }
            DaydreaminTheme(accent = accentByName(accentName)) {
                Box(Modifier.fillMaxSize()) {
                    // Home composes underneath from the first frame, so it's ready the moment the intro leaves.
                    AppNavHost()
                    if (showIntro) {
                        val app = DaydreaminApp.instance
                        LaunchIntro(
                            logo = introLogo,
                            logoBounds = introLogoBounds,
                            isReady = { app.chartPrefetch.isCompleted },
                            processStartAtMs = app.processStartAtMs,
                            maxWaitMs = MAX_INTRO_WAIT_MS,
                            onFinished = { showIntro = false },
                        )
                    }
                }
            }
        }
    }
}

/** Longest the intro waits for Home's songs, from process start; after this Home shows its loading shimmer. */
private const val MAX_INTRO_WAIT_MS = 2_000L
