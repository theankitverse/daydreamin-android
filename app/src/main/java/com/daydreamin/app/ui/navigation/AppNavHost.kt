package com.daydreamin.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daydreamin.app.ui.theme.daydreamGlassStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.ui.components.AppDrawerContent
import com.daydreamin.app.ui.components.DaydreaminBottomBar
import com.daydreamin.app.ui.components.MiniPlayer
import com.daydreamin.app.ui.screens.home.HomeScreen
import com.daydreamin.app.ui.screens.library.LibraryScreen
import com.daydreamin.app.ui.screens.lyrics.LyricsScreen
import com.daydreamin.app.ui.screens.nowplaying.NowPlayingScreen
import com.daydreamin.app.ui.screens.profile.ProfileScreen
import com.daydreamin.app.ui.screens.profile.StatisticsScreen
import com.daydreamin.app.ui.screens.queue.QueueScreen
import com.daydreamin.app.ui.screens.search.SearchScreen
import com.daydreamin.app.ui.screens.settings.SettingsScreen
import com.daydreamin.app.ui.screens.splash.SplashScreen
import com.daydreamin.app.ui.screens.themecustomize.ThemeCustomizeScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showChrome = currentRoute in Dest.bottomNavRoutes
    val hazeState = remember { HazeState() }
    val glassStyle = daydreamGlassStyle()

    PlayerController.ensureConnected(context)

    fun closeDrawer() = scope.launch { drawerState.close() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showChrome,
        drawerContent = {
            AppDrawerContent(
                hazeState = hazeState,
                glassStyle = glassStyle,
                onHome = { closeDrawer(); navController.navigateTopLevel(Dest.HOME) },
                onSearch = { closeDrawer(); navController.navigateTopLevel(Dest.SEARCH) },
                onLibrary = { closeDrawer(); navController.navigateTopLevel(Dest.LIBRARY) },
                onStatistics = { closeDrawer(); navController.navigate(Dest.STATISTICS) },
                onDownloads = { closeDrawer(); navController.navigate(Dest.SETTINGS) },
                onSettings = { closeDrawer(); navController.navigate(Dest.SETTINGS) },
                onFeedback = { closeDrawer() },
                onDiscord = { closeDrawer() },
                onAbout = { closeDrawer(); navController.navigate(Dest.SETTINGS) },
            )
        },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (showChrome) {
                    // Scoped to this lambda (its own recomposition group) on purpose: the ticking
                    // progress flow should only invalidate the mini player, not the whole screen.
                    val playerMeta by PlayerController.meta.collectAsState()
                    Column(modifier = Modifier.hazeEffect(state = hazeState, style = glassStyle)) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = playerMeta.currentSong != null,
                            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
                            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220)) + fadeOut(tween(160)),
                        ) {
                            playerMeta.currentSong?.let { song ->
                                val playbackProgress by PlayerController.progress.collectAsState()
                                val ratio = if (playbackProgress.durationMs > 0) playbackProgress.positionMs.toFloat() / playbackProgress.durationMs else 0f
                                MiniPlayer(
                                    song = song,
                                    isPlaying = playerMeta.isPlaying,
                                    progress = ratio,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                    onTogglePlay = { PlayerController.togglePlayPause() },
                                    onNext = { PlayerController.next() },
                                    onClick = { navController.navigate(Dest.NOW_PLAYING) },
                                )
                            }
                        }
                        DaydreaminBottomBar(currentRoute = currentRoute, onSelect = { navController.navigateTopLevel(it) })
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Dest.SPLASH,
                modifier = Modifier.hazeSource(hazeState),
                enterTransition = { fadeIn(tween(260, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(260, easing = FastOutSlowInEasing)) },
                popExitTransition = { fadeOut(tween(180)) },
            ) {
                composable(Dest.SPLASH) {
                    SplashScreen(onFinished = { navController.navigateTopLevel(Dest.HOME, popSplash = true) })
                }
                composable(Dest.HOME) {
                    HomeScreen(onOpenDrawer = { scope.launch { drawerState.open() } }, onSearchClick = { navController.navigateTopLevel(Dest.SEARCH) }, contentPadding = padding)
                }
                composable(Dest.SEARCH) { SearchScreen(contentPadding = padding) }
                composable(Dest.LIBRARY) { LibraryScreen(contentPadding = padding) }
                composable(Dest.PROFILE) {
                    ProfileScreen(
                        contentPadding = padding,
                        onStatistics = { navController.navigate(Dest.STATISTICS) },
                        onSettings = { navController.navigate(Dest.SETTINGS) },
                        onThemeCustomize = { navController.navigate(Dest.THEME_CUSTOMIZE) },
                    )
                }
                // Modal-style destinations — pushed up from the bottom over whatever's behind
                // them, like a sheet, instead of the flat tab crossfade above.
                composable(
                    Dest.NOW_PLAYING,
                    enterTransition = { slideUpEnter() },
                    exitTransition = { fadeOut(tween(150)) },
                    popExitTransition = { slideDownExit() },
                    popEnterTransition = { fadeIn(tween(200)) },
                ) {
                    NowPlayingScreen(
                        onBack = { navController.popBackStack() },
                        onQueueClick = { navController.navigate(Dest.QUEUE) },
                        onLyricsClick = { navController.navigate(Dest.LYRICS) },
                    )
                }
                composable(
                    Dest.QUEUE,
                    enterTransition = { slideUpEnter() },
                    exitTransition = { fadeOut(tween(150)) },
                    popExitTransition = { slideDownExit() },
                    popEnterTransition = { fadeIn(tween(200)) },
                ) { QueueScreen(onBack = { navController.popBackStack() }) }
                composable(
                    Dest.LYRICS,
                    enterTransition = { slideUpEnter() },
                    exitTransition = { fadeOut(tween(150)) },
                    popExitTransition = { slideDownExit() },
                    popEnterTransition = { fadeIn(tween(200)) },
                ) { LyricsScreen(onBack = { navController.popBackStack() }) }
                composable(
                    Dest.SETTINGS,
                    enterTransition = { slideUpEnter() },
                    exitTransition = { fadeOut(tween(150)) },
                    popExitTransition = { slideDownExit() },
                    popEnterTransition = { fadeIn(tween(200)) },
                ) { SettingsScreen(onBack = { navController.popBackStack() }) }
                composable(
                    Dest.THEME_CUSTOMIZE,
                    enterTransition = { slideUpEnter() },
                    exitTransition = { fadeOut(tween(150)) },
                    popExitTransition = { slideDownExit() },
                    popEnterTransition = { fadeIn(tween(200)) },
                ) { ThemeCustomizeScreen(onBack = { navController.popBackStack() }) }
                composable(
                    Dest.STATISTICS,
                    enterTransition = { slideUpEnter() },
                    exitTransition = { fadeOut(tween(150)) },
                    popExitTransition = { slideDownExit() },
                    popEnterTransition = { fadeIn(tween(200)) },
                ) { StatisticsScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}

private fun slideUpEnter() = slideInVertically(
    initialOffsetY = { fullHeight -> fullHeight / 3 },
    animationSpec = tween(320, easing = FastOutSlowInEasing),
) + fadeIn(tween(260))

private fun slideDownExit() = slideOutVertically(
    targetOffsetY = { fullHeight -> fullHeight / 3 },
    animationSpec = tween(280, easing = FastOutSlowInEasing),
) + fadeOut(tween(220))

private fun androidx.navigation.NavController.navigateTopLevel(route: String, popSplash: Boolean = false) {
    navigate(route) {
        // Popping the start destination (splash) by its string route hits a route-matching
        // bug in this Navigation-Compose version; popping by the graph's own id instead
        // is the version-safe way to discard it from the back stack entirely.
        if (popSplash) {
            popUpTo(graph.id) { inclusive = true }
        } else {
            popUpTo(graph.findStartDestination().id) { saveState = true }
        }
        launchSingleTop = true
        restoreState = !popSplash
    }
}

