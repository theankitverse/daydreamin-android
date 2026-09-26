package com.daydreamin.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.ui.navigation.Dest
import com.daydreamin.app.ui.theme.Motion

private data class NavTab(val route: String, val label: String, val selectedIcon: ImageVector, val icon: ImageVector)

private val tabs = listOf(
    NavTab(Dest.HOME, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    NavTab(Dest.SEARCH, "Search", Icons.Rounded.Search, Icons.Outlined.Search),
    NavTab(Dest.LIBRARY, "Library", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    NavTab(Dest.PROFILE, "Profile", Icons.Rounded.Person, Icons.Outlined.Person),
)

/**
 * Four quiet destinations on glass. Selection is shown by weight (filled icon, full-white label)
 * rather than a coloured pill — the accent colour is kept for "what's playing", so it stays meaningful.
 * Transparent itself: the frosted backdrop comes from the hazeEffect its caller wraps it in.
 */
@Composable
fun DaydreaminBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(60.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            val color by animateColorAsState(
                if (selected) Color.White else Color.White.copy(alpha = 0.46f),
                Motion.settle(),
                label = "navColor",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { this.selected = selected }
                    .pressable(role = Role.Tab, onClick = { onSelect(tab.route) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(25.dp))
                Spacer(Modifier.height(3.dp))
                Text(tab.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.1.sp), color = color)
            }
        }
    }
}
