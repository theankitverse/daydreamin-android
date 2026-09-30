package com.daydreamin.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.daydreamin.app.ui.theme.BgBase
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.daydreamin.app.ui.components.PlayerSheet
import com.daydreamin.app.ui.screens.library.LibraryLaunch
import com.daydreamin.app.ui.components.ToastHost
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.graphics.graphicsLayer
import com.daydreamin.app.ui.screens.nowplaying.NowPlayingScreen
import com.daydreamin.app.ui.screens.profile.ProfileScreen
import com.daydreamin.app.ui.screens.profile.StatisticsScreen
import com.daydreamin.app.ui.screens.queue.QueueScreen
import com.daydreamin.app.ui.screens.search.SearchScreen
import com.daydreamin.app.ui.screens.search.SearchFocus
import com.daydreamin.app.ui.screens.settings.SettingsScreen
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
    val onTab = currentRoute in Dest.bottomNavRoutes
    // While Now Playing grows out of the mini player, the chrome stays on screen fading out
    // (the route has already changed, but the flight hasn't finished).
    val showChrome = onTab || (currentRoute == Dest.NOW_PLAYING && PlayerSheet.morphInProgress)
    val hazeState = remember { HazeState() }
    val glassStyle = daydreamGlassStyle()
    val chromeStyle = remember {
        HazeStyle(backgroundColor = BgBase, tints = listOf(HazeTint(Color.Black.copy(alpha = 0.55f))), blurRadius = 28.dp, noiseFactor = 0.05f, fallbackTint = com.daydreamin.app.ui.theme.GlassFallback)
    }

    PlayerController.ensureConnected(context)

    fun closeDrawer() = scope.launch { drawerState.close() }

    // Back closes an open drawer instead of leaving the app from underneath it.
    androidx.activity.compose.BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = onTab,
        drawerContent = {
            AppDrawerContent(
                hazeState = hazeState,
                glassStyle = glassStyle,
                currentRoute = currentRoute,
                onHome = { closeDrawer(); navController.navigateTopLevel(Dest.HOME) },
                onSearch = { closeDrawer(); navController.navigateTopLevel(Dest.SEARCH) },
                onLibrary = { closeDrawer(); navController.navigateTopLevel(Dest.LIBRARY) },
                onProfile = { closeDrawer(); navController.navigateTopLevel(Dest.PROFILE) },
                onStatistics = { closeDrawer(); navController.navigate(Dest.STATISTICS) },
                onTheme = { closeDrawer(); navController.navigate(Dest.THEME_CUSTOMIZE) },
                onSettings = { closeDrawer(); navController.navigate(Dest.SETTINGS) },
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
                    val chromeFadePx = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }
                    // Progressive: clear at the top edge, fully frosted by the nav bar — so the
                    // floating mini player (which blurs on its own) sits over a soft fade, not a slab.
                    Column(
                        modifier = Modifier
                            // Fades away as Now Playing opens and back as it closes into the mini player.
                            .graphicsLayer { alpha = 1f - PlayerSheet.expansion }
                            .then(
                                if (dev.chrisbanes.haze.HazeDefaults.blurEnabled()) {
                                    Modifier.hazeEffect(state = hazeState, style = chromeStyle) {
                                        inputScale = dev.chrisbanes.haze.HazeInputScale.Auto
                                        progressive = HazeProgressive.verticalGradient(
                                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                                            startY = 0f,
                                            startIntensity = 0f,
                                            endY = chromeFadePx,
                                            endIntensity = 1f,
                                            preferPerformance = true,
                                        )
                                    }
                                } else {
                                    // No blur below Android 12L: the same clear-to-solid fade, painted plainly —
                                    // a see-through tint left song titles readable behind the tab labels.
                                    Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, BgBase), startY = 0f, endY = chromeFadePx))
                                },
                            ),
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = playerMeta.currentSong != null,
                            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeIn(tween(220)),
                            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220)) + fadeOut(tween(160)),
                        ) {
                            playerMeta.currentSong?.let { song ->
                                val playbackProgress = PlayerController.progress.collectAsState()
                                MiniPlayer(
                                    song = song,
                                    isPlaying = playerMeta.isPlaying,
                                    progress = {
                                        val p = playbackProgress.value
                                        if (p.durationMs > 0) p.positionMs.toFloat() / p.durationMs else 0f
                                    },
                                    hazeState = hazeState,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                                    onTogglePlay = { PlayerController.togglePlayPause() },
                                    onNext = { PlayerController.next() },
                                    onClick = {
                                        PlayerSheet.requestMorphOpen()
                                        navController.navigate(Dest.NOW_PLAYING)
                                    },
                                )
                            }
                        }
                        DaydreaminBottomBar(currentRoute = currentRoute, onSelect = { navController.navigateTopLevel(it) })
                    }
                }
            },
        ) { padding ->
          val openPlayer = remember(navController) {
              {
                  PlayerSheet.requestMorphOpen()
                  navController.navigate(Dest.NOW_PLAYING)
              }
          }
          androidx.compose.runtime.CompositionLocalProvider(com.daydreamin.app.ui.components.LocalOpenPlayer provides openPlayer) {
          Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Dest.HOME,
                modifier = Modifier.hazeSource(hazeState),
                // Fade-through: the outgoing screen clears first, then the incoming one fades in and
                // settles from a hair smaller — two screens' text never overlaps mid-transition.
                enterTransition = { fadeThroughEnter() },
                // Opening Now Playing keeps the current screen visible beneath it for the whole flight;
                // closing it reveals that screen instantly (it was "under" the player all along).
                exitTransition = {
                    if (targetState.destination.route == Dest.NOW_PLAYING) ExitTransition.KeepUntilTransitionsFinished else fadeOut(tween(90))
                },
                popEnterTransition = {
                    if (initialState.destination.route == Dest.NOW_PLAYING) EnterTransition.None else fadeThroughEnter()
                },
                popExitTransition = { fadeOut(tween(90)) },
            ) {
                composable(Dest.HOME) {
                    HomeScreen(
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearchClick = { SearchFocus.requested = true; navController.navigateTopLevel(Dest.SEARCH) },
                        onOpenLibrary = { tab, playlistId ->
                            LibraryLaunch.tab = tab
                            LibraryLaunch.playlistId = playlistId
                            navController.navigateTopLevel(Dest.LIBRARY)
                        },
                        contentPadding = padding,
                    )
                }
                composable(Dest.SEARCH) {
                    SearchScreen(
                        contentPadding = padding,
                        onOpenPlayer = {
                            PlayerSheet.requestMorphOpen()
                            navController.navigate(Dest.NOW_PLAYING)
                        },
                    )
                }
                composable(Dest.LIBRARY) {
                    // Draws its own glow behind the status bar, like Home.
                    LibraryScreen(
                        contentPadding = padding,
                        onOpenPlayer = {
                            PlayerSheet.requestMorphOpen()
                            navController.navigate(Dest.NOW_PLAYING)
                        },
                        onGoHome = { navController.navigateTopLevel(Dest.HOME) },
                    )
                }
                composable(Dest.PROFILE) {
                    // Draws its own glow behind the status bar, like Home and Library.
                    ProfileScreen(
                        contentPadding = padding,
                        onStatistics = { navController.navigate(Dest.STATISTICS) },
                        onSettings = { navController.navigate(Dest.SETTINGS) },
                        onThemeCustomize = { navController.navigate(Dest.THEME_CUSTOMIZE) },
                        onOpenLibrary = { tab ->
                            LibraryLaunch.tab = tab
                            navController.navigateTopLevel(Dest.LIBRARY)
                        },
                        onOpenPlayer = {
                            PlayerSheet.requestMorphOpen()
                            navController.navigate(Dest.NOW_PLAYING)
                        },
                    )
                }
                // Modal-style destinations — pushed up from the bottom over whatever's behind
                // them, like a sheet, instead of the flat tab crossfade above.
                // Now Playing choreographs its own open/close (artwork flying to and from the mini
                // player) off this transition; the navigation-level effects are only the fallback
                // for when there's no mini player to fly from/to.
                composable(
                    Dest.NOW_PLAYING,
                    enterTransition = { if (PlayerSheet.isMorphPending) EnterTransition.None else slideUpEnter() },
                    // The Queue sheet slides up *over* the player, so the player stays put beneath it.
                    exitTransition = { if (targetState.destination.route == Dest.QUEUE) ExitTransition.KeepUntilTransitionsFinished else fadeOut(tween(150)) },
                    popExitTransition = {
                        // KeepUntilTransitionsFinished (not None): stay composed while the artwork flies home.
                        if (targetState.destination.route in Dest.bottomNavRoutes && PlayerSheet.miniArtworkBounds != null) ExitTransition.KeepUntilTransitionsFinished else slideDownExit()
                    },
                    popEnterTransition = { if (initialState.destination.route == Dest.QUEUE) EnterTransition.None else fadeIn(tween(200)) },
                ) {
                    NowPlayingScreen(
                        visibility = this,
                        closingToMini = {
                            navController.currentBackStackEntry?.destination?.route in Dest.bottomNavRoutes && PlayerSheet.miniArtworkBounds != null
                        },
                        onBack = { navController.popIfOn(Dest.NOW_PLAYING) },
                        onQueueClick = { navController.navigate(Dest.QUEUE) },
                    )
                }
                // A glass sheet over Now Playing: it animates itself (slide + the room dimming) off
                // this transition, so the navigation-level effects stay out of the way.
                composable(
                    Dest.QUEUE,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { fadeOut(tween(150)) },
                    popExitTransition = { ExitTransition.KeepUntilTransitionsFinished },
                    popEnterTransition = { fadeIn(tween(200)) },
                ) { QueueScreen(visibility = this, onBack = { navController.popIfOn(Dest.QUEUE) }) }
                composable(
                    Dest.SETTINGS,
                    enterTransition = { slideUpEnter() },
                    exitTransition = { fadeOut(tween(150)) },
                    popExitTransition = { slideDownExit() },
                    popEnterTransition = { fadeIn(tween(200)) },
                ) { SettingsScreen(onBack = { navController.popIfOn(Dest.SETTINGS) }) }
                composable(
                    Dest.THEME_CUSTOMIZE,
                    enterTransition = { slideUpEnter() },
                    exitTransition = { fadeOut(tween(150)) },
                    popExitTransition = { slideDownExit() },
                    popEnterTransition = { fadeIn(tween(200)) },
                ) { ThemeCustomizeScreen(onBack = { navController.popIfOn(Dest.THEME_CUSTOMIZE) }) }
                composable(
                    Dest.STATISTICS,
                    enterTransition = { slideUpEnter() },
                    exitTransition = { fadeOut(tween(150)) },
                    popExitTransition = { slideDownExit() },
                    popEnterTransition = { fadeIn(tween(200)) },
                ) { StatisticsScreen(onBack = { navController.popIfOn(Dest.STATISTICS) }) }
            }
            // Confirmations for song actions ("Added to queue"), floating just above the bottom chrome.
            ToastHost(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding() + 12.dp))
          }
          }
        }
    }
}

private fun fadeThroughEnter() =
    fadeIn(tween(210, delayMillis = 90, easing = FastOutSlowInEasing)) +
        androidx.compose.animation.scaleIn(initialScale = 0.985f, animationSpec = tween(300, delayMillis = 90, easing = FastOutSlowInEasing))

private fun slideUpEnter() = slideInVertically(
    initialOffsetY = { fullHeight -> fullHeight / 3 },
    animationSpec = tween(320, easing = FastOutSlowInEasing),
) + fadeIn(tween(260))

private fun slideDownExit() = slideOutVertically(
    targetOffsetY = { fullHeight -> fullHeight / 3 },
    animationSpec = tween(280, easing = FastOutSlowInEasing),
) + fadeOut(tween(220))

/**
 * Pops [route] only if it's what's actually showing. A sheet can ask to close more than once
 * (a pull gesture ending plus its fling, say) — a bare popBackStack() would then also close
 * whatever was underneath it.
 */
private fun androidx.navigation.NavController.popIfOn(route: String) {
    if (currentBackStackEntry?.destination?.route == route) popBackStack()
}

private fun androidx.navigation.NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

