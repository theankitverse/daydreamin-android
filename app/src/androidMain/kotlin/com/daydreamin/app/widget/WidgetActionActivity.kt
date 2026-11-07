package com.daydreamin.app.widget

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.daydreamin.app.player.PlayerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What a widget button opens: nothing to see, and touches pass through it. It's here because with
 * the app closed, Android gives a background process no network and soon freezes it, so a press
 * handled in the background can't load the last song. A screen counts as the app being in use
 * (and a widget tap may always open one) until the music starts and the player keeps itself going.
 */
class WidgetActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        val action = intent.action
        lifecycleScope.launch {
            PlayerController.ensureConnected(applicationContext)
            // The app may have been closed: connect, and give the last song a moment to come back.
            withTimeoutOrNull(4_000) {
                while (!PlayerController.isConnected || PlayerController.meta.value.currentSong == null) delay(50)
            }
            val wasPlaying = PlayerController.meta.value.isPlaying
            when (action) {
                NowPlayingWidget.ACTION_PLAY -> PlayerController.togglePlayPause()
                NowPlayingWidget.ACTION_NEXT -> PlayerController.next()
                NowPlayingWidget.ACTION_PREVIOUS -> PlayerController.previous()
            }
            // Pausing needs nothing more. Starting takes as long as the song takes to load.
            if (action != NowPlayingWidget.ACTION_PLAY || !wasPlaying) {
                withTimeoutOrNull(20_000) { PlayerController.meta.first { it.isPlaying && !it.isBuffering } }
            }
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
