package com.daydreamin.app.ui.screens.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.youtube.YtPlaylist
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.AmbientGlow
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.EqualizerBars
import com.daydreamin.app.ui.components.GlassChip
import com.daydreamin.app.ui.components.SolidPillButton
import com.daydreamin.app.ui.components.SongActions
import com.daydreamin.app.ui.components.SongListRow
import com.daydreamin.app.ui.components.SongMenuButton
import com.daydreamin.app.ui.components.Toaster
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.components.shimmer
import com.daydreamin.app.ui.screens.home.moodCards
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.Space
import com.daydreamin.app.ui.theme.glass
import com.daydreamin.app.ui.theme.rememberArtworkLight
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

private val DefaultLight = ArtworkLight(key = BrandViolet, fill = Color(0xFF3D3A9E))

/** Set by entry points that mean "I want to type" (Home's search button) — the field takes focus once. */
object SearchFocus {
    var requested by mutableStateOf(false)
}

private enum class Phase { IDLE, LOADING, ERROR, EMPTY, RESULTS }
private data class NowPlaying(val playId: String?, val isPlaying: Boolean)

/**
 * Search. A glass field at the top; before you type, moods to start from; while searching, the
 * shape of results shimmering in place; then a lit top result, the songs, and filters for the
 * artists and playlists the search found. The page is lit by the top result's artwork.
 */
