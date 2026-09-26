package com.daydreamin.app.ui.screens.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.player.SleepTimer
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.ArtworkBackdrop
import com.daydreamin.app.ui.components.PlayerSheet
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.screens.lyrics.LyricsViewModel
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.glass
import com.daydreamin.app.ui.theme.rememberArtworkLight
import kotlinx.coroutines.delay
import coil.imageLoader
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

private val DefaultLight = ArtworkLight(key = BrandViolet, fill = Color(0xFF3D3A9E))
private val MiniArtRadius = 12.dp
private val BigArtRadius = 20.dp
private val SmallArtRadius = 10.dp
private val SmallArtSize = 56.dp
private val Gutter = 28.dp

/**
 * Now Playing. Everything is built outward from the artwork: the cover itself, blown up and
 * blurred into the room's light; its colors as a glow beneath it; glass controls in that light;
 * type on top. Lyrics live here too — the artwork shrinks into a header and the words take the
 * stage — so it's one continuous space rather than a second screen.
 *
 * [visibility] is the navigation transition this screen is entering/leaving with: the open/close
 * choreography (artwork flying between here and the mini player) is driven from it, so the
 * navigation system keeps both screens alive for exactly as long as the flight takes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    visibility: AnimatedVisibilityScope,
    closingToMini: () -> Boolean,
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
) {
    val vm: NowPlayingViewModel = composeViewModel()
    val lyricsVm: LyricsViewModel = composeViewModel()
    val meta by PlayerController.meta.collectAsState()
    val progressState = PlayerController.progress.collectAsState()
    val likedIds by vm.likedIds.collectAsState()
    val lyrics by lyricsVm.state.collectAsState()
    val song = meta.currentSong
    val position = rememberSmoothPosition(progressState, meta.isPlaying)
    // Between tapping a song and the player having it (the stream being found), the player reports
    // neither playing nor buffering — but no duration is known yet, which is the tell.
    // Capped at 20s, so a song that never loads can't leave a spinner running forever.
    val gaveUpWaiting by androidx.compose.runtime.produceState(false, song?.playId) { value = false; delay(20_000); value = true }
    val loading = meta.isBuffering || (song != null && !gaveUpWaiting && !meta.isPlaying && progressState.value.durationMs <= 0L)
    val light = rememberArtworkLight(song?.cover?.ifBlank { song.artworkUrl }, DefaultLight)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    LaunchedEffect(song?.playId) { song?.let { lyricsVm.load(it.artist, it.title) } }
    // Warm up the next song's cover so skipping lands on real artwork, not an empty frame.
    val context = androidx.compose.ui.platform.LocalContext.current
    val upNextArt = meta.queue.firstOrNull()?.artworkUrl
    LaunchedEffect(upNextArt) {
        if (!upNextArt.isNullOrBlank()) {
            context.imageLoader.enqueue(coil.request.ImageRequest.Builder(context).data(com.daydreamin.app.ui.theme.artworkModel(upNextArt)).size(1080).build())
        }
    }

    // ---- open / close choreography
    val morphIn = remember { PlayerSheet.isMorphPending }
    val expansion = visibility.transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.86f, stiffness = 300f, visibilityThreshold = 0.001f) },
        label = "expansion",
    ) { state ->
        when (state) {
            EnterExitState.PreEnter -> if (morphIn) 0f else 1f
            EnterExitState.Visible -> 1f
            EnterExitState.PostExit -> if (closingToMini()) 0f else 1f
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { expansion.value }.collect { e ->
            PlayerSheet.expansion = e
            if (e >= 0.999f) PlayerSheet.finishMorphOpen()
        }
    }
    DisposableEffect(Unit) { onDispose { PlayerSheet.expansion = 0f } }

    // ---- drag down to dismiss
    val dragY = remember { Animatable(0f) }
    val dismissPx = with(density) { 140.dp.toPx() }
    val dismiss = {
        scope.launch { dragY.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 300f)) }
        onBack()
    }
    val dragToDismiss = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dy ->
                change.consume()
                scope.launch { dragY.snapTo((dragY.value + dy).coerceAtLeast(0f)) }
            },
            onDragEnd = {
                if (dragY.value > dismissPx) dismiss()
                else scope.launch { dragY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f)) }
            },
            onDragCancel = { scope.launch { dragY.animateTo(0f) } },
        )
    }

    // ---- lyrics mode
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val lyricsT by animateFloatAsState(if (showLyrics) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 260f), label = "lyricsT")
    BackHandler(enabled = showLyrics) { showLyrics = false }

    // Artwork rests a little smaller while paused — the song is "set down", not stopped dead.
    val restScale by animateFloatAsState(if (meta.isPlaying || loading) 1f else 0.86f, spring(dampingRatio = 0.62f, stiffness = 220f), label = "restScale")

    // ---- geometry shared between the slots and the flying artwork (all in `stage` coordinates)
    var stageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var bigRect by remember { mutableStateOf(Rect.Zero) }
    var smallRect by remember { mutableStateOf(Rect.Zero) }
    fun LayoutCoordinates.inStage(): Rect? = stageCoords?.takeIf { it.isAttached && isAttached }?.localBoundingBoxOf(this)

    val e = { expansion.value }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = dragY.value }
            .onGloballyPositioned { stageCoords = it }
            .drawBehind { drawRect(Color.Black.copy(alpha = (e() * 1.7f).coerceIn(0f, 1f))) },
    ) {
        // The room darkens quickly (the screen underneath is gone before the player's own text
        // arrives, so the two never overlap); the player's content fades in over the second half.
        PlayerAtmosphere(song, light, alpha = { (e() * 1.4f).coerceIn(0f, 1f) })

        // Everything except the artwork fades with the open/close; the artwork flies instead.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = ((e() - 0.45f) / 0.55f).coerceIn(0f, 1f) }
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            TopBar(onClose = dismiss, modifier = dragToDismiss)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Player stage: the big artwork slot and the title.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        // Staged: each stage's text leaves in the first part of the move and arrives in
                        // the last part, so it never sits under the artwork while it travels.
                        .graphicsLayer { alpha = (1f - lyricsT * 2.2f).coerceIn(0f, 1f) }
                        .then(if (!showLyrics) dragToDismiss else Modifier),
                ) {
                    val artSize = minOf(maxWidth - Gutter * 2, maxHeight - 112.dp)
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.weight(0.5f))
                        Box(
                            Modifier
                                .size(artSize)
                                .onGloballyPositioned { c -> c.inStage()?.let { if (it != bigRect) bigRect = it } },
                        )
                        Spacer(Modifier.weight(1f))
                        if (song != null) {
                            TitleRow(
                                song = song,
                                liked = song.playId in likedIds,
                                onLike = { vm.toggleLiked(song) },
                                modifier = Modifier.padding(horizontal = Gutter),
                                large = true,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }

                // Lyrics stage: the small artwork slot in a header, then the words.
                Column(Modifier.fillMaxSize().graphicsLayer { alpha = ((lyricsT - 0.55f) / 0.45f).coerceIn(0f, 1f) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 4.dp, bottom = 6.dp).then(if (showLyrics) dragToDismiss else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(SmallArtSize)
                                .onGloballyPositioned { c -> c.inStage()?.let { if (it != smallRect) smallRect = it } },
                        )
                        Spacer(Modifier.width(14.dp))
                        if (song != null) {
                            TitleRow(
                                song = song,
                                liked = song.playId in likedIds,
                                onLike = { vm.toggleLiked(song) },
                                modifier = Modifier.weight(1f),
                                large = false,
                            )
                        }
                    }
                    if (showLyrics || lyricsT > 0.01f) {
                        LyricsPane(
                            state = lyrics,
                            position = { position.value },
                            onSeek = PlayerController::seekTo,
                            onRetry = lyricsVm::retry,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = Gutter).padding(bottom = 10.dp)) {
                Scrubber(
                    position = { position.value },
                    durationMs = progressState.value.durationMs,
                    accent = light.key,
                    onSeek = PlayerController::seekTo,
                )
                Spacer(Modifier.height(4.dp))
                Transport(
                    isPlaying = meta.isPlaying || loading, // starting up counts as playing: the button offers "pause"
                    isBuffering = loading,
                    accent = light.key,
                    onPrevious = PlayerController::previous,
                    onPlayPause = PlayerController::togglePlayPause,
                    onNext = PlayerController::next,
                )
                Spacer(Modifier.height(10.dp))
                ActionRow(
                    lyricsOn = showLyrics,
                    shuffleOn = meta.shuffle,
                    repeatMode = meta.repeatMode,
                    onLyrics = { showLyrics = !showLyrics },
                    onShuffle = PlayerController::toggleShuffle,
                    onRepeat = PlayerController::cycleRepeat,
                    onQueue = onQueueClick,
                )
            }
        }

        // ---- the one artwork, flying between the mini player, the big slot and the lyrics header
        if (song != null && bigRect.width > 0f) {
            val bigW = with(density) { bigRect.width.toDp() }
            val miniRadiusPx = with(density) { MiniArtRadius.toPx() }
            val bigRadiusPx = with(density) { BigArtRadius.toPx() }
            val smallRadiusPx = with(density) { SmallArtRadius.toPx() }
            val shadowPx = with(density) { 26.dp.toPx() }
            // Colored light pooling beneath the artwork — brighter while it plays.
            Box(
                Modifier
                    .offset { IntOffset(bigRect.left.roundToInt(), bigRect.top.roundToInt()) }
                    .size(bigW)
                    .graphicsLayer {
                        alpha = (1f - lyricsT) * e().coerceIn(0f, 1f) * (0.55f + 0.45f * ((restScale - 0.86f) / 0.14f).coerceIn(0f, 1f))
                        scaleX = 1.25f * restScale; scaleY = 1.25f * restScale
                        translationY = size.height * 0.08f
                    }
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                0f to light.key.copy(alpha = 0.55f),
                                0.55f to light.fill.copy(alpha = 0.18f),
                                1f to Color.Transparent,
                                radius = size.minDimension * 0.62f,
                            ),
                        )
                    },
            )
            Box(
                Modifier
                    .offset { IntOffset(bigRect.left.roundToInt(), bigRect.top.roundToInt()) }
                    .size(bigW)
                    .graphicsLayer {
                        var target = lerp(bigRect.scaledAroundCenter(restScale), smallRect, lyricsT)
                        val miniBounds = PlayerSheet.miniArtworkBounds
                        val coords = stageCoords
                        val ex = e()
                        if (ex < 1f && miniBounds != null && coords != null && coords.isAttached) {
                            val tl = coords.windowToLocal(miniBounds.topLeft)
                            target = lerp(Rect(tl, miniBounds.size), target, ex)
                        }
                        val s = target.width / bigRect.width
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = target.left - bigRect.left
                        translationY = target.top - bigRect.top
                        val visibleRadius = lerp(miniRadiusPx, lerp(bigRadiusPx, smallRadiusPx, lyricsT), ex.coerceIn(0f, 1f))
                        shape = RoundedCornerShape(visibleRadius / s)
                        clip = true
                        shadowElevation = shadowPx * (1f - lyricsT) * ex.coerceIn(0f, 1f)
                        ambientShadowColor = Color.Black
                        spotShadowColor = Color.Black
                    }
                    .then(
                        if (showLyrics) {
                            // Tapping the small header artwork goes back to the full player.
                            Modifier.pointerInput(Unit) { detectTapGestures { showLyrics = false } }
                        } else {
                            dragToDismiss // the artwork sits on top of the stage, so it has to carry the pull-down too
                        },
                    ),
            ) {
                // A new song's cover dissolves in over the old one rather than replacing it.
                Crossfade(targetState = song.artworkUrl, animationSpec = tween(450), label = "artworkChange") { url ->
                    // Opaque underlay: while a cover loads, the glow beneath must not show through the frame.
                    Box(Modifier.fillMaxSize().background(Color(0xFF141417))) {
                        Artwork(url = url, shape = RectangleShape, edge = false, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        ErrorToast(message = meta.error, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp))
    }
}

private fun Rect.scaledAroundCenter(s: Float): Rect {
    val w = width * s; val h = height * s
    return Rect(Offset(center.x - w / 2, center.y - h / 2), androidx.compose.ui.geometry.Size(w, h))
}

/**
 * The room: the cover itself, decoded tiny and blurred into soft light, filling the screen; the
 * key light from above; and a floor of black so the controls always read. Crossfades between
 * songs so the whole space changes color like a slow lighting cue.
 */
