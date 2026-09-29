package com.daydreamin.app.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.R
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.EqualizerBars
import com.daydreamin.app.ui.components.GlassChip
import com.daydreamin.app.ui.components.GlassIconButton
import com.daydreamin.app.ui.components.SectionTitle
import com.daydreamin.app.ui.components.SolidPillButton
import com.daydreamin.app.ui.components.StaggeredAppear
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.components.shimmer
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.BrandPink
import com.daydreamin.app.ui.theme.BrandPurple
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.Space
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import com.daydreamin.app.ui.theme.glass
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.rememberArtworkLight
import com.daydreamin.app.ui.components.ArtworkBackdrop
import com.daydreamin.app.ui.components.AmbientGlow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.font.FontWeight
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.time.LocalTime
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

private val TopBarHeight = 60.dp
private val QuickPickRowHeight = 66.dp
private val SpotlightHeight = 216.dp
/** Until the lead artwork's own light is known: the brand's violet key with a deep indigo fill. */
private val DefaultLight = ArtworkLight(key = BrandViolet, fill = Color(0xFF3D3A9E))
private const val QUICK_PICK_ROWS = 4

/** What a song row/card needs to know about playback — kept tiny so rows only recompose when it actually changes. */
private data class NowPlaying(val playId: String?, val isPlaying: Boolean)

@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onSearchClick: () -> Unit,
    contentPadding: PaddingValues,
) {
    val vm: HomeViewModel = composeViewModel()
    val state by vm.state.collectAsState()
    val history by vm.history.collectAsState()
    val liked by vm.liked.collectAsState()
    val meta = PlayerController.meta.collectAsState()
    val nowPlaying by remember { derivedStateOf { NowPlaying(meta.value.currentSong?.playId, meta.value.isPlaying) } }

    val discover = state.selectedChip == "All"
    val ready = !state.loading && state.error == null
    val lead = if (ready) state.trending.firstOrNull() else null
    val light = rememberArtworkLight(lead?.cover?.ifBlank { lead.artworkUrl }, fallback = DefaultLight)

    val listState = rememberLazyListState()
    val scrollY = rememberSaveable(saver = FloatStateSaver) { mutableFloatStateOf(0f) }
    val scrollTracker = remember { ScrollTracker(scrollY) }
    val homeHaze = remember { HazeState() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize().background(BgBase)) {
        // Everything the top bar blurs — including the glow — lives in this one source.
        Box(modifier = Modifier.fillMaxSize().hazeSource(homeHaze)) {
            AmbientGlow(light = light, scrollY = { if (listState.canScrollBackward) scrollY.floatValue else 0f })
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(scrollTracker),
                contentPadding = PaddingValues(
                    top = statusTop + TopBarHeight,
                    bottom = contentPadding.calculateBottomPadding() + Space.l,
                ),
            ) {
                item(key = "update-banner") {
                    com.daydreamin.app.ui.components.UpdateBanner(modifier = Modifier.padding(bottom = Space.s))
                }
                item(key = "greeting") { Greeting() }
                item(key = "chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        items(genreChips) { chip ->
                            GlassChip(label = chip, selected = state.selectedChip == chip, onClick = { vm.onChipSelected(chip) })
                        }
                    }
                    Spacer(Modifier.height(Space.l))
                }

                if (discover) {
                    discoverFeed(
                        state = state,
                        lead = lead,
                        light = light,
                        history = history,
                        liked = liked,
                        nowPlaying = { nowPlaying },
                        onRetry = vm::retry,
                        onMood = vm::onMoodSelected,
                    )
                } else {
                    resultsFeed(state = state, nowPlaying = { nowPlaying }, onRetry = vm::retry)
                }
            }
        }

        HomeTopBar(
            haze = homeHaze,
            statusTop = statusTop,
            scrollY = { if (listState.canScrollBackward) scrollY.floatValue else 0f },
            onSearch = onSearchClick,
            onMenu = onOpenDrawer,
        )
    }
}

