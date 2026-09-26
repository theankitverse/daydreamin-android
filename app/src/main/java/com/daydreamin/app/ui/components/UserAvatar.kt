package com.daydreamin.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.ui.theme.BrandPink
import com.daydreamin.app.ui.theme.BrandPurple
import com.daydreamin.app.ui.theme.BrandViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * The user's avatar: their photo if they set one, otherwise their initial on the brand gradient,
 * otherwise a quiet person glyph. [ring] adds the violet-to-pink ring used in headers.
 */
@Composable
fun UserAvatar(size: Dp, modifier: Modifier = Modifier, ring: Boolean = true, override: ImageBitmap? = null, overrideName: String? = null) {
    val prefs = DaydreaminApp.instance.prefs
    val storedName by prefs.userName.collectAsState(initial = "")
    val version by prefs.avatarVersion.collectAsState(initial = 0L)
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(version) {
        photo = if (version == 0L) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(prefs.avatarFile.absolutePath)?.asImageBitmap() }.getOrNull()
        }
    }
    val shown = override ?: photo
    val name = overrideName ?: storedName
    val inner = if (ring) size * 0.05f else 0.dp
    Box(
        modifier
            .size(size)
            .then(if (ring) Modifier.border(size * 0.02f + 1.dp, Brush.linearGradient(listOf(BrandPurple, BrandPink)), CircleShape) else Modifier)
            .padding(inner)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(BrandViolet.copy(alpha = 0.85f), BrandPink.copy(alpha = 0.7f)))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            shown != null -> Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            name.isNotBlank() -> Text(
                name.trim().take(1).uppercase(),
                style = TextStyle(fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.SemiBold, color = Color.White),
            )
            else -> Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/**
 * Turns a picked photo into the stored avatar: decoded small (never the full camera image in
 * memory), center-cropped square, 512 px, JPEG. Returns null if it can't be read.
 */
suspend fun encodeAvatar(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
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