@Composable
fun PlayerAtmosphere(song: Song?, light: ArtworkLight, alpha: () -> Float) {
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha() }) {
        Crossfade(targetState = song?.artworkUrl, animationSpec = tween(900), label = "atmosphere") { url ->
            ArtworkBackdrop(url = url, blur = 36.dp, tiny = true, modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = 0.78f })
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            0f to light.key.copy(alpha = 0.30f),
                            1f to Color.Transparent,
                            center = Offset(size.width * 0.2f, 0f),
                            radius = size.width * 1.2f,
                        ),
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.30f),
                            0.45f to Color.Black.copy(alpha = 0.42f),
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    )
                },
        )
    }
}

@Composable
private fun TopBar(onClose: () -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
        // The grabber says "this is a sheet — pull it down".
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .size(width = 36.dp, height = 5.dp)
                .clip(Radius.pill)
                .background(Color.White.copy(alpha = 0.32f)),
        )
        RoundGlassButton(Icons.Rounded.ExpandMore, "Close", onClose, Modifier.align(Alignment.CenterStart))
        Box(Modifier.align(Alignment.CenterEnd)) {
            RoundGlassButton(Icons.Rounded.MoreHoriz, "More", { menuOpen = true })
            if (menuOpen) MoreMenu(onDismiss = { menuOpen = false })
        }
    }
}

