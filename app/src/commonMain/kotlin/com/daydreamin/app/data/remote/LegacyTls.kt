package com.daydreamin.app.data.remote

import okhttp3.OkHttpClient

/**
 * Android 7.0 and older lack the root certificates many sites now use (Let's Encrypt's ISRG
 * Root X1 among them), so on those versions every HTTPS client also trusts a few bundled roots.
 * Everywhere else this changes nothing.
 */
expect object LegacyTls {
    val isNeeded: Boolean

    /** Trusts the bundled roots too, on the platform versions that lack them. */
    fun OkHttpClient.Builder.withLegacyRoots(): OkHttpClient.Builder
}
