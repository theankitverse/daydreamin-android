package com.daydreamin.app.data.usage

import android.content.Context
import android.os.Build
import android.util.Log
import com.daydreamin.app.BuildConfig
import com.daydreamin.app.data.Region
import com.daydreamin.app.data.prefs.AppPreferences
import com.daydreamin.app.data.remote.LegacyTls.withLegacyRoots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Anonymous usage counts, so the person making Daydreamin can see roughly how many people use it,
 * where they are, and how they got it — nothing else.
 *
 * Once a day, at most, the app tells TelemetryDeck (a privacy-focused analytics service):
 *  - an ID made up on this phone (random, hashed before it leaves, gone if the app is uninstalled),
 *  - the app version and the Android version,
 *  - the country (the same one Home uses for charts),
 *  - which app installed Daydreamin (Chrome, WhatsApp, Telegram, a file manager…).
 * Never songs, searches, your name or anything else about you. Off with one switch in Settings.
 *
 * Sent directly (no SDK): the SDK needs Android 6+ and adds device details like the phone model;
 * this sends only the fields above, in the format TelemetryDeck's own SDK uses.
 */
object UsageStats {
    private const val TAG = "UsageStats"
    private const val APP_ID = "67958671-0747-4738-87C8-4AC01E5D7F20"
    private const val ENDPOINT = "https://nom.telemetrydeck.com/v2/"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .withLegacyRoots()
            .build()
    }
    private val session = UUID.randomUUID().toString()

    /** Called once from DaydreaminApp.onCreate. Sends today's count if it hasn't gone yet. */
    fun start(context: Context, prefs: AppPreferences, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (!prefs.usageStatsEnabled.first()) return@launch
                val today = day()
                if (prefs.usageLastSentDay.first() == today) return@launch
                val user = prefs.usageId()
                val payload = payload(app)
                val firstTime = prefs.usageLastSentDay.first() == null
                val types = if (firstTime) listOf("TelemetryDeck.Acquisition.newInstallDetected", "Daydreamin.dailyOpen") else listOf("Daydreamin.dailyOpen")
                if (send(types.map { signal(it, user, payload) })) prefs.setUsageLastSentDay(today)
            }.onFailure { Log.w(TAG, "couldn't send today's count: ${it.message}") }
        }
    }

    private fun send(signals: List<JsonObject>): Boolean {
        val body = JsonArray(signals).toString().toRequestBody("application/json".toMediaType())
        client.newCall(Request.Builder().url(ENDPOINT).post(body).header("Accept", "application/json").build()).execute().use { response ->
            Log.d(TAG, "sent ${signals.size} signal(s): HTTP ${response.code}")
            return response.isSuccessful
        }
    }

    private fun signal(type: String, user: String, payload: Map<String, String>) = JsonObject(
        mapOf(
            "receivedAt" to JsonPrimitive(timestamp()),
            "appID" to JsonPrimitive(APP_ID),
            "clientUser" to JsonPrimitive(sha256(user)),
            "sessionID" to JsonPrimitive(session),
            "type" to JsonPrimitive(type),
            "payload" to JsonObject(payload.mapValues { JsonPrimitive(it.value) }),
            // Test builds (mine, while testing) land in TelemetryDeck's test view, never in the real numbers.
            "isTestMode" to JsonPrimitive(BuildConfig.USAGE_TEST_MODE.toString()),
        ),
    )

    /** Exactly what's sent — see the class description. */
    internal fun payload(context: Context): Map<String, String> {
        val installedFrom = installedFrom(context)
        return mapOf(
            "TelemetryDeck.AppInfo.version" to BuildConfig.VERSION_NAME,
            "TelemetryDeck.AppInfo.buildNumber" to BuildConfig.VERSION_CODE.toString(),
            "TelemetryDeck.AppInfo.versionAndBuildNumber" to "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            "TelemetryDeck.Device.platform" to "Android",
            "TelemetryDeck.Device.operatingSystem" to "Android",
            "TelemetryDeck.Device.systemVersion" to "Android ${Build.VERSION.RELEASE}",
            "TelemetryDeck.Device.systemMajorVersion" to "Android ${Build.VERSION.RELEASE.substringBefore('.')}",
            "TelemetryDeck.UserPreference.region" to Region.country,
            "TelemetryDeck.RunContext.isSideLoaded" to "true",
            "TelemetryDeck.Acquisition.channel" to installedFrom,
            "Daydreamin.installedFrom" to installedFrom,
        )
    }

    /** Which app installed Daydreamin, as a plain name. */
    internal fun installedFrom(context: Context): String {
        val pm = context.packageManager
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(context.packageName)
                // The app that started the install (Chrome, WhatsApp…) says more than the system installer that finished it.
                info.initiatingPackageName ?: info.installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()
        return installerName(installer)
    }

    internal fun installerName(pkg: String?): String = when {
        pkg.isNullOrBlank() -> "Unknown"
        pkg == "com.daydreamin.app" -> "In-app update"
        pkg == "com.android.vending" -> "Play Store"
        pkg == "com.android.shell" -> "USB (adb)"
        pkg.startsWith("com.android.chrome") || pkg.startsWith("com.chrome") -> "Chrome"
        pkg.startsWith("com.whatsapp") -> "WhatsApp"
        pkg.startsWith("org.telegram") || pkg.startsWith("org.thunderdog.challegram") -> "Telegram"
        pkg.startsWith("com.instagram") -> "Instagram"
        pkg.startsWith("com.reddit") -> "Reddit"
        pkg.startsWith("com.mi.android.globalFileexplorer") || pkg.startsWith("com.android.fileexplorer") ||
            pkg.startsWith("com.google.android.apps.nbu.files") || pkg.startsWith("com.sec.android.app.myfiles") ||
            pkg.contains("filemanager", ignoreCase = true) || pkg.contains("documentsui", ignoreCase = true) -> "File manager"
        pkg.contains("packageinstaller", ignoreCase = true) -> "Android installer"
        pkg.startsWith("com.android.browser") || pkg.startsWith("com.mi.globalbrowser") || pkg.startsWith("com.sec.android.app.sbrowser") ||
            pkg.startsWith("org.mozilla") || pkg.startsWith("com.opera") || pkg.startsWith("com.brave") || pkg.startsWith("com.microsoft.emmx") -> "Browser"
        pkg.startsWith("com.google.android.gm") -> "Gmail"
        pkg.startsWith("com.google.android.apps.messaging") -> "Messages"
        pkg.contains("bluetooth", ignoreCase = true) || pkg.startsWith("com.lenovo.anyshare") || pkg.contains("nearby", ignoreCase = true) -> "Shared from another phone"
        else -> pkg
    }

    private fun day(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date())

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

}
