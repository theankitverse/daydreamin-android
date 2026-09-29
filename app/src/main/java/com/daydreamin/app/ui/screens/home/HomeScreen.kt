package com.daydreamin.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.R
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.prefs.Playlist
import com.daydreamin.app.data.recommend.FeedShelf
import com.daydreamin.app.data.recommend.HomeFeed
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.AmbientGlow
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.ArtworkBackdrop
import com.daydreamin.app.ui.components.EqualizerBars
import com.daydreamin.app.ui.components.GlassIconButton
import com.daydreamin.app.ui.components.LikedTile
import com.daydreamin.app.ui.components.Mosaic
import com.daydreamin.app.ui.components.NameDialog
import com.daydreamin.app.ui.components.PlaylistCover
import com.daydreamin.app.ui.components.SectionTitle
import com.daydreamin.app.ui.components.SolidPillButton
import com.daydreamin.app.ui.components.SongActions
import com.daydreamin.app.ui.components.SongMenu
import com.daydreamin.app.ui.components.StaggeredAppear
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.components.shimmer
import com.daydreamin.app.ui.screens.library.LibraryTab
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.Space
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import com.daydreamin.app.ui.theme.glass
import com.daydreamin.app.ui.theme.rememberArtworkLight
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

private val TopBarHeight = 60.dp
private val QuickPickRowHeight = 66.dp
private val HeroHeight = 212.dp
private val TileSize = 132.dp
/** Until the lead artwork's own light is known: the brand's violet key with a deep indigo fill. */
private val DefaultLight = ArtworkLight(key = BrandViolet, fill = Color(0xFF3D3A9E))
private const val QUICK_PICK_ROWS = 4

/** What a song row/card needs to know about playback — kept tiny so rows only recompose when it actually changes. */
private data class NowPlaying(val playId: String?, val isPlaying: Boolean)

