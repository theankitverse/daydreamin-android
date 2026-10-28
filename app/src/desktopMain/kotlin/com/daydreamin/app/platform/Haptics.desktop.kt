package com.daydreamin.app.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberHaptics(): (Haptic) -> Unit = NoHaptics

private val NoHaptics: (Haptic) -> Unit = {}
