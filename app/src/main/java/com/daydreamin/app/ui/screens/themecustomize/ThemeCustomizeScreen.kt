package com.daydreamin.app.ui.screens.themecustomize

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.daydreamin.app.ui.components.EqualizerBars
import com.daydreamin.app.ui.components.UserAvatar
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.theme.AccentPalette
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.Space
import com.daydreamin.app.ui.theme.accentByName
import com.daydreamin.app.ui.theme.glass
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

/**
 * The app's one colour setting: the accent. It applies instantly (the whole app re-colours as you
 * tap), with a small preview of what it touches. (The old "Background style" and "Now Playing
 * style" choices were never read by anything, so they're gone rather than left to mislead.)
 */
@Composable
fun ThemeCustomizeScreen(onBack: () -> Unit) {
    val vm: ThemeCustomizeViewModel = composeViewModel()
    val accentName by vm.accentName.collectAsState()
    val accent = accentByName(accentName)

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBase)
            .drawBehind {
                drawRect(Brush.radialGradient(0f to accent.copy(alpha = 0.3f), 1f to Color.Transparent, center = Offset(size.width * 0.15f, 0f), radius = size.width * 1.2f))
            },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Box(
                Modifier.padding(start = 12.dp, top = 8.dp).size(40.dp).pressable(onClick = onBack).glass(Radius.pill, Glass.Clear),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(26.dp)) }
            Text("Theme", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.padding(start = Space.gutter, top = 16.dp))
            Text(
                "Pick the colour that lights up what’s playing, your selections and the controls.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = 6.dp),
            )

            // Preview: the places the accent actually shows up.
            Row(
                Modifier.padding(horizontal = Space.gutter, vertical = 20.dp).fillMaxWidth().glass(Radius.panelShape, Glass.Clear).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(size = 48.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text("Song that’s playing", style = MaterialTheme.typography.titleSmall, color = accent)
                    Text("Artist", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                }
                EqualizerBars(playing = true, color = accent)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(36.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            AccentPalette.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter - 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { option ->
                        val isSel = option.name == accentName
                        val ring by animateDpAsState(if (isSel) 3.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "swatchRing")
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(58.dp)
                                    .semantics { contentDescription = option.name; this.selected = isSel }
                                    .pressable(onClick = { vm.setAccent(option.name) })
                                    .border(ring, Color.White, CircleShape)
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(option.color),
                                contentAlignment = Alignment.Center,
                            ) { if (isSel) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                            Text(option.name, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = if (isSel) 0.95f else 0.5f), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
