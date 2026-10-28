package com.daydreamin.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What a listener does first: open the app (through first run), scroll Home, visit each tab. */
@RunWith(AndroidJUnit4::class)
class StartupProfile {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.daydreamin.app", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.finishFirstRun()
        device.wait(Until.hasObject(By.text("Library")), 20_000)
        device.scrollMain()
        for (tab in listOf("Search", "Library", "Profile", "Home")) {
            device.findObject(By.text(tab))?.click() ?: continue
            device.waitForIdle()
            device.scrollMain()
        }
    }
}

private fun UiDevice.finishFirstRun() {
    for (button in listOf("Get started", "Skip", "Start listening")) {
        wait(Until.findObject(By.text(button)), 8_000)?.click() ?: return
        waitForIdle()
    }
}

private fun UiDevice.scrollMain() {
    // Plain swipes rather than a found list: screens recompose as songs load, which would leave a found list stale.
    val x = displayWidth / 2
    repeat(2) { swipe(x, displayHeight * 3 / 4, x, displayHeight / 4, 12); waitForIdle() }
    swipe(x, displayHeight / 4, x, displayHeight * 3 / 4, 12)
    waitForIdle()
}
