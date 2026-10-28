package com.daydreamin.app.platform

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberHaptics(): (Haptic) -> Unit {
    val view = LocalView.current
    return remember(view) { { haptic -> view.performHapticFeedback(haptic.constant()) } }
}

// The newer, more specific effects where the phone has them; the closest older one otherwise.
private fun Haptic.constant(): Int = when (this) {
    Haptic.Tap -> HapticFeedbackConstants.VIRTUAL_KEY
    Haptic.Confirm -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    Haptic.Toggle -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK
    Haptic.Tick -> when {
        Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.SEGMENT_TICK
        Build.VERSION.SDK_INT >= 27 -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
        else -> HapticFeedbackConstants.CLOCK_TICK
    }
    Haptic.Threshold -> when {
        Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        Build.VERSION.SDK_INT >= 23 -> HapticFeedbackConstants.CONTEXT_CLICK
        else -> HapticFeedbackConstants.VIRTUAL_KEY
    }
}