/**
 * Home, built around you: your mix up top (made from what you play, like and skip — see
 * [com.daydreamin.app.data.recommend.HomeFeedRepository]), then what you've been playing, your
 * own collections, and shelves of more like what you love. Before there's any listening to go on,
 * it leads with the chart instead, and says so.
 */
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onSearchClick: () -> Unit,
    onOpenLibrary: (tab: LibraryTab?, playlistId: String?) -> Unit,
    contentPadding: PaddingValues,
) {
    val vm: HomeViewModel = composeViewModel()
    val feed by vm.feed.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val hasTaste by vm.hasTaste.collectAsState()
    val feedFailed by vm.feedFailed.collectAsState()
    val browse by vm.browse.collectAsState()
    val history by vm.history.collectAsState()
    val liked by vm.liked.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val meta = PlayerController.meta.collectAsState()
    val nowPlaying by remember { derivedStateOf { NowPlaying(meta.value.currentSong?.playId, meta.value.isPlaying) } }
    BackHandler(enabled = browse != null) { vm.closeMood() }

    val lead = if (browse != null) browse?.songs?.firstOrNull() else feed?.heroMix?.firstOrNull() ?: history.firstOrNull()
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
                val mood = browse
                if (mood != null) {
                    moodResults(mood, nowPlaying = { nowPlaying }, onBack = vm::closeMood, onRetry = vm::retryMood)
                } else {
                    item(key = "greeting") { Greeting() }
                    if (feed?.isPopular == true || (feed == null && hasTaste == false)) {
                        item(key = "taste-hint") { TasteHint(Modifier.animateItem()) }
                    }
                    forYou(
                        feed = feed,
                        refreshing = refreshing,
                        failed = feedFailed,
                        light = light,
                        nowPlaying = { nowPlaying },
                        onRefresh = vm::refreshMix,
                    )
                    if (history.isNotEmpty()) {
                        item(key = "recent") {
                            Section(title = "Recently played", modifier = Modifier.animateItem()) {
                                CardRow(history.take(20), cardSize = TileSize, nowPlaying = { nowPlaying }) { song, _ -> PlayerController.playSong(song) }
                            }
                        }
                    }
                    if (liked.isNotEmpty() || playlists.isNotEmpty()) {
                        item(key = "library") {
                            Section(title = "Your library", modifier = Modifier.animateItem()) {
                                LibraryShelf(
                                    liked = liked,
                                    playlists = playlists,
                                    onOpenLiked = { onOpenLibrary(LibraryTab.LIKED, null) },
                                    onOpenPlaylist = { onOpenLibrary(null, it.id) },
                                )
                            }
                        }
                    }
                    feed?.shelves?.forEach { shelf -> feedShelf(shelf, nowPlaying = { nowPlaying }) }
                    item(key = "moods") {
                        Section(title = "Moods & moments", modifier = Modifier.animateItem()) { MoodGrid(vm::openMood) }
                    }
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

/** The mix and the list under it — yours, or what's popular where you are — or placeholders while the first one is built. */
private fun LazyListScope.forYou(
    feed: HomeFeed?,
    refreshing: Boolean,
    failed: Boolean,
    light: ArtworkLight,
    nowPlaying: () -> NowPlaying,
    onRefresh: () -> Unit,
) {
    when {
        feed != null && feed.topPicks.isNotEmpty() -> {
            item(key = "mix") {
                val np = nowPlaying()
                MixHero(feed, light, refreshing = refreshing, nowPlaying = np, onRefresh = onRefresh, modifier = Modifier.animateItem())
            }
            item(key = "top-picks") {
                Section(
                    title = if (feed.isPopular) "Top songs in ${regionName(feed.region)}" else "Top picks for you",
                    subtitle = when {
                        feed.isPopular -> "Most played right now"
                        feed.toppedUp -> "From what you've played so far, plus what's popular"
                        else -> "Picked from what you play and like"
                    },
                    modifier = Modifier.animateItem(),
                ) {
                    QuickPicksGrid(feed.topPicks.take(QUICK_PICK_ROWS * 5), nowPlaying) { index, _ ->
                        PlayerController.playFromList(feed.topPicks, index)
                    }
                }
            }
        }
        failed && !refreshing -> item(key = "mix-error") {
            ErrorPanel(
                title = "Couldn't load Home",
                message = "You may be offline. Your library and recent plays are below.",
                onRetry = onRefresh,
                modifier = Modifier.animateItem(),
            )
        }
        else -> {
            item(key = "mix-loading") { HeroPlaceholder(Modifier.animateItem()) }
            item(key = "picks-loading") { QuickPicksPlaceholder(Modifier.animateItem()) }
        }
    }
}

private fun regionName(region: String?): String = region?.let { com.daydreamin.app.data.recommend.PopularFeed.regionName(it) } ?: "your country"

/** A row of recommendations; tapping a song plays the rest of the row after it. */
private fun LazyListScope.feedShelf(shelf: FeedShelf, nowPlaying: () -> NowPlaying) {
    item(key = "shelf-" + shelf.key) {
        Section(title = shelf.title, subtitle = shelf.subtitle, modifier = Modifier.animateItem()) {
            CardRow(shelf.songs, cardSize = TileSize, nowPlaying = nowPlaying) { _, index ->
                PlayerController.playFromList(shelf.songs, index)
            }
        }
    }
}

private fun LazyListScope.moodResults(browse: MoodBrowse, nowPlaying: () -> NowPlaying, onBack: () -> Unit, onRetry: () -> Unit) {
    item(key = "mood-back") {
        Row(
            Modifier.animateItem().padding(start = 10.dp, top = Space.xs).pressable(onClick = onBack).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = Color.White.copy(alpha = 0.75f))
            Text("Home", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
        }
    }
    item(key = "mood-title") {
        val count = if (!browse.loading && browse.error == null) browse.songs.size else null
        SectionTitle(
            title = browse.mood.title,
            subtitle = if (count != null) "Mood · $count ${if (count == 1) "song" else "songs"}" else "Mood",
            modifier = Modifier.animateItem().padding(top = Space.xs),
        )
        Spacer(Modifier.height(Space.xs))
    }
    when {
        browse.loading -> items(8, key = { "row-loading-$it" }) { QuickPickRowPlaceholder(Modifier.animateItem().padding(horizontal = Space.gutter)) }
        browse.error != null -> item(key = "mood-error") { ErrorPanel("Couldn't load this mood", browse.error, onRetry, Modifier.animateItem()) }
        browse.songs.isEmpty() -> item(key = "mood-empty") {
            Text("Nothing here right now.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.gutter))
        }
        else -> itemsIndexed(browse.songs, key = { _, song -> "r-" + song.playId }) { index, song ->
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
        modifier = Modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.xs, bottom = Space.l),
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
                inputScale = dev.chrisbanes.haze.HazeInputScale.Auto
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
 * Your mix: the featured card on Home. Its surface is the top pick's cover, blurred into light,
 * under a scrim that keeps the text calm; a mosaic of the mix sits top-right. Play / shuffle /
 * save along the bottom, and a fresh mix on demand.
 */
@Composable
private fun MixHero(feed: HomeFeed, light: ArtworkLight, refreshing: Boolean, nowPlaying: NowPlaying, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val picks = feed.heroMix
    val playingFromMix = picks.any { it.playId == nowPlaying.playId }
    val library by com.daydreamin.app.DaydreaminApp.instance.prefs.playlists.collectAsState(initial = emptyList())
    val mixUrl = "daydreamin:mix:${feed.generatedAtMs}"
    val saved = library.any { it.sourceUrl == mixUrl }
    val onPlay = {
        if (playingFromMix) PlayerController.togglePlayPause() else PlayerController.playFromList(picks, 0)
    }
    Box(
        modifier = modifier
            .padding(horizontal = Space.gutter)
            .fillMaxWidth()
            .height(HeroHeight)
            .shadow(elevation = 30.dp, shape = Radius.panelShape, ambientColor = light.key, spotColor = light.key)
            .clip(Radius.panelShape)
            .background(Color.Black),
    ) {
        ArtworkBackdrop(url = picks.first().artworkUrl, modifier = Modifier.matchParentSize(), blur = 34.dp)
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.66f), 0.6f to Color.Black.copy(alpha = 0.42f), 1f to Color.Black.copy(alpha = 0.22f)))
                .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.38f))),
        )
        Box(Modifier.matchParentSize().glass(Radius.panelShape, Glass.Clear))
        Column(Modifier.matchParentSize().padding(Space.gutter)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(if (feed.isPopular) "POPULAR RIGHT NOW" else "MADE FOR YOU", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f))
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        if (feed.isPopular) "Hits in ${regionName(feed.region)}" else "Your Daydream Mix",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.6).sp),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (feed.isPopular) "Most played and trending there right now · ${picks.size} songs" else basedOnLine(feed.basedOn, picks.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.74f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Space.m))
                Mosaic(
                    picks.distinctBy { it.artworkUrl }.take(4),
                    size = 104.dp,
                    modifier = Modifier.shadow(elevation = 18.dp, shape = Radius.cardShape, ambientColor = Color.Black, spotColor = Color.Black),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val playing = playingFromMix && nowPlaying.isPlaying
                AnimatedContent(
                    targetState = playing,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) },
                    label = "mixPlay",
                ) { p ->
                    SolidPillButton(label = if (p) "Pause" else "Play", icon = if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, onClick = onPlay)
                }
                Spacer(Modifier.width(Space.xs))
                HeroButton(Icons.Rounded.Shuffle, "Shuffle play") { PlayerController.playFromList(picks.shuffled(), 0) }
                Spacer(Modifier.width(Space.xs))
                HeroButton(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (saved) "Saved to your library" else "Save to your library") {
                    if (saved) com.daydreamin.app.ui.components.Toaster.show("Already in your library")
                    else SongActions.savePlaylist(
                        name = (if (feed.isPopular) "Hits in ${regionName(feed.region)} · " else "Daydream Mix · ") + SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(feed.generatedAtMs)),
                        songs = picks,
                        sourceUrl = mixUrl,
                        author = if (feed.isPopular) "Charts" else "Made for you",
                    )
                }
                Spacer(Modifier.weight(1f))
                RefreshButton(refreshing, onRefresh)
            }
        }
    }
}

