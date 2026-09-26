package com.daydreamin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.glass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

private data class DrawerItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The side panel: who you are (tap for Profile), every place in the app, and the maker's credit.
 * Only destinations that exist — the old Downloads/About/Feedback/Discord entries either went to
 * Settings or did nothing at all.
 */
@Composable
fun AppDrawerContent(
    hazeState: HazeState,
    glassStyle: HazeStyle,
    currentRoute: String?,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onProfile: () -> Unit,
    onStatistics: () -> Unit,
    onTheme: () -> Unit,
    onSettings: () -> Unit,
) {
    val name by DaydreaminApp.instance.prefs.userName.collectAsState(initial = "")
    ModalDrawerSheet(
        drawerContainerColor = Color.Transparent,
        modifier = Modifier.hazeEffect(state = hazeState, style = glassStyle),
    ) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().pressable(onClick = onProfile).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(size = 52.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(name.ifBlank { "Daydreamin" }, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("View profile", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                }
            }
            Spacer(Modifier.height(22.dp))
            listOf(
                DrawerItem("Home", Icons.Rounded.Home, onHome) to "home",
                DrawerItem("Search", Icons.Rounded.Search, onSearch) to "search",
                DrawerItem("Library", Icons.Rounded.LibraryMusic, onLibrary) to "library",
            ).forEach { (item, route) -> DrawerRow(item, selected = currentRoute == route) }
            Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth().height(0.7.dp).background(Color.White.copy(alpha = 0.08f)))
            listOf(
                DrawerItem("Statistics", Icons.Rounded.BarChart, onStatistics),
                DrawerItem("Theme", Icons.Rounded.Palette, onTheme),
                DrawerItem("Settings", Icons.Rounded.Settings, onSettings),
            ).forEach { DrawerRow(it, selected = false) }

            Spacer(Modifier.weight(1f))
            // Credit line — tapping it opens the maker's Instagram.
            val context = LocalContext.current
            Text(
                buildAnnotatedString {
                    append("Made by ${Creator.NAME} · ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("@${Creator.INSTAGRAM_HANDLE}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.pressable(onClick = { Creator.openInstagram(context) }).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DrawerRow(item: DrawerItem, selected: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressable(onClick = item.onClick)
            .then(if (selected) Modifier.glass(Radius.pill, Glass.Regular) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, contentDescription = null, tint = if (selected) Color.White else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(item.label, style = MaterialTheme.typography.bodyLarge, color = if (selected) Color.White else Color.White.copy(alpha = 0.85f))
    }
}
