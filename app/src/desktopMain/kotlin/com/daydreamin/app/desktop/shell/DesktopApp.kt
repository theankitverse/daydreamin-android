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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.daydreamin.app.AppGraph
import com.daydreamin.app.desktop.components.DesktopScrollbars
import com.daydreamin.app.desktop.components.ProvideSharedState
import com.daydreamin.app.desktop.components.WindowBackdrop
import com.daydreamin.app.desktop.components.LocalAccent
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Navigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.pages.CollectionPage
import com.daydreamin.app.desktop.pages.HomePage
import com.daydreamin.app.desktop.pages.LibraryPage
import com.daydreamin.app.desktop.pages.ProfilePage
import com.daydreamin.app.desktop.pages.StatsPage
import com.daydreamin.app.desktop.pages.SettingsPage
import com.daydreamin.app.desktop.pages.SettingsSection
import com.daydreamin.app.desktop.pages.SearchPage
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
    val hasSong by remember { derivedStateOf { PlayerController.meta.value.currentSong != null } }
    DisposableEffect(nav) {
        DesktopUi.navigator = nav
        NavCommands.goBack = { nav.goBack() }
        NavCommands.openSearch = { if (nav.current !is Route.Search) nav.go(Route.Search()); DesktopUi.requestSearchFocus() }
        onDispose { DesktopUi.navigator = null; NavCommands.goBack = null; NavCommands.openSearch = null }
    }
    CompositionLocalProvider(LocalNavigator provides nav, LocalAccent provides accentByName(accentName)) {
      ProvideSharedState {
        DesktopScrollbars {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                WindowBackdrop()
                val narrowSidebar = maxWidth < WideSidebarFrom
                val sidebarWidth = if (narrowSidebar) DSize.sidebarNarrow else DSize.sidebarWide
                // A roomier panel on a big screen.
                val panelWidth = if (maxWidth >= 1800.dp) 400.dp else DSize.sidePanel
                val panelFits = maxWidth - sidebarWidth - panelWidth - DSpace.gap * 4 >= PageMinWidth
                SideEffect { DesktopUi.panelFits = panelFits }
                val showPanel = panelFits && DesktopUi.panelOpen && hasSong

                Column(Modifier.fillMaxSize()) {
                    TopBar()
                    Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = DSpace.gap)) {
                        Sidebar(narrowSidebar, sidebarWidth)
                        Spacer(Modifier.width(DSpace.gap))
                        Panel(Modifier.weight(1f).fillMaxHeight()) {
                            // Typing a search replaces the route on every key; the page itself stays.
                            key(if (nav.current is Route.Search) "search" else nav.current) { Page(nav.current) }
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
                    visible = !panelFits && DesktopUi.overlayOpen && hasSong,
                    enter = fadeIn() + slideInHorizontally { it / 4 },
                    exit = fadeOut() + slideOutHorizontally { it / 4 },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = DSize.topBarHeight, end = DSpace.gap, bottom = DSize.playerBarHeight),
                ) {
                    // Over the page it needs a solid base, or the page would show through its glass.
                    SidePanel(Modifier.width(DSize.sidePanel).fillMaxHeight().shadow(24.dp, RoundedCornerShape(DSize.panelRadius)).background(Color(0xFF0B0B10), RoundedCornerShape(DSize.panelRadius)))
                }

                ToastHost(Modifier.align(Alignment.BottomCenter).padding(bottom = DSize.playerBarHeight + 12.dp), onOpenDownloads = { nav.go(Route.Downloads) })
            }
        }
      }
    }
}

@Composable
private fun Page(route: Route) {
    when (route) {
        Route.Home -> HomePage()
        Route.Liked, Route.Downloads, Route.History, is Route.Playlist, is Route.Collection, is Route.Mood, is Route.Artist, is Route.YtPlaylist -> CollectionPage(route)
        is Route.Search -> SearchPage(route.query)
        Route.Library -> LibraryPage()
        Route.Profile -> ProfilePage()
        Route.Stats -> StatsPage()
        is Route.Settings -> SettingsPage(route.section?.let { runCatching { SettingsSection.valueOf(it) }.getOrNull() })
    }
}

