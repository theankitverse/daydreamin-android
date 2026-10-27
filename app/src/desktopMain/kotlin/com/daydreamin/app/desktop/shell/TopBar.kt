package com.daydreamin.app.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.daydreamin.app.desktop.components.DIconButton
import com.daydreamin.app.desktop.components.MenuEntry
import com.daydreamin.app.desktop.components.MenuPopup
import com.daydreamin.app.desktop.components.WithTooltip
import com.daydreamin.app.desktop.components.rememberHover
import com.daydreamin.app.desktop.nav.LocalNavigator
import com.daydreamin.app.desktop.nav.Route
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DSize
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.ui.components.UserAvatar

/** The window, when there is one (not in off-screen snapshots): lets the top bar drag it. */
val LocalWindowScope = staticCompositionLocalOf<WindowScope?> { null }
/** Room to leave for the Mac's window buttons, drawn over the app's own top bar. */
val LocalTitleBarInset = staticCompositionLocalOf { 0.dp }

/** Opens Settings (its own window). Set by the app. */
var openSettings: () -> Unit = {}
/** Opens Theme, set by the app. */
var openTheme: () -> Unit = {}

@Composable
fun TopBar(modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    val window = LocalWindowScope.current
    Box(modifier.fillMaxWidth().height(DSize.topBarHeight)) {
        // The bar's empty space moves the window, as a title bar would.
        if (window != null) with(window) { WindowDraggableArea(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) } }
        Row(Modifier.fillMaxSize().padding(start = 12.dp + LocalTitleBarInset.current, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            DIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Go back", { nav.goBack() }, enabled = nav.canGoBack, shortcut = "${cmd}[")
            DIconButton(Icons.AutoMirrored.Rounded.ArrowForward, "Go forward", { nav.goForward() }, enabled = nav.canGoForward, shortcut = "${cmd}]")
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HomeButton(selected = nav.current == Route.Home) { nav.go(Route.Home) }
                    Spacer(Modifier.width(8.dp))
                    SearchBox(Modifier.widthIn(max = 460.dp).fillMaxWidth())
                }
            }
            ProfileButton()
        }
    }
}

@Composable
private fun HomeButton(selected: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    WithTooltip("Home") {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (hovered) Color.White.copy(alpha = 0.16f) else DColors.field)
                .hoverable(source)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = source, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Home, contentDescription = "Home", tint = if (selected || hovered) Color.White else DColors.iconIdle, modifier = Modifier.size(22.dp))
        }
    }
}

/** The search box: always there, Cmd/Ctrl+K jumps to it, typing shows results as you go. */
@Composable
private fun SearchBox(modifier: Modifier) {
    val nav = LocalNavigator.current
    val current = nav.current
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val (source, hovered) = rememberHover()
    // The same one the Search page shows results from: Enter saves the search to your history.
    val searchVm: com.daydreamin.app.ui.screens.search.SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel { com.daydreamin.app.ui.screens.search.SearchViewModel() }
    LaunchedEffect(DesktopUi.focusSearch) { if (DesktopUi.focusSearch > 0) runCatching { focus.requestFocus() } }
    // Leaving the search page (or arriving with a query, "Go to artist") keeps the box in step.
    LaunchedEffect(current) {
        if (current is Route.Search) { if (current.query != text && !focused) text = current.query } else if (!focused) text = ""
    }
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) DColors.fieldFocused else if (hovered) Color.White.copy(alpha = 0.11f) else DColors.field)
            .border(1.dp, if (focused) Color.White.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(50))
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Text)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = if (focused) Color.White else DColors.iconIdle, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text("What do you want to play?", style = DType.body.copy(color = Color.White.copy(alpha = 0.5f)))
            BasicTextField(
                value = text,
                onValueChange = { v ->
                    text = v
                    // Results follow the typing; history only gets one entry for the whole search.
                    if (current is Route.Search) nav.replace(Route.Search(v)) else nav.go(Route.Search(v))
                },
                singleLine = true,
                textStyle = DType.body.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchVm.commitSearch(); focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { focusManager.clearFocus(); true } else false
                    },
            )
        }
        if (text.isNotEmpty()) {
            DIconButton(Icons.Rounded.Close, "Clear search", { text = ""; nav.replace(Route.Search("")); runCatching { focus.requestFocus() } }, size = 28.dp, iconSize = 17.dp)
        } else if (!focused) {
            Text("${cmd}K", style = DType.small.copy(color = Color.White.copy(alpha = 0.35f)), modifier = Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
private fun ProfileButton() {
    val nav = LocalNavigator.current
    var open by remember { mutableStateOf(false) }
    WithTooltip("Your profile") {
        Box(Modifier.size(40.dp).clip(CircleShape).pointerHoverIcon(PointerIcon.Hand).clickable { open = true }, contentAlignment = Alignment.Center) {
            UserAvatar(size = 34.dp, ring = false)
        }
    }
    if (open) {
        MenuPopup(
            listOf(
                MenuEntry("Profile", Icons.Rounded.Person) { nav.go(Route.Profile) },
                MenuEntry("Your stats", Icons.Rounded.BarChart) { nav.go(Route.Stats) },
                MenuEntry("Theme", Icons.Rounded.Palette) { openTheme() },
                null,
                MenuEntry("Settings", Icons.Rounded.Settings, shortcut = "$cmd,") { openSettings() },
            ),
            onDismiss = { open = false },
        )
    }
}
