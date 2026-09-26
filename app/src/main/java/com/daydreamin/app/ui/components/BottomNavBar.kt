package com.daydreamin.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.daydreamin.app.ui.navigation.Dest
import com.daydreamin.app.ui.theme.TextMuted

private data class NavTab(val route: String, val label: String, val filled: ImageVector, val outline: ImageVector)

private val tabs = listOf(
    NavTab(Dest.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavTab(Dest.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    NavTab(Dest.LIBRARY, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    NavTab(Dest.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun DaydreaminBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    // Transparent on purpose — the frosted-glass blur comes from the hazeEffect the caller
    // wraps this bar in, not from a solid background here.
    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            val iconScale by animateFloatAsState(
                if (selected) 1.15f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "navIconScale",
            )
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab.route) },
                icon = {
                    Icon(
                        if (selected) tab.filled else tab.outline,
                        contentDescription = tab.label,
                        modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                    )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}