// ---------------------------------------------------------------- feeds

private fun LazyListScope.discoverFeed(
    state: HomeUiState,
    lead: Song?,
    light: ArtworkLight,
    history: List<Song>,
    liked: List<Song>,
    nowPlaying: () -> NowPlaying,
    onRetry: () -> Unit,
    onMood: (MoodCard) -> Unit,
) {
    when {
        state.loading -> {
            item(key = "spotlight-loading") { SpotlightPlaceholder(Modifier.animateItem()) }
            item(key = "quick-loading") { QuickPicksPlaceholder(Modifier.animateItem()) }
        }
        state.error != null -> item(key = "error") { ErrorPanel(state.error, onRetry, Modifier.animateItem()) }
        else -> {
            if (lead != null) {
                item(key = "spotlight") {
                    val np = nowPlaying()
                    Spotlight(
                        song = lead,
                        light = light,
                        modifier = Modifier.animateItem(),
                        isCurrent = np.playId == lead.playId,
                        isPlaying = np.isPlaying,
                    )
                }
            }
            val picks = state.trending.drop(1).take(QUICK_PICK_ROWS * 5)
            if (picks.isNotEmpty()) {
                item(key = "quick-picks") {
                    Section(title = "Quick picks", subtitle = "Tap a song to start a radio", modifier = Modifier.animateItem()) {
                        QuickPicksGrid(picks, nowPlaying)
                    }
                }
            }
        }
    }
    if (history.size >= 3) {
        item(key = "listen-again") {
            Section(title = "Listen again", modifier = Modifier.animateItem()) {
                CardRow(history.take(15), cardSize = 128.dp, nowPlaying = nowPlaying) { song, _ -> PlayerController.playSong(song) }
            }
        }
    }
    if (liked.isNotEmpty()) {
        item(key = "likes") {
            Section(title = "From your likes", subtitle = "${liked.size} ${if (liked.size == 1) "song" else "songs"}", modifier = Modifier.animateItem()) {
                // A likes shelf plays like a playlist: the rest of your likes follow the one you tapped.
                CardRow(liked, cardSize = 156.dp, nowPlaying = nowPlaying) { song, index ->
                    PlayerController.playSong(song, queueContext = liked.drop(index + 1) + liked.take(index))
                }
            }
        }
    }
    item(key = "moods") {
        Section(title = "Moods & moments", modifier = Modifier.animateItem()) { MoodGrid(onMood) }
    }
}

