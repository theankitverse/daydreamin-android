package com.daydreamin.app.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.prefs.AppPreferences
import com.daydreamin.app.data.prefs.RestoreResult
import com.daydreamin.app.data.prefs.restoreLibraryFromUri
import com.daydreamin.app.player.AudioDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

actual class PickedFile(val uri: Uri)

private val BACKUP_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

@Composable
actual fun rememberPicker(kind: PickKind, onPicked: (PickedFile?) -> Unit): PickerLauncher {
    val callback = rememberUpdatedState(onPicked)
    val deliver: (Uri?) -> Unit = { uri -> callback.value(uri?.let(::PickedFile)) }
    return when (kind) {
        PickKind.OPEN_BACKUP -> {
            val l = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), deliver)
            PickerLauncher { l.launch(BACKUP_TYPES) }
        }
        PickKind.RESTORE_BACKUP -> {
            val l = rememberLauncherForActivityResult(OpenBackupDocument(), deliver)
            PickerLauncher { l.launch(BACKUP_TYPES) }
        }
        PickKind.SAVE_BACKUP -> {
            val l = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), deliver)
            PickerLauncher { name -> l.launch(name) }
        }
        PickKind.PHOTO -> {
            val l = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), deliver)
            PickerLauncher { l.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
    }
}

/** The system file picker, opened right at Download/Daydreamin where the automatic backup lives. */
private class OpenBackupDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): android.content.Intent =
        super.createIntent(context, input).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, AutoBackup.pickerStartUri)
            }
        }
}

actual suspend fun PickedFile.writeText(text: String) = withContext(Dispatchers.IO) {
    val stream = DaydreaminApp.instance.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no output stream")
    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

actual suspend fun restoreLibrary(prefs: AppPreferences, file: PickedFile, keepBackingUpHere: Boolean): RestoreResult =
    restoreLibraryFromUri(DaydreaminApp.instance, prefs, file.uri, keepBackingUpHere)

actual suspend fun encodeAvatar(file: PickedFile): ByteArray? = encodeAvatarFromUri(DaydreaminApp.instance, file.uri)

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

actual object DeviceStorage {
    // Measured from the files themselves, so reading it never touches the player's live cache.
    actual fun audioCacheBytes(): Long =
        File(DaydreaminApp.instance.cacheDir, "audio_cache").walkTopDown().filter { it.isFile }.sumOf { it.length() }
    actual val audioCacheCapBytes: Long = AudioDiskCache.MAX_CACHE_BYTES
}

actual val platformContext: coil3.PlatformContext get() = DaydreaminApp.instance

/**
 * Turns a picked photo into the stored avatar: decoded small (never the full camera image in
 * memory), center-cropped square, 512 px, JPEG. Returns null if it can't be read.
 */
internal suspend fun encodeAvatarFromUri(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val src: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val shortest = minOf(info.size.width, info.size.height)
                if (shortest > 1024) decoder.setTargetSampleSize(shortest / 1024)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = (minOf(bounds.outWidth, bounds.outHeight) / 1024).coerceAtLeast(1)
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return@runCatching null
        }
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            out.toByteArray()
        }
    }.getOrNull()
}
