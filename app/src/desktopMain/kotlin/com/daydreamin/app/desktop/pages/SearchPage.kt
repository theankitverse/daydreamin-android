package com.daydreamin.app.desktop.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.desktop.components.songDragSource
import com.daydreamin.app.desktop.components.CardTile
import com.daydreamin.app.desktop.components.ContextMenuArea
import com.daydreamin.app.desktop.components.DButton
import com.daydreamin.app.desktop.components.DIconButton
import com.daydreamin.app.desktop.components.LocalShared
import com.daydreamin.app.desktop.components.SectionHeader
import com.daydreamin.app.desktop.components.Shelf
import com.daydreamin.app.desktop.components.TileGrid
import com.daydreamin.app.desktop.components.TrackTableState
import com.daydreamin.app.desktop.components.desktopGlass
import com.daydreamin.app.desktop.components.rememberHover
import com.daydreamin.app.desktop.components.songMenuEntries
import com.daydreamin.app.desktop.components.trackTable
import com.daydreamin.app.desktop.components.trackTableKeys
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DSpace
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.screens.home.moodCards
import com.daydreamin.app.ui.screens.search.SearchTab
import com.daydreamin.app.ui.screens.search.SearchUiState
import com.daydreamin.app.ui.screens.search.SearchViewModel
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

/** What the results show: everything at a glance, or one kind of result in full. */
private enum class SearchView(val label: String) { ALL("All"), SONGS("Songs"), ARTISTS("Artists"), PLAYLISTS("Playlists") }

