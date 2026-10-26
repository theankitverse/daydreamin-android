package com.daydreamin.app

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** The window's size and place, so it opens where you left it. Kept in the app's data folder (window.json). */
@Serializable
data class WindowMemory(val x: Float, val y: Float, val width: Float, val height: Float, val maximized: Boolean) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(dir: File): WindowMemory? = runCatching {
            json.decodeFromString(serializer(), File(dir, "window.json").readText())
        }.getOrNull()?.takeIf { it.width >= 380 && it.height >= 560 }

        fun isOnScreen(m: WindowMemory): Boolean {
            if (m.x.isNaN() || m.y.isNaN()) return false
            val screens = runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds } }.getOrDefault(emptyList())
            return screens.any { it.contains((m.x + 40).toInt(), (m.y + 20).toInt()) }
        }

        fun save(dir: File, state: WindowState) {
            runCatching {
                val memory = WindowMemory(
                    x = state.position.x.value, y = state.position.y.value,
                    width = state.size.width.value, height = state.size.height.value,
                    maximized = state.placement == WindowPlacement.Maximized,
                )
                File(dir, "window.json").writeText(json.encodeToString(serializer(), memory))
            }
        }
    }
}
