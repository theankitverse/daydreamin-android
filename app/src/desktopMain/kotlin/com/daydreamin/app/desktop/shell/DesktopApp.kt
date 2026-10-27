package com.daydreamin.app.desktop.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.daydreamin.app.AppGraph
import com.daydreamin.app.desktop.components.DesktopScrollbars
import com.daydreamin.app.desktop.components.LocalAccent
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Navigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.pages.CollectionPage
import com.daydreamin.app.desktop.pages.HomePage
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DSize
import com.daydreamin.app.desktop.theme.DSpace
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.ToastHost
import com.daydreamin.app.ui.navigation.NavCommands
import com.daydreamin.app.ui.theme.accentByName

/** Below this the sidebar shows icons only. */
private val WideSidebarFrom = 1000.dp
/** The page never gets narrower than this to make room for the side panel; the panel floats over it instead. */
private val PageMinWidth = 600.dp

/**
 * The desktop app: the sidebar, the page, the side panel, the top bar and the player bar — the
 * layout desktop music apps share — over the same data and player as the phone app.
 */
@Composable
fun DesktopApp(start: Route = Route.Home) {
    val nav = remember { Navigator(start) }
    val accentName by AppGraph.prefs.accentName.collectAsState(initial = "Violet")
    val meta by PlayerController.meta.collectAsState()
    DisposableEffect(nav) {
        DesktopUi.navigator = nav
        NavCommands.goBack = { nav.goBack() }
        NavCommands.openSearch = { if (nav.current !is Route.Search) nav.go(Route.Search()); DesktopUi.requestSearchFocus() }
        onDispose { DesktopUi.navigator = null; NavCommands.goBack = null; NavCommands.openSearch = null }
    }
    CompositionLocalProvider(LocalNavigator provides nav, LocalAccent provides accentByName(accentName)) {
        DesktopScrollbars {
            BoxWithConstraints(Modifier.fillMaxSize().background(DColors.window)) {
                val narrowSidebar = maxWidth < WideSidebarFrom
                val sidebarWidth = if (narrowSidebar) DSize.sidebarNarrow else DSize.sidebarWide
                // A roomier panel on a big screen.
                val panelWidth = if (maxWidth >= 1800.dp) 400.dp else DSize.sidePanel
                val panelFits = maxWidth - sidebarWidth - panelWidth - DSpace.gap * 4 >= PageMinWidth
                SideEffect { DesktopUi.panelFits = panelFits }
                val showPanel = panelFits && DesktopUi.panelOpen && meta.currentSong != null

                Column(Modifier.fillMaxSize()) {
                    TopBar()
                    Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = DSpace.gap)) {
                        Sidebar(narrowSidebar, sidebarWidth)
                        Spacer(Modifier.width(DSpace.gap))
                        Panel(Modifier.weight(1f).fillMaxHeight()) {
                            key(nav.current) { Page(nav.current) }
                        }
                        if (showPanel) {
                            Spacer(Modifier.width(DSpace.gap))
                            SidePanel(Modifier.width(panelWidth).fillMaxHeight())
                        }
                    }
                    PlayerBar()
                }

                // Too narrow for the panel beside the page: it floats over the page when asked for.
                AnimatedVisibility(
                    visible = !panelFits && DesktopUi.overlayOpen && meta.currentSong != null,
                    enter = fadeIn() + slideInHorizontally { it / 4 },
                    exit = fadeOut() + slideOutHorizontally { it / 4 },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = DSize.topBarHeight, end = DSpace.gap, bottom = DSize.playerBarHeight),
                ) {
                    SidePanel(Modifier.width(DSize.sidePanel).fillMaxHeight().shadow(24.dp, RoundedCornerShape(DSize.panelRadius)))
                }

                ToastHost(Modifier.align(Alignment.BottomCenter).padding(bottom = DSize.playerBarHeight + 12.dp), onOpenDownloads = { nav.go(Route.Downloads) })
            }
        }
    }
}

@Composable
private fun Page(route: Route) {
    when (route) {
        Route.Home -> HomePage()
        Route.Liked, Route.Downloads, Route.History, is Route.Playlist, is Route.Collection, is Route.Mood -> CollectionPage(route)
        is Route.Search -> Placeholder("Search", "Search results are the next step of the redesign.")
        Route.Library -> Placeholder("Your Library", "The full Library page is the next step of the redesign.")
        Route.Profile -> Placeholder("Profile", "The Profile page is the next step of the redesign.")
        Route.Stats -> Placeholder("Your stats", "The Statistics page is the next step of the redesign.")
    }
}

@Composable
private fun Placeholder(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(DSpace.pagePad)) {
        Text(title, style = DType.pageTitle)
        Text(body, style = DType.body, modifier = Modifier.padding(top = 8.dp))
    }
}
