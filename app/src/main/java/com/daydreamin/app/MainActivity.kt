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

    /** The system splash has actually left the screen — [LaunchIntro]'s clock starts from here. */
    private var splashGone by mutableStateOf(false)

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

        // Render the icon ourselves, centered at the platform's splash-icon size, before the first
        // frame — so the intro's first frame already has it even when the system never reports
        // where it drew its own. When it does (below), that exact position replaces this one while
        // the system splash is still covering the screen.
        val density = resources.displayMetrics.density
        val fallbackPx = (com.daydreamin.app.ui.screens.intro.SPLASH_ICON_DP * density).toInt()
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
            window.decorView.postOnAnimation {
                window.decorView.postOnAnimation {
                    provider.remove()
                    splashGone = true
                }
            }
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
            // Held back while the intro's letters animate — see LaunchIntro.
            var contentMounted by rememberSaveable { mutableStateOf(!coldStart) }
            // null until the store has been read — nothing is shown over Home until we know.
            val onboarded by DaydreaminApp.instance.prefs.onboarded.collectAsState(initial = null)
            val scope = rememberCoroutineScope()
            // Asked for once the user can see what it's for (after setup and the intro), not over them.
            LaunchedEffect(onboarded, showIntro) {
                if (onboarded == true && !showIntro && DaydreaminApp.instance.prefs.claimNotificationAsk()) askForNotifications()
            }
            DaydreaminTheme(accent = accentByName(accentName)) {
                Box(Modifier.fillMaxSize()) {
                    // Home composes underneath the intro once its letters have landed, so it's drawn
                    // and ready by the time the intro fades — except during first-run setup, which
                    // must own every touch (and the screen reader) until it's done.
                    if (contentMounted) {
                        if (onboarded == true) AppNavHost()
                        if (onboarded == false) {
                            OnboardingScreen(onDone = { scope.launch { DaydreaminApp.instance.prefs.setOnboarded() } })
                        }
                    }
                    if (showIntro) {
                        LaunchIntro(
                            icon = introIcon,
                            splashGone = { splashGone },
                            isReady = { DaydreaminApp.instance.homeFeedReady() },
                            onMountContent = { contentMounted = true },
                            onFinished = { showIntro = false },
                        )
                    }
                }
            }
        }
    }
}
