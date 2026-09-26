package com.daydreamin.app.ui.screens.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.player.RepeatMode
import com.daydreamin.app.player.SleepTimer
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.daydreamGlassStyle
import com.daydreamin.app.ui.theme.TextMuted
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
) {
    val vm: NowPlayingViewModel = composeViewModel()
    val playerMeta by PlayerController.meta.collectAsState()
    val progress by PlayerController.progress.collectAsState()
    val likedIds by vm.likedIds.collectAsState()
    val npStyle by vm.nowPlayingStyle.collectAsState()
    val sleepRemaining by SleepTimer.remainingSeconds.collectAsState()

    val song = playerMeta.currentSong
    var showSleepDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val hazeState = remember { HazeState() }
    val glassStyle = daydreamGlassStyle(tintAlpha = 0.35f, blurRadiusDp = 30)

    LaunchedEffect(playerMeta.error) {
        val message = playerMeta.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            PlayerController.consumeError()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgBase)) {
        // Liquid-glass backdrop: the album art itself, blown up and blurred, standing in
        // for the "ambient" background Apple Music-style now-playing screens use — the
        // control tray below then samples this via hazeEffect for a real frosted-glass look.
        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            if (!song?.artworkUrl.isNullOrBlank()) {
                Artwork(
                    url = song?.artworkUrl,
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(80.dp),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(BgBase))
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(BgBase.copy(alpha = 0.55f), BgBase.copy(alpha = 0.75f), BgBase.copy(alpha = 0.95f)),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(28.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            val breathTransition = rememberInfiniteTransition(label = "breathe")
            val breathScale by breathTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (playerMeta.isPlaying) 1.015f else 1f,
                animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
                label = "breathScale",
            )

            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = song?.playId,
                    transitionSpec = {
                        (fadeIn(tween(380)) + scaleIn(initialScale = 0.9f, animationSpec = tween(380))) togetherWith
                            fadeOut(tween(180))
                    },
                    label = "artworkCrossfade",
                ) {
                    when (npStyle) {
                        "Vinyl" -> {
                            val transition = rememberInfiniteTransition(label = "vinyl")
                            val angle by transition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
                                label = "angle",
                            )
                            Artwork(
                                url = song?.artworkUrl,
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxSize(0.85f)
                                    .rotate(if (playerMeta.isPlaying) angle else 0f),
                            )
                        }
                        "Minimal" -> Artwork(
                            url = song?.artworkUrl,
                            modifier = Modifier
                                .fillMaxSize(0.7f)
                                .graphicsLayer { scaleX = breathScale; scaleY = breathScale },
                            shape = RoundedCornerShape(12.dp),
                        )
                        else -> Artwork(
                            url = song?.artworkUrl,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { scaleX = breathScale; scaleY = breathScale },
                            shape = RoundedCornerShape(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(song?.artist ?: "Pick a song from Home or Search", style = MaterialTheme.typography.bodyLarge, color = TextSecondary, maxLines = 1)
                }
                if (song != null) {
                    val liked = song.playId in likedIds
                    val heartScale = remember { Animatable(1f) }
                    LaunchedEffect(liked) {
                        if (liked) {
                            heartScale.animateTo(1.35f, tween(140, easing = FastOutSlowInEasing))
                            heartScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                        }
                    }
                    IconButton(onClick = { vm.toggleLiked(song) }) {
                        Icon(
                            if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (liked) MaterialTheme.colorScheme.primary else TextSecondary,
                            modifier = Modifier.graphicsLayer { scaleX = heartScale.value; scaleY = heartScale.value },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val duration = progress.durationMs.coerceAtLeast(1L)
            Slider(
                value = progress.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                valueRange = 0f..duration.toFloat(),
                onValueChange = { PlayerController.seekTo(it.toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = TextMuted.copy(alpha = 0.3f),
                ),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(progress.positionMs), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(formatMs(progress.durationMs), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            // The glass control tray: everything below the scrubber sits on a real frosted
            // panel sampling the blurred artwork behind it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .hazeEffect(state = hazeState, style = glassStyle)
                    .padding(vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { PlayerController.toggleShuffle() }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (playerMeta.shuffle) MaterialTheme.colorScheme.primary else TextSecondary)
                    }
                    IconButton(onClick = { PlayerController.previous() }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = TextPrimary, modifier = Modifier.size(36.dp))
                    }
                    val playButtonInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val playButtonPressed by playButtonInteraction.collectIsPressedAsState()
                    val playButtonScale by animateFloatAsState(if (playButtonPressed) 0.9f else 1f, label = "playButtonPress")
                    val pulseTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by pulseTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (playerMeta.isPlaying) 1.05f else 1f,
                        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
                        label = "pulseScale",
                    )
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .graphicsLayer {
                                val s = pulseScale * playButtonScale
                                scaleX = s; scaleY = s
                            }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(
                                interactionSource = playButtonInteraction,
                                indication = null,
                                onClick = { PlayerController.togglePlayPause() },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (playerMeta.isBuffering) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                        } else {
                            AnimatedContent(
                                targetState = playerMeta.isPlaying,
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.5f, animationSpec = tween(180)) + fadeIn(tween(140))) togetherWith
                                        (scaleOut(targetScale = 0.5f, animationSpec = tween(120)) + fadeOut(tween(90)))
                                },
                                label = "playPauseIcon",
                            ) { isPlaying ->
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }
                    }
                    IconButton(onClick = { PlayerController.next() }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = TextPrimary, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { PlayerController.cycleRepeat() }) {
                        Icon(
                            if (playerMeta.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Repeat",
                            tint = if (playerMeta.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else TextSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onQueueClick) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = "Queue", tint = TextSecondary)
                    }
                    TextButton(onClick = onLyricsClick) { Text("Lyrics", color = TextPrimary) }
                    IconButton(onClick = { showSleepDialog = true }) {
                        Icon(Icons.Filled.Bedtime, contentDescription = "Sleep timer", tint = if (sleepRemaining != null) MaterialTheme.colorScheme.primary else TextSecondary)
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Info", tint = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            currentRemaining = sleepRemaining,
            onDismiss = { showSleepDialog = false },
            onPick = { minutes -> SleepTimer.start(minutes); showSleepDialog = false },
            onCancel = { SleepTimer.cancel(); showSleepDialog = false },
        )
    }

    if (showInfoDialog && song != null) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Close") } },
            title = { Text(song.title) },
            text = {
                Column {
                    Text("Artist: ${song.artist}")
                    Text("Album: ${song.album}")
                    Text("Genre: ${song.genre}")
                    if (song.duration > 0) Text("Duration: ${formatMs(song.duration * 1000L)}")
                    playerMeta.streamQuality?.let { Text("Streaming: $it (best YouTube offers — no lossless source exists)") }
                }
            },
        )
    }
}

@Composable
private fun SleepTimerDialog(
    currentRemaining: Int?,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                if (currentRemaining != null) {
                    Text("Pausing in ${currentRemaining / 60}m ${currentRemaining % 60}s", color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                }
                listOf(10, 20, 30, 45, 60).forEach { minutes ->
                    TextButton(onClick = { onPick(minutes) }) { Text("$minutes minutes", textAlign = TextAlign.Start) }
                }
            }
        },
        confirmButton = {
            if (currentRemaining != null) TextButton(onClick = onCancel) { Text("Turn off") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
