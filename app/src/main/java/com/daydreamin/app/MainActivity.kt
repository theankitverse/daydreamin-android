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
import com.daydreamin.app.ui.screens.intro.LaunchIcon
import com.daydreamin.app.ui.screens.intro.LaunchIntro
import com.daydreamin.app.ui.screens.onboarding.OnboardingScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.daydreamin.app.ui.navigation.AppNavHost
import com.daydreamin.app.ui.theme.DaydreaminTheme
import com.daydreamin.app.ui.theme.accentByName

class MainActivity : ComponentActivity() {

    /**
     * The launch icon as the system drew it, and where — handed from the system launch screen to
     * [LaunchIntro]. Set in one write (never the bitmap and the bounds separately): [LaunchIntro]
     * waits on this single value, so the logo and its wordmark animation always start together.
     */
    private var introIcon by mutableStateOf<LaunchIcon?>(null)

    /**
     * The launcher icon drawn the way the launch screen draws it: the adaptive icon (background +
     * foreground, clipped to this device's icon shape) filling the icon view. Drawn ourselves
     * rather than copied from the view, which on Android 12+ may be a surface we can't read.
     */
    private var askedForNotifications = false
    private fun askForNotifications() {
        if (askedForNotifications) return
        askedForNotifications = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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

        // Render the icon ourselves immediately, centered, rather than only from the system's exit
        // callback: that callback fires whenever the OS gets around to it — a cold start under load
        // can leave it visibly late — and until it fires, [introIcon] was null, so the wordmark
        // (gated on it) sat there un-animated instead of arriving with the logo. This version is
        // ready before the very first frame, so the wordmark always starts immediately. If the
        // callback below does fire promptly, it replaces this with the system's exact icon and
        // position; Android centers the splash icon by spec, so the swap is never visible.
        val density = resources.displayMetrics.density
        val fallbackPx = (240 * density).toInt()
        val fallbackLeft = (resources.displayMetrics.widthPixels - fallbackPx) / 2
        val fallbackTop = (resources.displayMetrics.heightPixels - fallbackPx) / 2
        renderLauncherIcon(fallbackPx, fallbackPx)?.let { bmp ->
            introIcon = LaunchIcon(bmp, IntRect(fallbackLeft, fallbackTop, fallbackLeft + fallbackPx, fallbackTop + fallbackPx))
        }

        splash.setOnExitAnimationListener { provider ->
            // Launches not started from the home screen (a notification, a link, `am start`) can get a
            // plain launch screen with no icon at all — and then iconView throws. The eager render
            // above already covers that case.
            val iconView = runCatching { provider.iconView }.getOrNull()
            if (iconView != null && iconView.width > 0 && iconView.height > 0) {
                val at = IntArray(2).also { iconView.getLocationInWindow(it) }
                val bounds = IntRect(at[0], at[1], at[0] + iconView.width, at[1] + iconView.height)
                renderLauncherIcon(iconView.width, iconView.height)?.let { bmp -> introIcon = LaunchIcon(bmp, bounds) }
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


        // Only a fresh launch plays the intro — not a rotation or a return from the background.
        val coldStart = savedInstanceState == null
        setContent {
            val accentName by DaydreaminApp.instance.prefs.accentName.collectAsState(initial = "Violet")
            var showIntro by rememberSaveable { mutableStateOf(coldStart) }
            // null until the store has been read — nothing is shown over Home until we know.
            val onboarded by DaydreaminApp.instance.prefs.onboarded.collectAsState(initial = null)
            val scope = rememberCoroutineScope()
            // Asked for once the user can see what it's for (after setup and the intro), not over them.
            LaunchedEffect(onboarded, showIntro) {
                if (onboarded == true && !showIntro && DaydreaminApp.instance.prefs.claimNotificationAsk()) askForNotifications()
            }
            DaydreaminTheme(accent = accentByName(accentName)) {
                Box(Modifier.fillMaxSize()) {
                    // Home composes underneath from the first frame, so it's ready the moment the intro
                    // leaves — except during first-run setup, which must own every touch (and the
                    // screen reader) until it's done.
                    if (onboarded == true) AppNavHost()
                    if (onboarded == false) {
                        OnboardingScreen(onDone = { scope.launch { DaydreaminApp.instance.prefs.setOnboarded() } })
                    }
                    if (showIntro) {
                        val app = DaydreaminApp.instance
                        LaunchIntro(
                            icon = introIcon,
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

/**
 * Longest the intro waits for Home's songs, from process start; after this Home shows its loading
 * shimmer instead. Measured cold start: the chart fetch itself takes well under a second on a
 * normal connection, so this is only ever spent on a slow one — capped well short of 2s (the old
 * value) so a bad connection shows Home's own shimmer sooner rather than leaving the splash on
 * screen; that reads as "the app is responding" instead of "the app is stuck loading."
 */
private const val MAX_INTRO_WAIT_MS = 1_200L
