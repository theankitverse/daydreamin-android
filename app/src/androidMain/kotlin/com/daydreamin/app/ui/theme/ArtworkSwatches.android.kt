package com.daydreamin.app.ui.theme

import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.daydreamin.app.DaydreaminApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun artworkSwatches(model: String): ArtworkSwatches? {
    val context = DaydreaminApp.instance
    val request = ImageRequest.Builder(context)
        .data(model)
        .size(96)
        .allowHardware(false) // Palette needs to read pixels
        .build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    val bitmap = result.image.toBitmap()
    return withContext(Dispatchers.Default) {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val key = (palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch
            ?: palette.mutedSwatch ?: palette.dominantSwatch)?.rgb ?: return@withContext null
        val fills = listOfNotNull(palette.darkVibrantSwatch, palette.mutedSwatch, palette.lightMutedSwatch, palette.dominantSwatch, palette.lightVibrantSwatch)
        ArtworkSwatches(key, fills.map { it.rgb })
    }
}
