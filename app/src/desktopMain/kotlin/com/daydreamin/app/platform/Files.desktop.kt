package com.daydreamin.app.platform

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.prefs.AppPreferences
import com.daydreamin.app.data.prefs.BackupFormatException
import com.daydreamin.app.data.prefs.RestoreResult
import com.daydreamin.app.data.prefs.parseLibraryBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual class PickedFile(val file: File)

/** A real backup is a few hundred KB even for thousands of songs (plus a small photo); anything far past this isn't one. */
private const val MAX_BACKUP_BYTES = 10L * 1024 * 1024

@Composable
actual fun rememberPicker(kind: PickKind, onPicked: (PickedFile?) -> Unit): PickerLauncher {
    val callback = rememberUpdatedState(onPicked)
    return remember(kind) {
        PickerLauncher { suggestedName ->
            // The system's own dialog (Finder's on a Mac), so it looks and behaves like every other app.
            val saving = kind == PickKind.SAVE_BACKUP
            val dialog = FileDialog(null as Frame?, if (saving) "Export library" else "Choose a file", if (saving) FileDialog.SAVE else FileDialog.LOAD)
            when (kind) {
                PickKind.PHOTO -> dialog.setFilenameFilter { _, name -> name.lowercase().substringAfterLast('.') in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") }
                PickKind.SAVE_BACKUP -> dialog.file = suggestedName
                PickKind.OPEN_PLAYLIST -> dialog.setFilenameFilter { _, name -> name.lowercase().substringAfterLast('.') in setOf("csv", "m3u", "m3u8", "txt") }
                else -> dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".json") || name.lowercase().endsWith(".txt") }
            }
            dialog.isVisible = true
            val picked = dialog.file?.let { File(dialog.directory, it) }
            callback.value(picked?.let(::PickedFile))
        }
    }
}

actual suspend fun PickedFile.writeText(text: String) = withContext(Dispatchers.IO) { file.writeText(text, Charsets.UTF_8) }

actual suspend fun PickedFile.readText(): String = withContext(Dispatchers.IO) { com.daydreamin.app.data.library.PlaylistFile.read(file.inputStream()) }

actual suspend fun PickedFile.displayName(): String = file.name

actual fun shareTargets(): List<ShareTarget> = emptyList()
actual val hasSystemShare: Boolean = false
actual val canSaveImage: Boolean = true
actual val canCopyImage: Boolean = true

actual suspend fun shareImage(png: ByteArray, fileName: String, text: String, target: String?) {
    saveImage(png, fileName)
    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
}

actual suspend fun saveImage(png: ByteArray, fileName: String): String? = withContext(Dispatchers.IO) {
    val downloads = File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))
    runCatching { File(downloads, fileName).writeBytes(png); downloads.name }.getOrNull()
}

actual suspend fun copyImage(png: ByteArray) = withContext(Dispatchers.IO) {
    val picture = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(png))
    val flavor = java.awt.datatransfer.DataFlavor.imageFlavor
    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(object : java.awt.datatransfer.Transferable {
        override fun getTransferDataFlavors() = arrayOf(flavor)
        override fun isDataFlavorSupported(f: java.awt.datatransfer.DataFlavor) = f == flavor
        override fun getTransferData(f: java.awt.datatransfer.DataFlavor): Any = picture
    }, null)
}

actual fun androidx.compose.ui.graphics.ImageBitmap.encodePng(): ByteArray =
    org.jetbrains.skia.Image.makeFromBitmap(asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes

actual fun coil3.request.ImageRequest.Builder.forOffscreenDrawing(): coil3.request.ImageRequest.Builder = this

actual suspend fun restoreLibrary(prefs: AppPreferences, file: PickedFile, keepBackingUpHere: Boolean): RestoreResult {
    val text = withContext(Dispatchers.IO) {
        if (file.file.length() > MAX_BACKUP_BYTES) throw BackupFormatException("That file is too large to be a Daydreamin backup.")
        file.file.readText(Charsets.UTF_8)
    }
    val backup = parseLibraryBackup(text)
    val summary = prefs.importLibrary(backup)
    val profileApplied = prefs.applyProfileIfFresh(backup.profile)
    return RestoreResult(summary, profileApplied)
}

actual suspend fun encodeAvatar(file: PickedFile): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val src = Image.makeFromEncoded(file.file.readBytes())
        val side = minOf(src.width, src.height)
        val surface = Surface.makeRasterN32Premul(512, 512)
        val left = (src.width - side) / 2f; val top = (src.height - side) / 2f
        surface.canvas.drawImageRect(src, Rect.makeXYWH(left, top, side.toFloat(), side.toFloat()), Rect.makeWH(512f, 512f))
        surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 88)?.bytes
    }.getOrNull()
}

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

// The automatic copy outside the app (Android keeps it in Download/Daydreamin) comes to desktop later;
// Export / Import work in the meantime.
actual object AutoBackup {
    actual val isSupported: Boolean = false
    actual suspend fun writeNow(prefs: AppPreferences): Boolean = false
}

actual object DeviceStorage {
    actual fun audioCacheBytes(): Long =
        File(AppGraph.filesDir, "audio_cache").walkTopDown().filter { it.isFile }.sumOf { it.length() }
    actual val audioCacheCapBytes: Long = 1024L * 1024 * 1024
}

actual val platformContext: coil3.PlatformContext get() = coil3.PlatformContext.INSTANCE
