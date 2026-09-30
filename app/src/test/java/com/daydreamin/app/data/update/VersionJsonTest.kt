package com.daydreamin.app.data.update

import com.daydreamin.app.BuildConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
    }
}
