package com.daydreamin.app.ui.components

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
import com.daydreamin.app.AppGraph
import com.daydreamin.app.platform.decodeImage
import com.daydreamin.app.ui.theme.BrandPink
import com.daydreamin.app.ui.theme.BrandPurple
import com.daydreamin.app.ui.theme.BrandViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The user's avatar: their photo if they set one, otherwise their initial on the brand gradient,
 * otherwise a quiet person glyph. [ring] adds the violet-to-pink ring used in headers.
 */
@Composable
fun UserAvatar(size: Dp, modifier: Modifier = Modifier, ring: Boolean = true, override: ImageBitmap? = null, overrideName: String? = null) {
    val prefs = AppGraph.prefs
    val storedName by prefs.userName.collectAsState(initial = "")
    val version by prefs.avatarVersion.collectAsState(initial = 0L)
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(version) {
        photo = if (version == 0L) null else withContext(Dispatchers.IO) {
            runCatching { decodeImage(prefs.avatarFile.readBytes()) }.getOrNull()
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
