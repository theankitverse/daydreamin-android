package com.daydreamin.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * `--snapshot <dir>`: renders the app off-screen at a phone, a tablet and a desktop window size and
 * saves a PNG of each — for checking layouts where there's no display to look at (CI, Windows and
 * Linux build machines) and for comparing sizes side by side.
 */
fun saveSnapshots(dir: File, pages: List<String>, content: @Composable (page: String) -> Unit) {
    dir.mkdirs()
    for (page in pages) {
        for ((name, size) in listOf("phone" to (400 to 860), "desktop" to (1280 to 820), "wide" to (1720 to 980))) {
            val file = File(dir, "${page.replace('/', '_')}-$name.png")
            renderOffscreen(file, size.first, size.second, settleMs = 9_000) { content(page) }
            println("snapshot: ${file.absolutePath}")
        }
    }
}

private class SnapshotOwner : LifecycleOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
}

// On the UI thread (Swing's), like a real window: navigation and lifecycles insist on it.
private fun renderOffscreen(file: File, widthDp: Int, heightDp: Int, settleMs: Long, content: @Composable () -> Unit) = runBlocking(Dispatchers.Main) {
    val owner = SnapshotOwner()
    val scene = ImageComposeScene(widthDp * 2, heightDp * 2, Density(2f), coroutineContext = coroutineContext) {
        CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalViewModelStoreOwner provides owner) { content() }
    }
    // Let data and artwork arrive: keep drawing frames (which also runs the screen's effects) for a
    // while — ten a second is plenty to settle animations, and drawing is slow off-screen (no GPU).
    var t = 0L
    while (t < settleMs * 1_000_000) {
        scene.render(t)
        delay(100); t += 100_000_000
    }
    file.writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    owner.viewModelStore.clear()
}
