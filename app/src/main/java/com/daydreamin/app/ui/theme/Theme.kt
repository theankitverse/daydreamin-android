package com.daydreamin.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Daydreamin is dark-themed by design (matches the reference screens); [accent] is the
 * one user-configurable color, picked on the Theme / Customize screen.
 */
@Composable
fun DaydreaminTheme(
    accent: Color = BrandPurple,
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = BrandPink,
        onSecondary = Color.White,
        background = BgBase,
        onBackground = TextPrimary,
        surface = Surface,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = TextSecondary,
        outline = Divider,
        error = DangerRed,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            window.statusBarColor = BgBase.toArgb()
            window.navigationBarColor = BgBase.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DaydreaminTypography,
        content = content,
    )
}