/**
 * Search: the box lives in the top bar and results follow the typing. With nothing typed, your
 * recent searches and the moods to browse; with a query, the top result beside the best songs,
 * then artists and playlists — or one of those in full.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchPage(query: String) {
    val vm: SearchViewModel = composeViewModel { SearchViewModel() }
    val state by vm.state.collectAsState()
    val history by vm.history.collectAsState()
    val nav = LocalNavigator.current
    var view by remember { mutableStateOf(SearchView.ALL) }
    LaunchedEffect(query) { if (query != state.query) vm.onQueryChange(query) }
    LaunchedEffect(view) { if (view == SearchView.PLAYLISTS) vm.onTabChange(SearchTab.PLAYLISTS) else if (state.tab == SearchTab.PLAYLISTS) vm.onTabChange(SearchTab.SONGS) }
    val listState = rememberLazyListState()
    val table = remember(state.resultsFor) { TrackTableState() }
    // As on the phone: a search result plays with its own radio as Up next, not the rest of the results.
    val play: (Int, List<Song>) -> Unit = { i, list -> vm.commitSearch(); list.getOrNull(i)?.let { PlayerController.playSong(it) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(
                if (view == SearchView.SONGS) Modifier.trackTableKeys(table, { table.sorted(state.songs) }) { i -> play(i, table.sorted(state.songs)) }.focusable() else Modifier,
            ),
            contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp),
        ) {
            if (query.isBlank()) {
                if (history.isNotEmpty()) item(key = "recent") {
                    Column(Modifier.padding(horizontal = DSpace.pagePad).padding(bottom = DSpace.sectionGap)) {
                        SectionHeader("Recent searches", onShowAll = null)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            history.take(12).forEach { q -> RecentChip(q, onOpen = { nav.replace(Route.Search(q)); vm.searchAgain(q) }, onRemove = { vm.removeFromHistory(q) }) }
                        }
                        Spacer(Modifier.height(8.dp))
                        DButton("Clear recent searches", { vm.clearHistory() })
                    }
                }
                item(key = "browse") {
                    Column {
                        SectionHeader("Browse all", modifier = Modifier.padding(horizontal = DSpace.pagePad))
                        TileGrid(moodCards, minTile = 200.dp) { mood, m -> MoodTile(mood.title, Color(mood.accent), m) { nav.go(Route.Mood(mood.title, mood.query)) } }
                    }
                }
                return@LazyColumn
            }

            item(key = "views") {
                Row(Modifier.padding(horizontal = DSpace.pagePad).padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchView.entries.forEach { v -> ViewChip(v.label, view == v) { view = v } }
                }
            }
            val waiting = state.resultsFor != query.trim() && state.resultsFor != query
            when {
                state.error != null -> item(key = "error") {
                    Column(Modifier.padding(horizontal = DSpace.pagePad)) {
                        Text(state.error!!, style = DType.body)
                        Spacer(Modifier.height(12.dp))
                        DButton("Try again", { vm.retry() }, primary = true)
                    }
                }
                view == SearchView.PLAYLISTS -> playlistResults(state, onRetry = vm::retryPlaylists) { p -> vm.commitSearch(); nav.go(Route.YtPlaylist(p.url, p.title, p.author, p.thumbnail)) }
                state.songs.isEmpty() && (state.loading || waiting) -> item(key = "loading") { Text("Searching…", style = DType.body, modifier = Modifier.padding(horizontal = DSpace.pagePad)) }
                state.songs.isEmpty() -> item(key = "none") {
                    Column(Modifier.padding(horizontal = DSpace.pagePad)) {
                        Text("No results found for “$query”", style = DType.section)
                        Text("Check the spelling, or try fewer or different words.", style = DType.body, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                view == SearchView.SONGS -> trackTable(state.songs, table, showAlbum = false, onPlay = play)
                view == SearchView.ARTISTS -> item(key = "artists") {
                    TileGrid(vm.artists(state.songs), minTile = 168.dp) { a, m -> ArtistTile(a.name, a.sample, m) { vm.commitSearch(); nav.go(Route.Artist(a.name)) } }
                }
                else -> {
                    item(key = "top") { TopRow(state.songs, play) }
                    item(key = "artists") {
                        Shelf("Artists", vm.artists(state.songs), modifier = Modifier.padding(top = DSpace.sectionGap), onShowAll = { view = SearchView.ARTISTS }) { a, m ->
                            ArtistTile(a.name, a.sample, m) { vm.commitSearch(); nav.go(Route.Artist(a.name)) }
                        }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

/** The top result as a big card, and the best few songs beside it (stacked on a narrow window). */
@Composable
private fun TopRow(songs: List<Song>, play: (Int, List<Song>) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = DSpace.pagePad)) {
        val side = maxWidth > 780.dp
        val top = @Composable { m: Modifier ->
            Column(m) {
                SectionHeader("Top result")
                TopResultCard(songs.first()) { play(0, songs) }
            }
        }
        val list = @Composable { m: Modifier ->
            Column(m) {
                SectionHeader("Songs")
                songs.take(4).forEachIndexed { i, s -> CompactSongRow(s) { play(i, songs) } }
            }
        }
        if (side) Row { top(Modifier.weight(0.42f)); Spacer(Modifier.width(24.dp)); list(Modifier.weight(0.58f)) }
        else Column { top(Modifier.fillMaxWidth()); Spacer(Modifier.height(DSpace.sectionGap)); list(Modifier.fillMaxWidth()) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopResultCard(song: Song, onPlay: () -> Unit) {
    val (source, hovered) = rememberHover()
    val shared = LocalShared.current
    val playing by remember(song.playId) { derivedStateOf { shared.meta.value.let { it.isPlaying && it.currentSong?.playId == song.playId } } }
    ContextMenuArea(songMenuEntries(song, onPlay)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(236.dp)
                .desktopGlass(RoundedCornerShape(10.dp), tint = if (hovered) DColors.ink.copy(alpha = 0.10f) else DColors.ink.copy(alpha = 0.05f))
                .hoverable(source)
                .pointerHoverIcon(PointerIcon.Hand)
                .combinedClickable(interactionSource = source, indication = null, onClick = onPlay)
                .padding(20.dp),
        ) {
            Column {
                Artwork(url = song.cover.ifBlank { song.artworkUrl }, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(96.dp).shadow(12.dp, RoundedCornerShape(6.dp)))
                Spacer(Modifier.height(18.dp))
                Text(song.title, style = DType.pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(song.artist, style = DType.body.copy(color = DColors.ink.copy(alpha = 0.75f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                        Text("Song", style = DType.small.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
            if (hovered || playing) Box(Modifier.align(Alignment.BottomEnd)) { BigPlayButton(playing) { if (playing) PlayerController.togglePlayPause() else onPlay() } }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactSongRow(song: Song, onPlay: () -> Unit) {
    val (source, hovered) = rememberHover()
    val isCurrent = com.daydreamin.app.desktop.components.rememberIsCurrent(song)
    val accent = com.daydreamin.app.desktop.components.LocalAccent.current
    ContextMenuArea(songMenuEntries(song, onPlay)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (hovered) DColors.hover else Color.Transparent)
                .hoverable(source)
                .songDragSource(song)
                .combinedClickable(interactionSource = source, indication = null, onClick = {}, onDoubleClick = onPlay)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Artwork(url = song.cover.ifBlank { song.artworkUrl }, shape = RoundedCornerShape(4.dp), modifier = Modifier.size(40.dp))
                if (hovered) Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.5f)).pointerHoverIcon(PointerIcon.Hand).combinedClickable(onClick = onPlay), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${song.title}", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(song.title, style = DType.itemTitle.copy(color = if (isCurrent) accent else DColors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = DType.itemSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(com.daydreamin.app.desktop.components.formatDuration(song.duration), style = DType.numeric)
        }
    }
}

@Composable
private fun ArtistTile(name: String, sample: Song, modifier: Modifier, onOpen: () -> Unit) {
    CardTile(name, "Artist", onOpen = onOpen, onPlay = null, modifier = modifier, round = true) { m ->
        Artwork(url = sample.cover.ifBlank { sample.artworkUrl }, shape = CircleShape, modifier = m)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.playlistResults(
    state: SearchUiState,
    onRetry: () -> Unit,
    onOpen: (com.daydreamin.app.data.youtube.YtPlaylist) -> Unit,
) {
    when {
        state.playlistsLoading -> item(key = "pl-loading") { Text("Finding playlists…", style = DType.body, modifier = Modifier.padding(horizontal = DSpace.pagePad)) }
        state.playlistsError != null -> item(key = "pl-error") {
            Column(Modifier.padding(horizontal = DSpace.pagePad)) {
                Text(state.playlistsError, style = DType.body)
                Spacer(Modifier.height(12.dp))
                DButton("Try again", onRetry, primary = true)
            }
        }
        state.playlists.isEmpty() -> item(key = "pl-none") { Text("No playlists for this one.", style = DType.body, modifier = Modifier.padding(horizontal = DSpace.pagePad)) }
        else -> item(key = "pl-grid") {
            TileGrid(state.playlists) { p, m ->
                CardTile(p.title, listOfNotNull(p.author.ifBlank { null }, p.trackCount.takeIf { it > 0 }?.let { "$it songs" }).joinToString(" · "), onOpen = { onOpen(p) }, onPlay = null, modifier = m) { am ->
                    Artwork(url = p.thumbnail, shape = RoundedCornerShape(6.dp), modifier = am)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentChip(query: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    val (source, hovered) = rememberHover()
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (hovered) DColors.ink.copy(alpha = 0.14f) else DColors.ink.copy(alpha = 0.07f))
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .combinedClickable(interactionSource = source, indication = null, onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.History, contentDescription = null, tint = DColors.iconIdle, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(query, style = DType.small.copy(color = DColors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        DIconButton(Icons.Rounded.Close, "Remove “$query”", onRemove, size = 26.dp, iconSize = 14.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ViewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) DColors.ink else if (hovered) DColors.ink.copy(alpha = 0.14f) else DColors.ink.copy(alpha = 0.07f))
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .combinedClickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = DType.small.copy(fontWeight = FontWeight.SemiBold, color = if (selected) DColors.onInk else DColors.ink)) }
}
