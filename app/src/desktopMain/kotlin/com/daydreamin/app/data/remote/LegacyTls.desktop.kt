package com.daydreamin.app.data.remote

import okhttp3.OkHttpClient

// The Java runtime bundled with the desktop app has current root certificates.
actual object LegacyTls {
    actual val isNeeded: Boolean = false
    actual fun OkHttpClient.Builder.withLegacyRoots(): OkHttpClient.Builder = this
}