private fun LazyListScope.resultsFeed(state: HomeUiState, nowPlaying: () -> NowPlaying, onRetry: () -> Unit) {
    item(key = "results-title") {
        val count = if (!state.loading && state.error == null) state.trending.size else null
        val kind = if (state.selectionIsMood) "Mood" else "Genre"
        SectionTitle(
            title = state.selectedChip,
            subtitle = if (count != null) "$kind · $count ${if (count == 1) "song" else "songs"}" else kind,
            modifier = Modifier.animateItem(),
        )
        Spacer(Modifier.height(Space.xs))
    }
    when {
        state.loading -> items(8, key = { "row-loading-$it" }) { QuickPickRowPlaceholder(Modifier.animateItem().padding(horizontal = Space.gutter)) }
        state.error != null -> item(key = "results-error") { ErrorPanel(state.error, onRetry, Modifier.animateItem()) }
        state.trending.isEmpty() -> item(key = "results-empty") {
            Text(
                "Nothing here right now.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(Space.gutter),
            )
        }
        else -> itemsIndexed(state.trending, key = { _, song -> "r-" + song.playId }) { index, song ->
            StaggeredAppear(index, modifier = Modifier.padding(horizontal = Space.gutter)) {
                val np = nowPlaying()
                QuickPickRow(
                    song = song,
                    isCurrent = np.playId == song.playId,
                    isPlaying = np.isPlaying,
                    onClick = { PlayerController.playSong(song) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- chrome

@Composable
private fun Greeting() {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Up late"
        }
    }
    val name by com.daydreamin.app.DaydreaminApp.instance.prefs.userName.collectAsState(initial = "")
    Text(
        buildAnnotatedString {
            append(greeting)
            if (name.isNotBlank()) withStyle(SpanStyle(color = TextSecondary)) { append(", ${name.trim().substringBefore(' ')}") }
        },
        style = MaterialTheme.typography.displaySmall,
        color = TextPrimary,
        modifier = Modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.xs, bottom = Space.m + 2.dp),
    )
}

/**
 * Transparent over the glow at rest; as content scrolls under it, a progressive blur (strongest
 * at the very top, dissolving to nothing at its lower edge) fades in — no hard bar edge.
 */
@Composable
private fun HomeTopBar(haze: HazeState, statusTop: Dp, scrollY: () -> Float, onSearch: () -> Unit, onMenu: () -> Unit) {
    val density = LocalDensity.current
    val fadeDistancePx = with(density) { 72.dp.toPx() }
    // Fully frosted behind the bar's own content; dissolves only across the last stretch below it.
    val solidUntilPx = with(density) { (statusTop + TopBarHeight - 6.dp).toPx() }
    val fadeEndPx = with(density) { (statusTop + TopBarHeight + 16.dp).toPx() }
    val style = remember {
        HazeStyle(
            backgroundColor = BgBase,
            tints = listOf(HazeTint(Color.Black.copy(alpha = 0.58f))),
            blurRadius = 30.dp,
            noiseFactor = 0.04f,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusTop + TopBarHeight + 16.dp)
            .hazeEffect(haze, style) {
                progressive = HazeProgressive.verticalGradient(
                    startY = solidUntilPx,
                    startIntensity = 1f,
                    endY = fadeEndPx,
                    endIntensity = 0f,
                    preferPerformance = true,
                )
                alpha = (scrollY() / fadeDistancePx).coerceIn(0f, 1f)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop)
                .height(TopBarHeight)
                .padding(horizontal = Space.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.splash_logo), contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(Space.xs))
            Text("Daydreamin", style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, letterSpacing = (-0.5).sp), color = TextPrimary)
            Spacer(Modifier.weight(1f))
            GlassIconButton(Icons.Rounded.Search, contentDescription = "Search", onClick = onSearch)
            Spacer(Modifier.width(10.dp))
            Avatar(onClick = onMenu)
        }
    }
}

@Composable
private fun Avatar(onClick: () -> Unit) {
    // The avatar doubles as the menu button — announced as such.
    com.daydreamin.app.ui.components.UserAvatar(
        size = 40.dp,
        modifier = Modifier
            .semantics(mergeDescendants = true) { contentDescription = "Menu" }
            .pressable(onClick = onClick),
    )
}

// ---------------------------------------------------------------- sections

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(modifier = modifier.padding(top = Space.section)) {
        SectionTitle(title = title, subtitle = subtitle)
        Spacer(Modifier.height(Space.titleToContent))
        content()
    }
}

/**
 * The one featured item on Home: today's #1, lit by its own artwork. The card's surface *is* the
 * cover — decoded tiny and blurred into light — under a scrim that keeps the text side quiet,
 * then a skin of glass (edge light + sheen) on top. The cover itself floats on the right.
 */
