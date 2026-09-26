package com.daydreamin.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.EmptyState
import com.daydreamin.app.ui.components.ShimmerSongRow
import com.daydreamin.app.ui.components.SongRow
import com.daydreamin.app.ui.components.StaggeredAppear
import com.daydreamin.app.ui.theme.SurfaceVariant
import com.daydreamin.app.ui.theme.TextMuted
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

@Composable
fun SearchScreen(contentPadding: PaddingValues) {
    val vm: SearchViewModel = composeViewModel()
    val state by vm.state.collectAsState()
    val playerMeta by PlayerController.meta.collectAsState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceVariant)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.padding(start = 10.dp))
                TextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    placeholder = { Text("Song, artist, lyrics, or \"song by artist\"", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchTab.entries.forEach { tab ->
                val selected = tab == state.tab
                val bgColor by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else SurfaceVariant,
                    animationSpec = tween(200),
                    label = "tabBg",
                )
                val fgColor by animateColorAsState(
                    if (selected) androidx.compose.ui.graphics.Color.White else TextSecondary,
                    animationSpec = tween(200),
                    label = "tabFg",
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(bgColor)
                        .clickable { vm.onTabChange(tab) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        tab.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = fgColor,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

        when {
            state.query.isBlank() -> EmptyState("Search for any song, artist, or mood — or even a line of lyrics.", modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
            state.tab == SearchTab.PLAYLISTS -> when {
                state.playlistsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                state.playlists.isEmpty() -> EmptyState("No playlists found for \"${state.query}\".", modifier = Modifier.padding(top = 48.dp))
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(state.playlists, key = { _, p -> p.url }) { index, playlist ->
                        StaggeredAppear(index) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            val tracks = DaydreaminApp.instance.repository.playlistTracks(playlist.url).getOrDefault(emptyList())
                                            if (tracks.isNotEmpty()) PlayerController.playFromList(tracks, 0)
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Artwork(url = playlist.thumbnail, modifier = Modifier.size(52.dp), shape = RoundedCornerShape(10.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(playlist.title, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (playlist.trackCount >= 0) "${playlist.author} · ${playlist.trackCount} songs" else playlist.author,
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            state.loading -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
                items(6) { ShimmerSongRow() }
            }
            state.error != null -> EmptyState(state.error ?: "Search failed", modifier = Modifier.padding(top = 48.dp))
            state.tab == SearchTab.SONGS && state.songs.isEmpty() -> EmptyState("No results for \"${state.query}\".", modifier = Modifier.padding(top = 48.dp))
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
                when (state.tab) {
                    SearchTab.SONGS -> itemsIndexed(state.songs, key = { _, song -> song.playId }) { index, song ->
                        StaggeredAppear(index) {
                            SongRow(
                                song = song,
                                isPlaying = playerMeta.currentSong?.playId == song.playId,
                                // Search results aren't a playlist — near-duplicate versions of
                                // what you searched for shouldn't become your queue. Real
                                // recommendations come from the YouTube-related path instead.
                                onClick = { PlayerController.playSong(song) },
                            )
                        }
                    }
                    SearchTab.ARTISTS -> items(vm.artists) { (artist, sample) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.onQueryChange(artist) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(url = sample.artworkUrl, modifier = Modifier.size(48.dp), shape = CircleShape)
                            Text(artist, color = TextPrimary, modifier = Modifier.padding(start = 14.dp), fontWeight = FontWeight.Medium)
                        }
                    }
                    SearchTab.ALBUMS -> items(vm.albums) { (album, sample) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(url = sample.artworkUrl, modifier = Modifier.size(48.dp))
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(album, color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text(sample.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    SearchTab.PLAYLISTS -> {} // handled above, outside this branch
                }
            }
        }
    }
}
