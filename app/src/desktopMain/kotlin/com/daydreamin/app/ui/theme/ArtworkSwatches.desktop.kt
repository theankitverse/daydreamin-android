package com.daydreamin.app.ui.theme

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android uses the Palette library; here, a small equivalent: the artwork is loaded at 96 px, its
 * pixels grouped into coarse colour buckets, and the most vivid well-represented bucket is the key.
 */
actual suspend fun artworkSwatches(model: String): ArtworkSwatches? {
    val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(model).size(96).build()
    val result = SingletonImageLoader.get(PlatformContext.INSTANCE).execute(request) as? SuccessResult ?: return null
    val bitmap = result.image.toBitmap()
    return withContext(Dispatchers.Default) {
        val counts = HashMap<Int, IntArray>() // bucket -> [count, rSum, gSum, bSum]
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            val c = bitmap.getColor(x, y)
            val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
            val key = (r shr 5 shl 6) or (g shr 5 shl 3) or (b shr 5)
            val e = counts.getOrPut(key) { IntArray(4) }
            e[0]++; e[1] += r; e[2] += g; e[3] += b
        }
        if (counts.isEmpty()) return@withContext null
        val total = counts.values.sumOf { it[0] }.toFloat()
        val swatches = counts.values.filter { it[0] / total > 0.01f }.map { e ->
            val rgb = (0xFF shl 24) or ((e[1] / e[0]) shl 16) or ((e[2] / e[0]) shl 8) or (e[3] / e[0])
            val hsv = rgbToHsv(rgb)
            Triple(rgb, e[0] / total, hsv)
        }
        // Vivid: saturated and not too dark, weighted by how much of the cover it covers.
        val key = swatches.maxByOrNull { (_, share, hsv) -> hsv[1] * hsv[2] * (0.3f + share) }?.first ?: return@withContext null
        val fills = swatches.sortedByDescending { it.second }.map { it.first }.filter { it != key }
        ArtworkSwatches(key, fills)
    }
}
