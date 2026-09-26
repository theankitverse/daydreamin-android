package com.daydreamin.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.daydreamin.app.R
import com.daydreamin.app.ui.theme.Divider
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

private data class DrawerItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun AppDrawerContent(
    hazeState: HazeState,
    glassStyle: HazeStyle,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onStatistics: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onFeedback: () -> Unit,
    onDiscord: () -> Unit,
    onAbout: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = Color.Transparent,
        modifier = Modifier.hazeEffect(state = hazeState, style = glassStyle),
    ) {
        Column(modifier = Modifier.fillMaxHeight().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.splash_logo),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Daydreamin", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Music for a calmer you", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(24.dp))

            val topItems = listOf(
                DrawerItem("Home", Icons.Filled.Home, onHome),
                DrawerItem("Search", Icons.Filled.Search, onSearch),
                DrawerItem("Library", Icons.Filled.LibraryMusic, onLibrary),
                DrawerItem("Statistics", Icons.Filled.BarChart, onStatistics),
                DrawerItem("Downloads", Icons.Filled.Download, onDownloads),
                DrawerItem("Settings", Icons.Filled.Settings, onSettings),
            )
            topItems.forEach { DrawerRow(it) }

            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(8.dp))

            val bottomItems = listOf(
                DrawerItem("Send Feedback", Icons.Filled.Feedback, onFeedback),
                DrawerItem("Join Discord", Icons.Filled.Forum, onDiscord),
                DrawerItem("About", Icons.Filled.Info, onAbout),
            )
            bottomItems.forEach { DrawerRow(it) }

            // Credit line — tapping it opens the maker's Instagram.
            val context = androidx.compose.ui.platform.LocalContext.current
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append("Made by ${Creator.NAME} \u00B7 ")
                    withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("@${Creator.INSTAGRAM_HANDLE}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable { Creator.openInstagram(context) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun DrawerRow(item: DrawerItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, contentDescription = null, tint = TextSecondary)
        Spacer(Modifier.width(16.dp))
        Text(item.label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
    }
}
