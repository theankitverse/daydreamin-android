package com.daydreamin.app.desktop.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.data.recommend.HomeFeed
import com.daydreamin.app.desktop.components.songDragSource
import com.daydreamin.app.desktop.components.CardTile
import com.daydreamin.app.desktop.components.ContextMenuArea
import com.daydreamin.app.desktop.components.PlayingBars
import com.daydreamin.app.desktop.components.rememberIsCurrent
import com.daydreamin.app.desktop.components.rememberIsPlaying
import com.daydreamin.app.desktop.components.DIconButton
import com.daydreamin.app.desktop.components.LocalAccent
import com.daydreamin.app.desktop.components.SectionHeader
import com.daydreamin.app.desktop.components.Shelf
import com.daydreamin.app.desktop.components.WithTooltip
import com.daydreamin.app.desktop.components.columnsFor
import com.daydreamin.app.desktop.components.rememberHover
import com.daydreamin.app.desktop.components.songMenuEntries
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.shell.IconTile
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DSpace
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.LikedTile
import com.daydreamin.app.ui.components.Mosaic
import com.daydreamin.app.ui.components.PlaylistCover
import com.daydreamin.app.ui.components.SongActions
import com.daydreamin.app.ui.screens.home.HomeViewModel
import com.daydreamin.app.ui.screens.home.moodCards
import com.daydreamin.app.ui.theme.ArtworkLight
import com.daydreamin.app.ui.theme.BrandViolet
import com.daydreamin.app.ui.theme.rememberArtworkLight
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

internal val DefaultPageLight = ArtworkLight(BrandViolet, Color(0xFF3D3A9E))

/** The top of a page, washed with [light] — the same glow the phone app draws, as a desktop header gradient. */
@Composable
internal fun PageGlow(light: ArtworkLight, height: Dp = 340.dp) {
    Box(
        Modifier.fillMaxWidth().height(height).background(
            Brush.verticalGradient(listOf(light.key.copy(alpha = 0.42f), light.fill.copy(alpha = 0.16f), Color.Transparent)),
        ),
    )
}

