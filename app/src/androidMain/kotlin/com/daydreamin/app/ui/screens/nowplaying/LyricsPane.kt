package com.daydreamin.app.ui.screens.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.data.model.LyricLine
import com.daydreamin.app.ui.components.SolidPillButton
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.components.shimmer
import com.daydreamin.app.ui.screens.lyrics.LyricsUiState
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lines light up a touch early — reading is slower than hearing, so exact timing feels late. */
private const val LEAD_MS = 250L
/** After you scroll the lyrics yourself, how long they wait before following the song again. */
private const val RESUME_FOLLOW_MS = 4_000L

private val LyricStyle @Composable get() = MaterialTheme.typography.headlineMedium.copy(
    fontSize = 28.sp,
    lineHeight = 35.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.5).sp,
)

@Composable
fun LyricsPane(
    state: LyricsUiState,
    position: () -> Long,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading -> LyricsSkeleton()
            state.offline -> LyricsMessage(
                icon = Icons.Rounded.CloudOff,
                title = "Lyrics need a connection",
                body = "They'll be here as soon as you're back online.",
                action = { SolidPillButton(label = "Try again", icon = Icons.Rounded.Refresh, onClick = onRetry) },
            )
            state.syncedLines.any { it.text.isNotEmpty() } -> SyncedLyrics(state.syncedLines, position, onSeek)
            !state.plainText.isNullOrBlank() -> PlainLyrics(state.plainText)
            else -> LyricsMessage(
                icon = Icons.Rounded.Lyrics,
                title = "No lyrics for this one",
                body = "Some songs just aren't in the lyrics catalog yet.",
            )
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, position: () -> Long, onSeek: (Long) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Only changes when the active line changes — not every frame the position moves.
    val active by remember(lines) {
        derivedStateOf {
            val p = position() + LEAD_MS
            lines.indexOfLast { it.timeMs <= p }
        }
    }

    // Following the song vs. the user browsing on their own.
    var following by remember { mutableStateOf(true) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) {
        if (dragged) following = false
    }
    LaunchedEffect(following, dragged, listState.isScrollInProgress) {
        if (!following && !dragged && !listState.isScrollInProgress) {
            delay(RESUME_FOLLOW_MS)
            following = true
        }
    }

    var firstPositioning by remember { mutableStateOf(true) }
    LaunchedEffect(active, following) {
        if (!following) return@LaunchedEffect
        val target = active.coerceAtLeast(0)
        if (firstPositioning) {
            listState.scrollToItem(target) // opening lyrics mid-song: be there, don't travel there
            firstPositioning = false
        } else {
            listState.animateScrollToItem(target)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The current line rests about a quarter of the way down; the bottom padding lets the last
        // line get there too. Content fades out at both edges instead of being cut off.
        val topPad = maxHeight * 0.22f
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPad, bottom = maxHeight * 0.7f),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.10f to Color.Black,
                            0.82f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            itemsIndexed(lines, key = { i, l -> "${l.timeMs}-$i" }) { index, line ->
                LyricRow(
                    line = line,
                    isActive = index == active,
                    isPast = index < active,
                    onClick = {
                        onSeek(line.timeMs)
                        following = true
                        scope.launch { listState.animateScrollToItem(index) }
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = !following,
            enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .pressable(onClick = {
                        following = true
                        scope.launch { listState.animateScrollToItem(active.coerceAtLeast(0)) }
                    })
                    .glass(Radius.pill, Glass.Frosted, tint = Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.VerticalAlignCenter, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Back to current line", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

@Composable
private fun LyricRow(line: LyricLine, isActive: Boolean, isPast: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(
        when { isActive -> 1f; isPast -> 0.26f; else -> 0.38f },
        tween(420),
        label = "lyricAlpha",
    )
    val scale by animateFloatAsState(if (isActive) 1f else 0.94f, spring(dampingRatio = 0.8f, stiffness = 260f), label = "lyricScale")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale; scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
    ) {
        if (line.text.isEmpty()) BreathingDots(isActive) else Text(line.text, style = LyricStyle, color = Color.White)
    }
}

/** An instrumental break: three dots that breathe while the music plays on without words. */
@Composable
private fun BreathingDots(isActive: Boolean) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(35.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val s by t.animateFloat(
                initialValue = 0.7f,
                targetValue = if (isActive) 1.15f else 0.7f,
                animationSpec = infiniteRepeatable(tween(900, delayMillis = i * 180), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(11.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).glass(CircleShape, Glass.Frosted, tint = Color.White.copy(alpha = 0.7f)))
        }
    }
}

@Composable
private fun PlainLyrics(text: String) {
    LazyColumn(
        contentPadding = PaddingValues(top = 24.dp, bottom = 80.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                "These lyrics aren't time-synced",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
        itemsIndexed(text.lines()) { _, l ->
            Text(
                l,
                style = LyricStyle.copy(fontSize = 24.sp, lineHeight = 32.sp),
                color = Color.White.copy(alpha = if (l.isBlank()) 0f else 0.82f),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 5.dp),
            )
        }
    }
}

/** Loading: the shape of lyrics, shimmering — the layout doesn't jump when the real lines arrive. */
@Composable
private fun LyricsSkeleton() {
    val widths = listOf(0.78f, 0.62f, 0.86f, 0.54f, 0.7f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 72.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        widths.forEach { w -> Box(Modifier.fillMaxWidth(w).height(26.dp).clip(Radius.pill).shimmer()) }
    }
}

@Composable
private fun LyricsMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).glass(CircleShape, Glass.Regular), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}
