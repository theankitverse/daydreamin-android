package com.daydreamin.app.platform

import coil3.request.allowHardware
import androidx.compose.ui.graphics.asAndroidBitmap
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
        PickKind.OPEN_PLAYLIST -> {
            val l = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), deliver)
            // Everything: phones label CSV and M3U files all sorts of ways.
            PickerLauncher { l.launch(arrayOf("*/*")) }
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

actual suspend fun PickedFile.readText(): String = withContext(Dispatchers.IO) {
    com.daydreamin.app.DaydreaminApp.instance.contentResolver.openInputStream(uri)?.let(com.daydreamin.app.data.library.PlaylistFile::read)
        ?: throw java.io.IOException("can't open $uri")
}

actual suspend fun PickedFile.displayName(): String = withContext(Dispatchers.IO) {
    com.daydreamin.app.DaydreaminApp.instance.contentResolver
        .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
}

private val POPULAR_APPS = listOf(
    "com.whatsapp" to "WhatsApp", "com.instagram.android" to "Instagram", "org.telegram.messenger" to "Telegram",
    "com.snapchat.android" to "Snapchat", "com.twitter.android" to "X",
)

actual fun shareTargets(): List<ShareTarget> {
    val packages = com.daydreamin.app.DaydreaminApp.instance.packageManager
    return POPULAR_APPS.mapNotNull { (id, label) ->
        val icon = runCatching { packages.getApplicationIcon(id) }.getOrNull() ?: return@mapNotNull null
        val bitmap = android.graphics.Bitmap.createBitmap(144, 144, android.graphics.Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 144, 144)
        icon.draw(android.graphics.Canvas(bitmap))
        ShareTarget(id, label, bitmap.asImageBitmap())
    }
}

actual val hasSystemShare: Boolean = true
// Older Androids need a storage permission just for this; they still have Share.
actual val canSaveImage: Boolean get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
actual val canCopyImage: Boolean = false

actual suspend fun shareImage(png: ByteArray, fileName: String, text: String, target: String?) {
    val app = com.daydreamin.app.DaydreaminApp.instance
    val uri = withContext(Dispatchers.IO) {
        val dir = java.io.File(app.cacheDir, "shared").apply { mkdirs() }
        val file = java.io.File(dir, fileName).apply { writeBytes(png) }
        androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.share", file)
    }
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_TEXT, text)
        clipData = android.content.ClipData.newRawUri(null, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        target?.let(::setPackage)
    }
    val launch = if (target != null) send else android.content.Intent.createChooser(send, null)
    app.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

actual suspend fun saveImage(png: ByteArray, fileName: String): String? = withContext(Dispatchers.IO) {
    if (!canSaveImage) return@withContext null
    val resolver = com.daydreamin.app.DaydreaminApp.instance.contentResolver
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Daydreamin")
    }
    runCatching {
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        resolver.openOutputStream(uri)?.use { it.write(png) } ?: return@withContext null
        "Pictures"
    }.getOrNull()
}

actual suspend fun copyImage(png: ByteArray) = Unit

actual fun androidx.compose.ui.graphics.ImageBitmap.encodePng(): ByteArray =
    java.io.ByteArrayOutputStream().also { out ->
        asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    }.toByteArray()

actual fun coil3.request.ImageRequest.Builder.forOffscreenDrawing(): coil3.request.ImageRequest.Builder = allowHardware(false)

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
