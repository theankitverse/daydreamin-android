package com.daydreamin.app.ui.screens.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.EmptyState
import com.daydreamin.app.ui.components.FilterChip
import com.daydreamin.app.ui.components.SectionHeader
import com.daydreamin.app.ui.components.ShimmerSongRow
import com.daydreamin.app.ui.components.SongRow
import com.daydreamin.app.ui.components.StaggeredAppear
import com.daydreamin.app.ui.theme.BrandPurple
import com.daydreamin.app.ui.theme.SurfaceVariant
import com.daydreamin.app.ui.theme.TextMuted
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

private const val DISPLAY_NAME = "Ankit"

@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onSearchClick: () -> Unit,
    contentPadding: PaddingValues,
) {
    val vm: HomeViewModel = composeViewModel()
    val state by vm.state.collectAsState()
    val playerMeta by PlayerController.meta.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Daydreamin", style = MaterialTheme.typography.headlineSmall, color = BrandPurple, fontWeight = FontWeight.Bold)
                Text("Good evening, $DISPLAY_NAME", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(BrandPurple, MaterialTheme.colorScheme.secondary)))
                    .clickable(onClick = onOpenDrawer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceVariant)
                        .clickable(onClick = onSearchClick)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted)
                    Spacer(Modifier.width(10.dp))
                    Text("Search songs, artists, or moods...", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(18.dp))
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(genreChips) { chip ->
                        FilterChip(label = chip, selected = state.selectedChip == chip, onClick = { vm.onChipSelected(chip) })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader("For You")
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(moodCards) { mood ->
                        MoodCardView(mood = mood, onClick = { vm.onMoodSelected(mood) })
                    }
                }
                Spacer(Modifier.height(28.dp))
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(if (state.selectedChip == "All") "Trending Now" else state.selectedChip)
                }
                Spacer(Modifier.height(8.dp))
            }

            when {
                state.loading -> items(6) { index ->
                    StaggeredAppear(index, modifier = Modifier.padding(horizontal = 20.dp)) { ShimmerSongRow() }
                }
                state.error != null -> item {
                    EmptyState(message = state.error ?: "Something went wrong", modifier = Modifier.clickable { vm.retry() })
                }
                state.trending.isEmpty() -> item { EmptyState("Nothing here yet.") }
                else -> itemsIndexed(state.trending, key = { _, song -> song.playId }) { index, song ->
                    StaggeredAppear(index, modifier = Modifier.padding(horizontal = 20.dp)) {
                        SongRow(
                            song = song,
                            isPlaying = playerMeta.currentSong?.playId == song.playId,
                            // Home is a discovery surface, not a playlist — Up Next should be
                            // real recommendations, not "whatever else was on this list."
                            onClick = { PlayerController.playSong(song) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodCardView(mood: MoodCard, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(mood.gradient.map { Color(it) }))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column {
            Text(
                mood.title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(mood.subtitle, color = TextPrimary.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
        }
    }
}