@Composable
fun HomePage() {
    val vm: HomeViewModel = composeViewModel { HomeViewModel() }
    val feed by vm.feed.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val history by vm.history.collectAsState()
    val liked by vm.liked.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val shared = com.daydreamin.app.desktop.components.LocalShared.current
    val nav = LocalNavigator.current
    val lead = feed?.let { it.mix.ifEmpty { it.topPicks } }?.firstOrNull()
    val light = rememberArtworkLight(lead?.let { it.cover.ifBlank { it.artworkUrl } }, DefaultPageLight)
    val update by com.daydreamin.app.data.update.UpdateChecker.state.collectAsState()
    val state = rememberLazyListState()
    val playingId by remember { derivedStateOf { shared.meta.value.let { m -> m.currentSong?.playId.takeIf { m.isPlaying } } } }

    Box(Modifier.fillMaxSize()) {
        PageGlow(light)
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(DSpace.sectionGap)) {
            // A newer desktop version has been published: the same card the phone shows, installing in place.
            if (update.status == com.daydreamin.app.data.update.UpdateStatus.AVAILABLE) item(key = "update") { com.daydreamin.app.ui.components.UpdateBanner(Modifier.padding(horizontal = DSpace.pagePad - 16.dp)) }
            item(key = "greeting") {
                Column(Modifier.padding(horizontal = DSpace.pagePad)) {
                    Text(greeting(), style = DType.pageTitle)
                    Spacer(Modifier.height(16.dp))
                    JumpBackIn(liked, history, playlists, feed)
                }
            }
            feed?.let { f ->
                item(key = "mix") { MixBanner(f, light, refreshing, onRefresh = vm::refreshMix) }
                if (f.topPicks.isNotEmpty()) item(key = "picks") {
                    SongGrid(
                        title = if (f.kind == HomeFeed.KIND_POPULAR) "Top songs in ${regionName(f.region)}" else "Top picks for you",
                        subtitle = if (f.kind == HomeFeed.KIND_POPULAR) "Most played right now" else "Picked from what you play and like",
                        songs = f.topPicks,
                        onShowAll = { nav.go(Route.Collection(if (f.kind == HomeFeed.KIND_POPULAR) "Top songs" else "Top picks for you", null, f.topPicks)) },
                    )
                }
                f.shelves.forEach { shelf ->
                    item(key = "shelf-${shelf.key}") {
                        Shelf(shelf.title, shelf.songs, subtitle = shelf.subtitle, onShowAll = { nav.go(Route.Collection(shelf.title, shelf.subtitle, shelf.songs)) }) { song, m ->
                            SongCard(song, shelf.songs, playingId, m)
                        }
                    }
                }
            }
            if (history.isNotEmpty()) item(key = "recent") {
                val recent = history.distinctBy { it.playId }
                Shelf("Recently played", recent, onShowAll = { nav.go(Route.History) }) { song, m -> SongCard(song, recent, playingId, m, withRadio = true) }
            }
            if (playlists.isNotEmpty()) item(key = "playlists") {
                Shelf("Your playlists", playlists, onShowAll = { nav.go(Route.Library) }) { p, m ->
                    CardTile(
                        p.name, "Playlist · ${p.songs.size} songs",
                        onOpen = { nav.go(Route.Playlist(p.id)) },
                        onPlay = { PlayerController.playFromList(p.songs, 0) }.takeIf { p.songs.isNotEmpty() },
                        modifier = m,
                    ) { am -> Box(am.clip(RoundedCornerShape(6.dp))) { BoxWithConstraints { PlaylistCover(p, maxWidth) } } }
                }
            }
            item(key = "moods") {
                Shelf("Browse by mood", moodCards, minTile = 200.dp, onShowAll = null) { mood, m -> MoodTile(mood.title, Color(mood.accent), m) { nav.go(Route.Mood(mood.title, mood.query)) } }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

private fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    return when { h < 5 -> "Good evening"; h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" }
}

internal fun regionName(code: String?): String =
    code?.let { java.util.Locale("", it).getDisplayCountry(java.util.Locale.ENGLISH).ifBlank { it } } ?: "your country"

/** Big tiles for the places you go back to most: Liked songs, your mix, recent, your playlists. */
@Composable
private fun JumpBackIn(liked: List<Song>, history: List<Song>, playlists: List<com.daydreamin.app.data.prefs.Playlist>, feed: HomeFeed?) {
    val nav = LocalNavigator.current
    data class Tile(val title: String, val songs: List<Song>, val open: () -> Unit, val art: @Composable (Dp) -> Unit)
    val tiles = buildList {
        add(Tile("Liked songs", liked, { nav.go(Route.Liked) }) { LikedTile(it) })
        if (history.isNotEmpty()) add(Tile("Recently played", history, { nav.go(Route.History) }) { IconTile(Icons.Rounded.Refresh, it) })
        feed?.let { f -> val mix = f.mix.ifEmpty { f.topPicks }; if (mix.isNotEmpty()) add(Tile(if (f.kind == HomeFeed.KIND_POPULAR) "Hits in ${regionName(f.region)}" else "Your Daydream Mix", mix, { nav.go(Route.Collection(if (f.kind == HomeFeed.KIND_POPULAR) "Hits in ${regionName(f.region)}" else "Your Daydream Mix", null, mix, "Mix")) }) { Mosaic(mix, it) }) }
        playlists.take(5).forEach { p -> add(Tile(p.name, p.songs, { nav.go(Route.Playlist(p.id)) }) { PlaylistCover(p, it) }) }
    }.take(8)
    BoxWithConstraints {
        val columns = if (maxWidth > 900.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t -> JumpTile(t.title, t.songs, t.open, Modifier.weight(1f), t.art) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun JumpTile(title: String, songs: List<Song>, onOpen: () -> Unit, modifier: Modifier, art: @Composable (Dp) -> Unit) {
    val (source, hovered) = rememberHover()
    val accent = LocalAccent.current
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f))
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .combinedClickable(interactionSource = source, indication = null, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp)) { art(56.dp) }
        Text(title, style = DType.itemTitleStrong, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        if (hovered && songs.isNotEmpty()) {
            Box(
                Modifier.padding(end = 10.dp).size(34.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(accent).combinedClickable { PlayerController.playFromList(songs, 0) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play $title", tint = Color.Black, modifier = Modifier.size(22.dp)) }
        }
    }
}

/** The mix (or what's popular) as a wide banner: its artwork, what it's based on, and its actions. */
@Composable
private fun MixBanner(feed: HomeFeed, light: ArtworkLight, refreshing: Boolean, onRefresh: () -> Unit) {
    val songs = feed.mix.ifEmpty { feed.topPicks }
    if (songs.isEmpty()) return
    val popular = feed.kind == HomeFeed.KIND_POPULAR
    val title = if (popular) "Hits in ${regionName(feed.region)}" else "Your Daydream Mix"
    val meta by PlayerController.meta.collectAsState()
    val playingThis = meta.isPlaying && songs.any { it.playId == meta.currentSong?.playId }
    val nav = LocalNavigator.current
    Row(
        Modifier
            .padding(horizontal = DSpace.pagePad)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(light.key.copy(alpha = 0.30f), light.fill.copy(alpha = 0.14f), Color.White.copy(alpha = 0.03f))))
            .border(1.dp, DColors.panelEdge, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(176.dp).shadow(16.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).combinedClickable { nav.go(Route.Collection(title, null, songs, "Mix")) }) { Mosaic(songs, 176.dp) }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(if (popular) "POPULAR RIGHT NOW" else "MADE FOR YOU", style = DType.label.copy(color = Color.White.copy(alpha = 0.7f)))
            Spacer(Modifier.height(6.dp))
            // Sized to the space: the full title on a narrow page, not "Your Daydream …".
            BoxWithConstraints { Text(title, style = DType.hero.copy(fontSize = if (maxWidth < 420.dp) 28.sp else if (maxWidth < 560.dp) 34.sp else 40.sp, lineHeight = 46.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(6.dp))
            Text(
                if (popular) "Most played and trending there right now · ${songs.size} songs"
                else "Based on ${feed.basedOn.take(3).joinToString(", ")}${if (feed.basedOn.size > 3) " and more" else ""} · ${songs.size} songs",
                style = DType.body, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigPlayButton(playingThis) { if (playingThis) PlayerController.togglePlayPause() else PlayerController.playFromList(songs, 0) }
                DIconButton(Icons.Rounded.Shuffle, "Shuffle play", { PlayerController.playFromList(songs.shuffled(), 0) }, size = 40.dp, iconSize = 24.dp)
                DIconButton(Icons.Rounded.BookmarkBorder, "Save to Your Library", { SongActions.savePlaylist(title, songs, sourceUrl = "daydreamin:mix:${feed.generatedAtMs}", author = "Daydreamin") }, size = 40.dp, iconSize = 24.dp)
                DIconButton(Icons.Rounded.Refresh, if (refreshing) "Making a new mix…" else "New mix", onRefresh, size = 40.dp, iconSize = 22.dp, enabled = !refreshing)
            }
        }
    }
}

/** The page's main Play button (a round accent button, as on Spotify). */
@Composable
internal fun BigPlayButton(playing: Boolean, size: Dp = 52.dp, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    val accent = LocalAccent.current
    WithTooltip(if (playing) "Pause" else "Play") {
        Box(
            Modifier
                .size(if (hovered) size + 3.dp else size)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(accent)
                .hoverable(source)
                .pointerHoverIcon(PointerIcon.Hand)
                .combinedClickable(interactionSource = source, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(size * 0.56f)) }
    }
}

/** Songs as compact rows in as many columns as fit, four rows deep. Click a row to play it. */
@Composable
private fun SongGrid(title: String, subtitle: String?, songs: List<Song>, onShowAll: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = DSpace.pagePad)) {
        val columns = columnsFor(maxWidth, 300.dp, 16.dp, max = 4)
        val rows = 4
        val shown = songs.take(columns * rows)
        Column {
            SectionHeader(title, subtitle, onShowAll.takeIf { songs.size > shown.size })
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                (0 until columns).forEach { c ->
                    Column(Modifier.weight(1f)) {
                        (0 until rows).mapNotNull { r -> shown.getOrNull(c * rows + r)?.let { it to (c * rows + r) } }.forEach { (song, index) ->
                            GridSongRow(song) { PlayerController.playFromList(songs, index) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridSongRow(song: Song, onPlay: () -> Unit) {
    val (source, hovered) = rememberHover()
    val accent = LocalAccent.current
    val isCurrent = rememberIsCurrent(song)
    ContextMenuArea(songMenuEntries(song, onPlay = onPlay)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (hovered) DColors.hover else Color.Transparent)
                .hoverable(source)
                .pointerHoverIcon(PointerIcon.Hand)
                .songDragSource(song)
                .combinedClickable(interactionSource = source, indication = null, onClick = onPlay)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Artwork(url = song.cover.ifBlank { song.artworkUrl }, shape = RoundedCornerShape(4.dp), modifier = Modifier.size(44.dp))
                if (hovered || isCurrent) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        if (isCurrent && !hovered && rememberIsPlaying()) PlayingBars(accent, Modifier.size(16.dp))
                        else Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(song.title, style = DType.itemTitle.copy(color = if (isCurrent) accent else Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = DType.itemSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun SongCard(song: Song, list: List<Song>, playingId: String?, modifier: Modifier, withRadio: Boolean = false) {
    val index = list.indexOf(song).coerceAtLeast(0)
    // withRadio (Recently played, as on the phone): the song's radio follows, not the rest of the row.
    val start = { if (withRadio) PlayerController.playSong(song) else PlayerController.playFromList(list, index) }
    CardTile(
        title = song.title,
        subtitle = song.artist,
        onOpen = start,
        onPlay = { if (playingId == song.playId) PlayerController.togglePlayPause() else start() },
        playing = playingId == song.playId,
        modifier = modifier,
        menu = songMenuEntries(song, onPlay = start),
    ) { m -> Artwork(url = song.cover.ifBlank { song.artworkUrl }, shape = RoundedCornerShape(6.dp), modifier = m) }
}

@Composable
internal fun MoodTile(title: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    Box(
        modifier
            .padding(6.dp)
            .aspectRatio(1.7f)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(color.copy(alpha = if (hovered) 0.95f else 0.8f), color.copy(alpha = 0.35f))))
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .combinedClickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(14.dp),
    ) { Text(title, style = DType.section.copy(fontSize = 20.sp)) }
}
