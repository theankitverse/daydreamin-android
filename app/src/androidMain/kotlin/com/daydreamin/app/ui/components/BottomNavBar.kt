package com.daydreamin.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.ui.navigation.Dest
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Motion
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.glass

private data class NavTab(val route: String, val label: String, val selectedIcon: ImageVector, val icon: ImageVector)

private val tabs = listOf(
    NavTab(Dest.HOME, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    NavTab(Dest.SEARCH, "Search", Icons.Rounded.Search, Icons.Outlined.Search),
    NavTab(Dest.LIBRARY, "Library", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    NavTab(Dest.PROFILE, "Profile", Icons.Rounded.Person, Icons.Outlined.Person),
)

private val BarHeight = 62.dp
private val LensWidth = 72.dp
private val LensHeight = 52.dp

/**
 * Four destinations on glass. The current one sits inside a small glass lens that springs
 * across when you switch — one moving object that tells you where you are and where you went,
 * instead of a colored pill that just blinks from tab to tab. The accent color stays reserved
 * for "what's playing". Transparent itself: the frosted backdrop comes from the hazeEffect
 * its caller wraps it in.
 */
@Composable
fun DaydreaminBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(BarHeight)
            .padding(horizontal = 8.dp),
    ) {
        val tabWidth = maxWidth / tabs.size
        val lensX by animateDpAsState(
            targetValue = tabWidth * selectedIndex.coerceAtLeast(0) + (tabWidth - LensWidth) / 2,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
            label = "lensX",
        )
        val lensAlpha by animateFloatAsState(if (selectedIndex >= 0) 1f else 0f, Motion.settle(), label = "lensAlpha")
        Box(
            modifier = Modifier
                .offset(x = lensX, y = (BarHeight - LensHeight) / 2)
                .size(LensWidth, LensHeight)
                .graphicsLayer { alpha = lensAlpha }
                .glass(Radius.pill, Glass.Clear, tint = Color.White.copy(alpha = 0.03f)),
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
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
                    Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.1.sp), color = color)
                }
            }
        }
    }
}
