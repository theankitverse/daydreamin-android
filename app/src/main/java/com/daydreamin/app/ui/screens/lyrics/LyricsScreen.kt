package com.daydreamin.app.ui.screens.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.Artwork
import com.daydreamin.app.ui.components.EmptyState
import com.daydreamin.app.ui.components.FullScreenLoading
import com.daydreamin.app.ui.theme.TextMuted
import com.daydreamin.app.ui.theme.TextPrimary
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

@Composable
fun LyricsScreen(onBack: () -> Unit) {
    val vm: LyricsViewModel = composeViewModel()
    val playerMeta by PlayerController.meta.collectAsState()
    val progress by PlayerController.progress.collectAsState()
    val state by vm.state.collectAsState()
    val song = playerMeta.currentSong

    LaunchedEffect(song?.playId) {
        if (song != null) vm.load(song.artist, song.title)
    }

    val listState = rememberLazyListState()
    val activeIndex = state.syncedLines.indexOfLast { it.timeMs <= progress.positionMs }
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(song?.title ?: "Lyrics", color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                if (song != null) Text(song.artist, color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            if (song != null) Artwork(url = song.artworkUrl, modifier = Modifier.padding(4.dp))
        }

        when {
            song == null -> EmptyState("Play a song to see its lyrics.")
            state.loading -> FullScreenLoading(Modifier.fillMaxSize())
            state.notFound -> EmptyState("No lyrics found for \"${song.title}\".")
            state.syncedLines.isNotEmpty() -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(state.syncedLines) { line ->
                    val isActive = state.syncedLines.indexOf(line) == activeIndex
                    Text(
                        line.text,
                        color = if (isActive) MaterialTheme.colorScheme.primary else TextMuted,
                        style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                }
            }
            !state.plainText.isNullOrBlank() -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(state.plainText!!, color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
            else -> EmptyState("No lyrics found for \"${song.title}\".")
        }
    }
}
