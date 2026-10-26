package com.daydreamin.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledFromTest {
    @Test fun `installers read as plain names`() {
        assertEquals("Chrome", UsageStats.installerName("com.android.chrome"))
        assertEquals("WhatsApp", UsageStats.installerName("com.whatsapp"))
        assertEquals("Telegram", UsageStats.installerName("org.telegram.messenger"))
        assertEquals("File manager", UsageStats.installerName("com.mi.android.globalFileexplorer"))
        assertEquals("In-app update", UsageStats.installerName("com.daydreamin.app"))
        assertEquals("Unknown", UsageStats.installerName(null))
        assertEquals("com.example.other", UsageStats.installerName("com.example.other"))
    }
}