@Composable
private fun RoundGlassButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(40.dp).pressable(onClick = onClick).glass(Radius.pill, Glass.Clear, tint = Color.Black.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitleRow(song: Song, liked: Boolean, onLike: () -> Unit, modifier: Modifier, large: Boolean) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = song,
            transitionSpec = {
                (fadeIn(tween(320, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 3 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 4 })
            },
            contentKey = { it.playId },
            label = "titleChange",
            modifier = Modifier.weight(1f),
        ) { s ->
            Column {
                if (large) MarqueeTitle(s.title) else Text(
                    s.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    s.artist,
                    style = if (large) MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp) else MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        LikeButton(liked = liked, onClick = onLike, size = if (large) 44.dp else 40.dp)
    }
}

/**
 * The big title. A title that fits just sits there; one that doesn't glides sideways, and only
 * then do its edges soften (both sides — text slides out on the left too), so short titles are
 * never faded and long ones never hard-cut.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarqueeTitle(title: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        var textWidth by remember(title) { mutableStateOf(0) }
        val overflows = textWidth > constraints.maxWidth
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { textWidth = it.size.width },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (overflows) {
                        drawRect(
                            Brush.horizontalGradient(0f to Color.Transparent, 0.05f to Color.Black, 0.92f to Color.Black, 1f to Color.Transparent),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    }
                }
                .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_000, spacing = androidx.compose.foundation.MarqueeSpacing(48.dp)),
        )
    }
}

/** Heart that pops when you like, with a ring of light rippling out from it. */
@Composable
private fun LikeButton(liked: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp) {
    val pop = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(liked) {
        if (!initialized) { initialized = true; return@LaunchedEffect }
        if (liked) {
            launch { ring.snapTo(0f); ring.animateTo(1f, tween(520)) }
            pop.snapTo(0.7f)
            pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 420f))
        } else {
            pop.snapTo(0.85f)
            pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f))
        }
    }
    Box(
        modifier = Modifier.size(size).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    val r = ring.value
                    alpha = if (r in 0.001f..0.999f) (1f - r) * 0.8f else 0f
                    scaleX = 0.6f + r * 0.9f; scaleY = 0.6f + r * 0.9f
                }
                .drawBehind { drawCircle(Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())) },
        )
        Icon(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (liked) "Unlike" else "Like",
            tint = if (liked) Color.White else Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(26.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}

/** Playback problems as a small glass note that slides in and leaves on its own — not a Material snackbar. */
@Composable
private fun ErrorToast(message: String?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            shown = friendlyPlaybackError(message)
            PlayerController.consumeError()
            delay(3_600)
            shown = null
        }
    }
    AnimatedVisibility(
        visible = shown != null,
        enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { -it },
        exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it / 2 },
        modifier = modifier.padding(horizontal = 24.dp),
    ) {
        Text(
            shown.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .glass(Radius.pill, Glass.Frosted, tint = Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

/**
 * The less-used actions — sleep timer and song details — behind "•••", in a small glass panel
 * rather than stock Material dialogs.
 */
@Composable
private fun MoreMenu(onDismiss: () -> Unit) {
    val meta by PlayerController.meta.collectAsState()
    val sleepRemaining by SleepTimer.remainingSeconds.collectAsState()
    var page by remember { mutableStateOf("root") }
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(0, 48.dp.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(250.dp)
                .glass(RoundedCornerShape(20.dp), Glass.Regular, tint = Color(0xF2141418))
                .padding(vertical = 8.dp),
        ) {
            when (page) {
                "root" -> {
                    MenuItem(
                        Icons.Rounded.Bedtime,
                        "Sleep timer",
                        sleepRemaining?.let { "Pausing in ${it / 60}:${"%02d".format(it % 60)}" } ?: "Off",
                    ) { page = "sleep" }
                    MenuItem(Icons.Rounded.Info, "Song details", null) { page = "info" }
                }
                "sleep" -> {
                    MenuHeader("Pause playback in")
                    listOf(10, 20, 30, 45, 60).forEach { m -> MenuItem(null, "$m minutes", null) { SleepTimer.start(m); onDismiss() } }
                    if (sleepRemaining != null) MenuItem(null, "Turn off", null) { SleepTimer.cancel(); onDismiss() }
                }
                "info" -> {
                    val s = meta.currentSong
                    MenuHeader("Song details")
                    if (s != null) {
                        // "Single" / "Music" are the model's placeholders for unknown values — not facts worth showing.
                        if (s.album.isNotBlank() && s.album != "Single") InfoLine("Album", s.album)
                        if (s.genre.isNotBlank() && s.genre != "Music") InfoLine("Genre", s.genre)
                        if (s.duration > 0) InfoLine("Length", formatTime(s.duration * 1000L))
                        meta.streamQuality?.let { InfoLine("Streaming", it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, detail: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.55f))
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** Playback errors arrive as raw exception text; say what happened in plain words instead. */
private fun friendlyPlaybackError(raw: String): String {
    // The engine formats these as `… "<title>": <reason>` — and titles can contain quotes
    // themselves ("Channa Mereya (From "Ae Dil…")"), so match up to the closing `": `.
    val song = Regex("\"(.+?)\": ").find(raw)?.groupValues?.get(1)
        ?: Regex("\"(.+)\"").find(raw)?.groupValues?.get(1)
    val about = if (song != null) "\u201C$song\u201D" else "that song"
    val lower = raw.lowercase()
    return when {
        "unable to resolve host" in lower || "unknownhost" in lower || "failed to connect" in lower || "timeout" in lower ->
            "You\u2019re offline \u2014 couldn\u2019t play $about."
        raw.startsWith("Skipped") -> "Skipped $about \u2014 it couldn\u2019t be played."
        else -> "Couldn\u2019t play $about right now."
    }
}
