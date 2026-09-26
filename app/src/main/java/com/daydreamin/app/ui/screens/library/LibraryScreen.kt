package com.daydreamin.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.EmptyState
import com.daydreamin.app.ui.components.FilterChip
import com.daydreamin.app.ui.components.SongRow
import com.daydreamin.app.ui.components.StaggeredAppear
import com.daydreamin.app.ui.theme.SurfaceVariant
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

@Composable
fun LibraryScreen(contentPadding: PaddingValues) {
    val vm: LibraryViewModel = composeViewModel()
    val tab by vm.tab.collectAsState()
    val liked by vm.likedSongs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val playerMeta by PlayerController.meta.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Your Library",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(LibraryTab.entries.toList()) { t ->
                FilterChip(label = t.name.lowercase().replaceFirstChar { it.uppercase() }, selected = tab == t, onClick = { vm.onTabChange(t) })
            }
        }

        Spacer(Modifier.height(16.dp))

        when (tab) {
            LibraryTab.LIKED -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                if (liked.isEmpty()) {
                    item { EmptyState("Songs you like will show up here.") }
                } else {
                    itemsIndexed(liked, key = { _, song -> song.playId }) { index, song ->
                        StaggeredAppear(index) {
                            SongRow(
                                song = song,
                                isPlaying = playerMeta.currentSong?.playId == song.playId,
                                onClick = { PlayerController.playFromList(liked, liked.indexOf(song)) },
                            )
                        }
                    }
                }
            }

            LibraryTab.PLAYLISTS -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCreateDialog = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        Text("Create Playlist", color = TextPrimary, modifier = Modifier.padding(start = 14.dp), fontWeight = FontWeight.Medium)
                    }
                }
                if (playlists.isEmpty()) {
                    item { EmptyState("No playlists yet.") }
                } else {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) { Text("🎵") }
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(playlist.name, color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text("${playlist.songs.size} songs", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            LibraryTab.ARTISTS -> {
                val artists = liked.distinctBy { it.artist.lowercase() }
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp)) {
                    if (artists.isEmpty()) item { EmptyState("Like some songs to see artists here.") }
                    items(artists) { song ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(url = song.artworkUrl, modifier = Modifier.size(48.dp), shape = CircleShape)
                            Text(song.artist, color = TextPrimary, modifier = Modifier.padding(start = 14.dp), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            LibraryTab.ALBUMS -> {
                val albums = liked.distinctBy { it.album.lowercase() }
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp)) {
                    if (albums.isEmpty()) item { EmptyState("Like some songs to see albums here.") }
                    items(albums) { song ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(url = song.artworkUrl, modifier = Modifier.size(48.dp))
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(song.album, color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text(song.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Playlist name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { vm.createPlaylist(name); showCreateDialog = false }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } },
        )
    }
}
