package com.daydreamin.app.data.update

import com.daydreamin.app.BuildConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VersionJsonTest {

    /**
     * Installed apps learn about a release only from version.json. If a release bumps the app's
     * version but not this file, every phone keeps reporting "up to date" (1.4.0 shipped that way).
     */
    @Test fun `version json announces this build`() {
        val remote = Json { ignoreUnknownKeys = true }.decodeFromString<RemoteVersion>(File("../version.json").readText())
        assertEquals(BuildConfig.VERSION_CODE, remote.versionCode)
        assertEquals(BuildConfig.VERSION_NAME, remote.versionName)
        // The in-app updater downloads this exact file and checks it against the size and fingerprint.
        assertEquals(
            "https://github.com/theankitverse/daydreamin-android/releases/download/v${BuildConfig.VERSION_NAME}/daydreamin-v${BuildConfig.VERSION_NAME}.apk",
            remote.apkUrl,
        )
        assertTrue((remote.apkSize ?: 0) > 1_000_000)
        assertEquals(64, remote.sha256?.length)
    }

    @Test fun `install failures keep Android's own reason`() {
        val msg = installFailure(android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID, "INSTALL_PARSE_FAILED_NOT_APK: bad file")
        assertTrue(msg.startsWith("Android rejected the update file."))
        assertTrue(msg.contains("INSTALL_PARSE_FAILED_NOT_APK"))
    }
}
