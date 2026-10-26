package com.daydreamin.app

/** This build's version and settings, filled in at startup by each platform (Android: from BuildConfig). */
object AppInfo {
    var versionName: String = "dev"
    var versionCode: Int = 0
    /** Where installed apps look for new releases. */
    var updateUrl: String = ""
    /** Test builds send usage pings to TelemetryDeck's test view, not the real counts. */
    var usageTestMode: Boolean = false
    var debug: Boolean = false
    /** "Android", "macOS", "Windows", "Linux". */
    var platformName: String = "Android"
}