private fun basedOnLine(artists: List<String>, count: Int): String = when (artists.size) {
    0 -> "$count songs picked for you"
    1 -> "Based on ${artists[0]} · $count songs"
    2 -> "Based on ${artists[0]} and ${artists[1]} · $count songs"
    else -> "Based on ${artists[0]}, ${artists[1]} and more · $count songs"
}

@Composable
private fun HeroButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).pressable(onClick = onClick).glass(Radius.pill, Glass.Regular, tint = Color.Black.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

/** "New mix": spins while one is being built. */
@Composable
private fun RefreshButton(refreshing: Boolean, onRefresh: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "refreshSpin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "refreshAngle")
    Box(
        Modifier.size(40.dp).pressable(onClick = { if (!refreshing) onRefresh() }).glass(Radius.pill, Glass.Regular, tint = Color.Black.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = if (refreshing) "Building a new mix" else "New mix",
            tint = Color.White,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (refreshing) angle else 0f },
        )
    }
}

/** Shown to someone with nothing played yet: what this page will turn into. */
@Composable
private fun TasteHint(modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = Space.gutter)
            .padding(bottom = Space.l)
            .fillMaxWidth()
            .glass(Radius.panelShape, Glass.Clear)
            .padding(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).glass(Radius.pill, Glass.Regular), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = Space.s)) {
            Text("Your mix starts here", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                "Play or like a few songs you love — Home will start building mixes and picks around them.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

/** YouTube Music's signature shelf: songs in columns of four, paging sideways one column at a time. */
@Composable
private fun QuickPicksGrid(songs: List<Song>, nowPlaying: () -> NowPlaying, onPlay: (Int, Song) -> Unit) {
    val gridState = rememberLazyGridState()
    // The next column peeks in from the right so it's obvious the shelf scrolls.
    val columnWidth = LocalConfiguration.current.screenWidthDp.dp - Space.gutter * 2 - 28.dp
    val rows = songs.size.coerceIn(1, QUICK_PICK_ROWS)
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        state = gridState,
        flingBehavior = rememberSnapFlingBehavior(gridState, SnapPosition.Start),
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        modifier = Modifier.fillMaxWidth().height(QuickPickRowHeight * rows),
    ) {
        items(songs.size, key = { songs[it].playId }) { index ->
            val song = songs[index]
            val np = nowPlaying()
            QuickPickRow(
                song = song,
                isCurrent = np.playId == song.playId,
                isPlaying = np.isPlaying,
                onClick = { onPlay(index, song) },
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
        if (menuOpen) SongMenu(song = song, onDismiss = { menuOpen = false })
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
        if (menuOpen) SongMenu(song = song, onDismiss = { menuOpen = false })
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

/** Liked songs first, then your playlists, then a tile to start a new one. */
@Composable
private fun LibraryShelf(liked: List<Song>, playlists: List<Playlist>, onOpenLiked: () -> Unit, onOpenPlaylist: (Playlist) -> Unit) {
    var naming by remember { mutableStateOf(false) }
    if (naming) NameDialog(title = "New playlist", confirm = "Create", onDismiss = { naming = false }, onConfirm = { SongActions.createPlaylist(it); naming = false })
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.s + 2.dp),
    ) {
        item(key = "liked") {
            CollectionTile("Liked songs", songCount(liked.size), onClick = onOpenLiked) { LikedTile(TileSize) }
        }
        items(playlists, key = { "pl-" + it.id }) { p ->
            CollectionTile(p.name, songCount(p.songs.size), onClick = { onOpenPlaylist(p) }) { PlaylistCover(p, TileSize) }
        }
        item(key = "new") {
            CollectionTile("New playlist", "Start one", onClick = { naming = true }) {
                Box(Modifier.size(TileSize).glass(Radius.cardShape, Glass.Regular), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun CollectionTile(title: String, subtitle: String, onClick: () -> Unit, cover: @Composable () -> Unit) {
    Column(Modifier.width(TileSize).pressable(onClick = onClick)) {
        cover()
        Spacer(Modifier.height(Space.xs))
        Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
    }
}

private fun songCount(n: Int) = "$n ${if (n == 1) "song" else "songs"}"

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
private fun ErrorPanel(title: String, message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
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
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(Space.xxs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(Space.m))
        SolidPillButton(label = "Try again", icon = Icons.Rounded.Refresh, onClick = onRetry)
    }
}

// ---------------------------------------------------------------- loading placeholders (same geometry as the real thing, so nothing jumps)

@Composable
private fun HeroPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(horizontal = Space.gutter)
            .fillMaxWidth()
            .height(HeroHeight)
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