@Composable
fun SearchScreen(contentPadding: PaddingValues, onOpenPlayer: () -> Unit) {
    val vm: SearchViewModel = composeViewModel()
    val state by vm.state.collectAsState()
    val meta = PlayerController.meta.collectAsState()
    val nowPlaying by remember { derivedStateOf { NowPlaying(meta.value.currentSong?.playId, meta.value.isPlaying) } }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val phase = when {
        state.query.isBlank() -> Phase.IDLE
        state.error != null && !state.loading -> Phase.ERROR
        // Keep showing the previous results while a refined query loads — less flicker while typing.
        state.songs.isNotEmpty() -> Phase.RESULTS
        state.loading || state.resultsFor != state.query -> Phase.LOADING
        else -> Phase.EMPTY
    }
    val top = state.songs.firstOrNull()?.takeIf { phase == Phase.RESULTS }
    val light = rememberArtworkLight(top?.cover?.ifBlank { top.artworkUrl }, DefaultLight)

    val play = { song: Song ->
        // The song that's already playing opens the player — tapping it never restarts it.
        if (nowPlaying.playId == song.playId) onOpenPlayer() else PlayerController.playSong(song)
    }
    // Scrolling results is reading, not typing: put the keyboard away.
    val hideKeyboardOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) { keyboard?.hide(); focusManager.clearFocus() }
                return Offset.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        AmbientGlow(light = light, scrollY = { 0f })
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // The big title steps aside once you're searching, giving the results the room.
            AnimatedVisibility(
                visible = phase == Phase.IDLE,
                enter = fadeIn(tween(220)) + expandVertically(spring(dampingRatio = 0.9f, stiffness = 400f)),
                exit = fadeOut(tween(120)) + shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 400f)),
            ) {
                Text(
                    "Search",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier.padding(start = Space.gutter, top = Space.m, bottom = Space.s),
                )
            }
            SearchField(
                query = state.query,
                onQueryChange = vm::onQueryChange,
                onSubmit = { keyboard?.hide(); focusManager.clearFocus() },
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
            )
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 24 }) togetherWith
                        fadeOut(tween(120))
                },
                label = "searchPhase",
                modifier = Modifier.fillMaxSize(),
            ) { p ->
                val bottom = contentPadding.calculateBottomPadding() + Space.l
                when (p) {
                    Phase.IDLE -> IdleMoods(bottom, onMood = { vm.onQueryChange(it); keyboard?.hide(); focusManager.clearFocus() }, scroll = hideKeyboardOnScroll)
                    Phase.LOADING -> ResultsSkeleton()
                    Phase.ERROR -> Message(
                        icon = Icons.Rounded.CloudOff,
                        title = "Couldn’t search",
                        body = state.error.orEmpty(),
                        action = "Try again",
                        onAction = vm::retry,
                    )
                    Phase.EMPTY -> Message(
                        icon = Icons.Rounded.SearchOff,
                        title = "No matches for “${state.query.trim()}”",
                        body = "Check the spelling, or try a line you remember from the lyrics.",
                    )
                    Phase.RESULTS -> Results(
                        state = state,
                        vm = vm,
                        light = light,
                        nowPlaying = { nowPlaying },
                        play = play,
                        bottom = bottom,
                        scroll = hideKeyboardOnScroll,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- field

/** Glass field; focus brightens its edge and fill a step so you can see where your typing goes. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(SearchFocus.requested) {
        if (SearchFocus.requested) { SearchFocus.requested = false; focus.requestFocus() }
    }
    val edge by animateColorAsState(if (focused) Color.White.copy(alpha = 0.34f) else Color.Transparent, tween(200), label = "fieldEdge")
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "fieldLift")
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        interactionSource = interaction,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontSize = 16.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .glass(Radius.pill, if (focused) Glass.Frosted else Glass.Regular, tint = Color.White.copy(alpha = 0.02f * lift))
                    .border(1.dp, edge, Radius.pill)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f + 0.3f * lift), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Songs, artists, or a line from the lyrics", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.42f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f)) {
                    Box(Modifier.size(40.dp).pressable(onClick = { onQueryChange(""); focus.requestFocus() }), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------- idle

/** Before you type: the same moods as Home, as quick starting points (each runs a real search). */
@Composable
private fun IdleMoods(bottom: androidx.compose.ui.unit.Dp, onMood: (String) -> Unit, scroll: NestedScrollConnection) {
    LazyColumn(Modifier.fillMaxSize().nestedScroll(scroll), contentPadding = PaddingValues(top = Space.l, bottom = bottom)) {
        item {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                Text("Start with a mood", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Or search for a song, an artist, even a lyric you half remember.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
            }
            Spacer(Modifier.height(Space.titleToContent))
        }
        items(moodCards.chunked(2)) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { mood ->
                    Row(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .pressable(onClick = { onMood(mood.query) })
                            .glass(Radius.cardShape, Glass.Clear)
                            .background(Brush.horizontalGradient(listOf(Color(mood.accent).copy(alpha = 0.16f), Color.Transparent)))
                            .padding(start = 14.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(width = 4.dp, height = 24.dp).clip(Radius.pill).background(Color(mood.accent)))
                        Spacer(Modifier.width(12.dp))
                        Text(mood.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- results

@Composable
private fun Results(
    state: SearchUiState,
    vm: SearchViewModel,
    light: ArtworkLight,
    nowPlaying: () -> NowPlaying,
    play: (Song) -> Unit,
    bottom: androidx.compose.ui.unit.Dp,
    scroll: NestedScrollConnection,
) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        ) {
            items(SearchTab.entries) { t -> GlassChip(label = t.label, selected = state.tab == t, onClick = { vm.onTabChange(t) }) }
        }
        // A hairline of activity while a refined query loads over the previous results.
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            androidx.compose.animation.AnimatedVisibility(visible = state.loading, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.padding(horizontal = Space.gutter).fillMaxWidth().height(2.dp).clip(Radius.pill).shimmer())
            }
        }
        AnimatedContent(
            targetState = state.tab,
            transitionSpec = { fadeIn(tween(200, delayMillis = 40)) togetherWith fadeOut(tween(100)) },
            label = "searchTab",
            modifier = Modifier.fillMaxSize(),
        ) { tab ->
            val listState = rememberLazyListState()
            // New results for a new query start at the top.
            LaunchedEffect(state.resultsFor) { listState.scrollToItem(0) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(scroll),
                contentPadding = PaddingValues(top = Space.s, bottom = bottom),
            ) {
                when (tab) {
                    SearchTab.SONGS -> {
                        // Can be empty for a moment: this list stays composed while it animates out
                        // after a new search comes back with nothing.
                        val top = state.songs.firstOrNull() ?: return@LazyColumn
                        item(key = "top-" + top.playId) {
                            val np = nowPlaying()
                            TopResult(top, light, isCurrent = np.playId == top.playId, isPlaying = np.isPlaying, onPlay = { play(top) })
                        }
                        if (state.songs.size > 1) {
                            item(key = "songs-title") {
                                Text("Songs", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(start = Space.gutter, top = Space.l, bottom = Space.xs))
                            }
                            itemsIndexed(state.songs.drop(1), key = { _, s -> "s-" + s.playId }) { _, s ->
                                val np = nowPlaying()
                                SongListRow(s, isCurrent = np.playId == s.playId, isPlaying = np.isPlaying, onClick = { play(s) }, modifier = Modifier.animateItem())
                            }
                        }
                    }
                    SearchTab.ARTISTS -> {
                        val artists = vm.artists(state.songs)
                        items(artists, key = { "a-" + it.name.lowercase() }) { a ->
                            ArtistRow(a, onClick = { vm.onQueryChange(a.name); vm.onTabChange(SearchTab.SONGS) })
                        }
                    }
                    SearchTab.PLAYLISTS -> playlistItems(state, vm)
                }
            }
        }
    }
}

/**
 * The best match, set apart: lit by its own artwork (a wash of its key light through glass), a
 * larger cover, and a direct Play — or Pause, if it's the song already playing.
 */
@Composable
private fun TopResult(song: Song, light: ArtworkLight, isCurrent: Boolean, isPlaying: Boolean, onPlay: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = Space.gutter)
            .fillMaxWidth()
            .pressable(onClick = onPlay)
            .clip(Radius.panelShape)
            .background(Brush.linearGradient(listOf(light.key.copy(alpha = 0.24f), light.fill.copy(alpha = 0.08f))))
            .glass(Radius.panelShape, Glass.Clear)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = song.artworkUrl, shape = Radius.cardShape, modifier = Modifier.size(96.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text("TOP RESULT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(4.dp))
            Text(song.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.66f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val playingThis = isCurrent && isPlaying
                SolidPillButton(
                    label = if (isCurrent) (if (playingThis) "Playing" else "Paused") else "Play",
                    icon = if (playingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = { if (isCurrent) PlayerController.togglePlayPause() else onPlay() },
                )
                if (isCurrent) {
                    Spacer(Modifier.width(10.dp))
                    EqualizerBars(playing = isPlaying, color = Color.White)
                }
            }
        }
        SongMenuButton(song = song, modifier = Modifier.align(Alignment.Top))
    }
}

@Composable
private fun ArtistRow(a: ArtistHit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).pressable(onClick = onClick).padding(horizontal = Space.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = a.sample.artworkUrl, shape = CircleShape, modifier = Modifier.size(56.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(a.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${a.songCount} ${if (a.songCount == 1) "song" else "songs"} in these results",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = "Search ${a.name}", tint = Color.White.copy(alpha = 0.35f))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.playlistItems(state: SearchUiState, vm: SearchViewModel) {
    when {
        state.playlistsLoading -> items(6) { SkeletonRow() }
        state.playlistsError != null -> item {
            Message(icon = Icons.Rounded.CloudOff, title = "Couldn’t load playlists", body = state.playlistsError, action = "Try again", onAction = vm::retryPlaylists, fill = false)
        }
        state.playlists.isEmpty() -> item {
            Message(icon = Icons.Rounded.SearchOff, title = "No playlists for this one", body = "Songs and artists above may still have what you're after.", fill = false)
        }
        else -> items(state.playlists, key = { "p-" + it.url }) { p -> PlaylistRow(p) }
    }
}

/** A YouTube playlist: tapping loads its tracks and plays them in order; the bookmark keeps a copy in your library. */
@Composable
private fun PlaylistRow(p: YtPlaylist) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val library by DaydreaminApp.instance.prefs.playlists.collectAsState(initial = emptyList())
    val saved = library.any { it.sourceUrl == p.url }
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .pressable(onClick = {
                if (loading) return@pressable
                loading = true
                scope.launch {
                    val tracks = DaydreaminApp.instance.repository.playlistTracks(p.url).getOrDefault(emptyList())
                    loading = false
                    if (tracks.isNotEmpty()) {
                        PlayerController.playFromList(tracks, 0)
                        Toaster.show("Playing “${p.title}”")
                    } else {
                        Toaster.show("Couldn’t open that playlist")
                    }
                }
            })
            .padding(horizontal = Space.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(url = p.thumbnail, shape = Radius.cardShape, modifier = Modifier.size(56.dp))
            if (loading) {
                Box(Modifier.size(56.dp).clip(Radius.cardShape).background(Color.Black.copy(alpha = 0.45f)))
                Box(Modifier.size(22.dp).clip(CircleShape).shimmer())
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(p.author.takeIf { it.isNotBlank() }, if (p.trackCount > 0) "${p.trackCount} songs" else null).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .pressable(onClick = {
                    when {
                        saving -> Unit
                        saved -> Toaster.show("Already in your library")
                        else -> {
                            saving = true
                            scope.launch {
                                val tracks = DaydreaminApp.instance.repository.playlistTracks(p.url).getOrDefault(emptyList())
                                saving = false
                                if (tracks.isEmpty()) Toaster.show("Couldn’t save that playlist")
                                else SongActions.savePlaylist(p.title, tracks, sourceUrl = p.url, author = p.author, coverUrl = p.thumbnail)
                            }
                        }
                    }
                }),
            contentAlignment = Alignment.Center,
        ) {
            if (saving) Box(Modifier.size(18.dp).clip(CircleShape).shimmer())
            else Icon(
                if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = if (saved) "Saved to your library" else "Save to your library",
                tint = if (saved) Color.White else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- states

@Composable
private fun ResultsSkeleton() {
    Column(Modifier.fillMaxSize().padding(top = 52.dp)) {
        Box(Modifier.padding(horizontal = Space.gutter).fillMaxWidth().height(124.dp).clip(Radius.panelShape).shimmer())
        Spacer(Modifier.height(Space.l))
        repeat(6) { SkeletonRow() }
    }
}

@Composable
private fun SkeletonRow() {
    Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).clip(Radius.thumbShape).shimmer())
        Column(Modifier.padding(horizontal = 14.dp)) {
            Box(Modifier.size(width = 170.dp, height = 13.dp).clip(Radius.pill).shimmer())
            Spacer(Modifier.height(7.dp))
            Box(Modifier.size(width = 110.dp, height = 11.dp).clip(Radius.pill).shimmer())
        }
    }
}

@Composable
private fun Message(icon: ImageVector, title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null, fill: Boolean = true) {
    Column(
        (if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).padding(horizontal = 40.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (fill) Arrangement.Top else Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).glass(CircleShape, Glass.Regular), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.88f), modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            SolidPillButton(label = action, icon = Icons.Rounded.Refresh, onClick = onAction)
        }
    }
}