@Composable
private fun Spotlight(song: Song, light: ArtworkLight, isCurrent: Boolean, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val onPlay = { if (isCurrent) PlayerController.togglePlayPause() else PlayerController.playSong(song) }
    Box(
        modifier = modifier
            .padding(horizontal = Space.gutter)
            .fillMaxWidth()
            .height(SpotlightHeight)
            .pressable(onClick = onPlay)
            .shadow(elevation = 30.dp, shape = Radius.panelShape, ambientColor = light.key, spotColor = light.key)
            .clip(Radius.panelShape)
            .background(Color.Black),
    ) {
        ArtworkBackdrop(url = song.artworkUrl, modifier = Modifier.matchParentSize(), blur = 34.dp)
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.66f), 0.55f to Color.Black.copy(alpha = 0.40f), 1f to Color.Black.copy(alpha = 0.18f)))
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.30f))),
        )
        Box(Modifier.matchParentSize().glass(Radius.panelShape, Glass.Clear))
        Row(modifier = Modifier.matchParentSize().padding(Space.gutter)) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text("NO. 1 TODAY", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f))
                Column {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.6).sp),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.74f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val playingThis = isCurrent && isPlaying
                AnimatedContent(
                    targetState = playingThis,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) },
                    label = "spotlightPlay",
                ) { playing ->
                    SolidPillButton(
                        label = if (playing) "Pause" else "Play",
                        icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        onClick = onPlay,
                    )
                }
            }
            Spacer(Modifier.width(Space.m))
            Artwork(
                url = song.artworkUrl,
                shape = Radius.cardShape,
                modifier = Modifier
                    .size(SpotlightHeight - Space.gutter * 2)
                    .shadow(elevation = 22.dp, shape = Radius.cardShape, ambientColor = Color.Black, spotColor = Color.Black),
            )
        }
    }
}

/** YouTube Music's signature shelf: songs in columns of four, paging sideways one column at a time. */
@Composable
private fun QuickPicksGrid(songs: List<Song>, nowPlaying: () -> NowPlaying) {
    val gridState = rememberLazyGridState()
    // The next column peeks in from the right so it's obvious the shelf scrolls.
    val columnWidth = LocalConfiguration.current.screenWidthDp.dp - Space.gutter * 2 - 28.dp
    LazyHorizontalGrid(
        rows = GridCells.Fixed(QUICK_PICK_ROWS),
        state = gridState,
        flingBehavior = rememberSnapFlingBehavior(gridState, SnapPosition.Start),
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        modifier = Modifier.fillMaxWidth().height(QuickPickRowHeight * QUICK_PICK_ROWS),
    ) {
        items(songs.size, key = { songs[it].playId }) { index ->
            val song = songs[index]
            val np = nowPlaying()
            QuickPickRow(
                song = song,
                isCurrent = np.playId == song.playId,
                isPlaying = np.isPlaying,
                onClick = { PlayerController.playSong(song) },
                modifier = Modifier.width(columnWidth),
            )
        }
    }
}

