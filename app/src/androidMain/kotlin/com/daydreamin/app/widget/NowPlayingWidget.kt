package com.daydreamin.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.MainActivity
import com.daydreamin.app.R
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.theme.ArtworkColors
import com.daydreamin.app.ui.theme.artworkModel
import com.daydreamin.app.ui.theme.artworkZoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color as ComposeColor

/** Narrower than this, the widget is the small square one. */
private const val WIDE_MIN_DP = 200
private const val DARK = 0xFF16161C.toInt()
private const val ART_PX = 256

/**
 * Daydreamin on the home screen: the song, its artwork, and play and skip, on a card tinted with
 * the artwork's own colour. The buttons work with the app closed: see [WidgetActionActivity].
 */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh()

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh()

    /** After an update or a restart the widget is back to its plain layout: redraw it, and stay alive until that's done. */
    private fun refresh() {
        val pending = goAsync()
        DaydreaminApp.instance.appScope.launch {
            try { WidgetState.draw(force = true) } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_PLAY = "com.daydreamin.app.widget.PLAY"
        const val ACTION_NEXT = "com.daydreamin.app.widget.NEXT"
        const val ACTION_PREVIOUS = "com.daydreamin.app.widget.PREVIOUS"
    }
}

/** Keeps every widget in step with the player. */
object WidgetState {
    private var drawnSong: String? = null
    private var art: Bitmap? = null
    private var tint = DARK

    fun follow(scope: CoroutineScope) {
        scope.launch {
            PlayerController.meta
                .map { it.currentSong to it.isPlaying }
                .distinctUntilChanged()
                .collectLatest { draw(force = false) }
        }
    }

    suspend fun draw(force: Boolean) {
        val app = DaydreaminApp.instance
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, NowPlayingWidget::class.java))
        if (ids.isEmpty()) return
        val meta = PlayerController.meta.value
        // With the app closed there's no current song yet: show the last one, ready to play.
        val song = meta.currentSong ?: app.prefs.history.first().firstOrNull()
        if (song?.playId != drawnSong || (force && art == null)) {
            drawnSong = song?.playId
            art = null
            tint = DARK
            ids.forEach { manager.updateAppWidget(it, views(app, manager, it, song, meta.isPlaying)) } // the words at once, the picture when it's here
            song?.let { loadArt(it) }
        }
        ids.forEach { manager.updateAppWidget(it, views(app, manager, it, song, meta.isPlaying)) }
    }

    private suspend fun loadArt(song: Song) {
        val url = song.cover.ifBlank { song.artworkUrl }.takeIf { it.isNotBlank() } ?: return
        val app = DaydreaminApp.instance
        val request = ImageRequest.Builder(app).data(artworkModel(url)).size(ART_PX * 2).allowHardware(false).build()
        val bitmap = (SingletonImageLoader.get(app).execute(request) as? SuccessResult)?.image?.toBitmap()
        art = bitmap?.let { rounded(it, artworkZoom(url)) }
        runCatching { ArtworkColors.extract(url) }.getOrNull()?.let { light ->
            tint = lerp(light.key, ComposeColor(DARK), 0.62f).toArgb()
        }
    }

    /** A square from the middle of [source], zoomed past YouTube's letterbox bars, with soft corners. */
    private fun rounded(source: Bitmap, zoom: Float): Bitmap {
        val out = Bitmap.createBitmap(ART_PX, ART_PX, Bitmap.Config.ARGB_8888)
        val scale = ART_PX.toFloat() / minOf(source.width, source.height) * zoom
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((ART_PX - source.width * scale) / 2f, (ART_PX - source.height * scale) / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        }
        Canvas(out).drawRoundRect(RectF(0f, 0f, ART_PX.toFloat(), ART_PX.toFloat()), 30f, 30f, paint)
        return out
    }

    private fun views(context: Context, manager: AppWidgetManager, id: Int, song: Song?, playing: Boolean): RemoteViews {
        val width = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, Int.MAX_VALUE)
        val wide = width >= WIDE_MIN_DP
        return RemoteViews(context.packageName, if (wide) R.layout.widget_wide else R.layout.widget_small).apply {
            setTextViewText(R.id.widget_title, song?.title ?: "Daydreamin")
            setTextViewText(R.id.widget_artist, song?.artist ?: "Tap to start listening")
            art?.let { setImageViewBitmap(R.id.widget_art, it) } ?: setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher)
            setInt(R.id.widget_tint, "setColorFilter", tint)
            setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            setOnClickPendingIntent(R.id.widget_root, openApp(context, song != null))
            setOnClickPendingIntent(R.id.widget_play, press(context, NowPlayingWidget.ACTION_PLAY))
            setOnClickPendingIntent(R.id.widget_next, press(context, NowPlayingWidget.ACTION_NEXT))
            if (wide) setOnClickPendingIntent(R.id.widget_prev, press(context, NowPlayingWidget.ACTION_PREVIOUS))
        }
    }

    private fun press(context: Context, action: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            action.hashCode(),
            Intent(context, WidgetActionActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openApp(context: Context, toPlayer: Boolean): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                if (toPlayer) action = MainActivity.ACTION_OPEN_PLAYER
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
