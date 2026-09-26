package com.daydreamin.app.ui.screens.themecustomize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daydreamin.app.ui.theme.AccentPalette
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.Surface
import com.daydreamin.app.ui.theme.SurfaceVariant
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

@Composable
fun ThemeCustomizeScreen(onBack: () -> Unit) {
    val vm: ThemeCustomizeViewModel = composeViewModel()
    val accentName by vm.accentName.collectAsState()
    val backgroundStyle by vm.backgroundStyle.collectAsState()
    val nowPlayingStyle by vm.nowPlayingStyle.collectAsState()
    val accentColor = AccentPalette.firstOrNull { it.name == accentName }?.color ?: MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
            Text("Customize Theme", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(BgBase, accentColor.copy(alpha = 0.35f), BgBase))),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }

        Text("Accent Color", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(AccentPalette) { option ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(option.color)
                        .clickable { vm.setAccent(option.name) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (option.name == accentName) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White)
                }
            }
        }

        Text("Background Style", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Default", "Blur", "Solid", "Gradient").forEach { style ->
                OptionPill(label = style, selected = backgroundStyle == style, onClick = { vm.setBackgroundStyle(style) }, modifier = Modifier.weight(1f))
            }
        }

        Text("Now Playing Style", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Classic", "Vinyl", "Wave", "Minimal").forEach { style ->
                OptionPill(label = style, selected = nowPlayingStyle == style, onClick = { vm.setNowPlayingStyle(style) }, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text("Apply Theme", fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OptionPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
}