@Composable
private fun QuickPickRow(song: Song, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Long-press for the song menu (play next, add to queue, like, playlists) — same as everywhere else.
    var menuOpen by remember { mutableStateOf(false) }
    val openPlayer = com.daydreamin.app.ui.components.LocalOpenPlayer.current
    Row(
        modifier = modifier.fillMaxWidth().height(QuickPickRowHeight).pressable(onLongClick = { menuOpen = true }, onClick = { if (isCurrent) openPlayer() else onClick() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (menuOpen) com.daydreamin.app.ui.components.SongMenu(song = song, onDismiss = { menuOpen = false })
        Artwork(url = song.cover.ifBlank { song.artworkUrl }, shape = Radius.thumbShape, modifier = Modifier.size(52.dp))
        Column(modifier = Modifier.weight(1f).padding(horizontal = Space.s + 2.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Medium),
                color = if (isCurrent) MaterialTheme.colorScheme.primary else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isCurrent) EqualizerBars(playing = isPlaying, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CardRow(songs: List<Song>, cardSize: Dp, nowPlaying: () -> NowPlaying, onClick: (Song, Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.s + 2.dp),
    ) {
        itemsIndexed(songs, key = { _, song -> song.playId }) { index, song ->
            val np = nowPlaying()
            ArtCard(song, cardSize, isCurrent = np.playId == song.playId, isPlaying = np.isPlaying, onClick = { onClick(song, index) })
        }
    }
}

@Composable
private fun ArtCard(song: Song, size: Dp, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val openPlayer = com.daydreamin.app.ui.components.LocalOpenPlayer.current
    Column(modifier = Modifier.width(size).pressable(onLongClick = { menuOpen = true }, onClick = { if (isCurrent) openPlayer() else onClick() })) {
        if (menuOpen) com.daydreamin.app.ui.components.SongMenu(song = song, onDismiss = { menuOpen = false })
        Box {
            Artwork(url = song.artworkUrl, shape = Radius.cardShape, modifier = Modifier.size(size))
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Space.xs)
                        .glass(Radius.pill, Glass.Frosted, tint = Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = Space.xs, vertical = 6.dp),
                ) { EqualizerBars(playing = isPlaying, color = Color.White) }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            song.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (isCurrent) MaterialTheme.colorScheme.primary else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MoodGrid(onMood: (MoodCard) -> Unit) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.s - 2.dp),
        verticalArrangement = Arrangement.spacedBy(Space.s - 2.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp * 2 + (Space.s - 2.dp)),
    ) {
        items(moodCards.size, key = { moodCards[it].title }) { index ->
            val mood = moodCards[index]
            Row(
                modifier = Modifier
                    .width(164.dp)
                    .height(56.dp)
                    .pressable(onClick = { onMood(mood) })
                    .glass(Radius.cardShape, Glass.Clear)
                    // The mood's color bleeds faintly off its light strip — each tile has an identity without being painted.
                    .background(Brush.horizontalGradient(listOf(Color(mood.accent).copy(alpha = 0.13f), Color.Transparent)))
                    .padding(start = Space.s + 2.dp, end = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 4.dp, height = 22.dp).clip(Radius.pill).background(Color(mood.accent)))
                Spacer(Modifier.width(Space.s))
                Text(mood.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = Space.gutter)
            .fillMaxWidth()
            .glass(Radius.panelShape, Glass.Clear)
            .padding(Space.l),
        horizontalAlignment = Alignment.Start,
    ) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(Space.s))
        Text("Couldn't load your feed", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(Space.xxs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(Space.m))
        SolidPillButton(label = "Try again", icon = Icons.Rounded.Refresh, onClick = onRetry)
    }
}

// ---------------------------------------------------------------- loading placeholders (same geometry as the real thing, so nothing jumps)

@Composable
private fun SpotlightPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(horizontal = Space.gutter)
            .fillMaxWidth()
            .height(SpotlightHeight)
            .clip(Radius.panelShape)
            .shimmer(),
    )
}

@Composable
private fun QuickPicksPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier.padding(top = Space.section)) {
        Box(Modifier.padding(horizontal = Space.gutter).size(width = 140.dp, height = 22.dp).clip(Radius.thumbShape).shimmer())
        Spacer(Modifier.height(Space.titleToContent))
        repeat(QUICK_PICK_ROWS) { QuickPickRowPlaceholder(Modifier.padding(horizontal = Space.gutter)) }
    }
}

@Composable
private fun QuickPickRowPlaceholder(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().height(QuickPickRowHeight), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(Radius.thumbShape).shimmer())
        Column(Modifier.padding(horizontal = Space.s)) {
            Box(Modifier.size(width = 170.dp, height = 13.dp).clip(Radius.pill).shimmer())
            Spacer(Modifier.height(7.dp))
            Box(Modifier.size(width = 110.dp, height = 11.dp).clip(Radius.pill).shimmer())
        }
    }
}

// ---------------------------------------------------------------- scroll plumbing

/** Total distance scrolled, for draw-phase effects (glow parallax, top-bar blur) that need more than the first item's offset. */
private class ScrollTracker(private val y: MutableFloatState) : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        y.floatValue = (y.floatValue - consumed.y).coerceAtLeast(0f)
        return Offset.Zero
    }
}

private val FloatStateSaver = androidx.compose.runtime.saveable.Saver<MutableFloatState, Float>(
    save = { it.floatValue },
    restore = { mutableFloatStateOf(it) },
)
