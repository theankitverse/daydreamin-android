package com.daydreamin.app.desktop.pages

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daydreamin.app.AppGraph
import com.daydreamin.app.data.download.DownloadStatus
import com.daydreamin.app.data.download.Downloads
import com.daydreamin.app.desktop.Backdrop
import com.daydreamin.app.desktop.DesktopSettings
import com.daydreamin.app.desktop.LyricsSize
import com.daydreamin.app.desktop.components.ConfirmDialog
import com.daydreamin.app.desktop.components.DButton
import com.daydreamin.app.desktop.components.desktopClickable
import com.daydreamin.app.desktop.components.desktopGlass
import com.daydreamin.app.desktop.components.rememberHover
import com.daydreamin.app.desktop.shell.DesktopUi
import com.daydreamin.app.desktop.shell.cmd
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DSpace
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.EqPreset
import com.daydreamin.app.player.PlayerController
import com.daydreamin.app.platform.PickKind
import com.daydreamin.app.platform.rememberPicker
import com.daydreamin.app.system.LaunchAtLogin
import com.daydreamin.app.ui.components.Toaster
import com.daydreamin.app.ui.screens.settings.AboutCard
import com.daydreamin.app.ui.screens.settings.CROSSFADE_CHOICES
import com.daydreamin.app.ui.screens.settings.CreatorRow
import com.daydreamin.app.ui.screens.settings.ExtractorRow
import com.daydreamin.app.ui.screens.settings.GlassSwitch
import com.daydreamin.app.ui.screens.settings.SettingsViewModel
import com.daydreamin.app.ui.screens.settings.SupportRow
import com.daydreamin.app.ui.screens.settings.UpdateRow
import com.daydreamin.app.ui.screens.settings.UpiRow
import com.daydreamin.app.ui.screens.settings.crossfadeDescription
import com.daydreamin.app.ui.screens.settings.eqDescription
import com.daydreamin.app.ui.screens.settings.formatBytes
import com.daydreamin.app.ui.screens.themecustomize.ThemeCustomizeViewModel
import com.daydreamin.app.ui.theme.AccentPalette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

/** The pages of Settings, listed down the left. */
enum class SettingsSection(val label: String, val blurb: String, val icon: ImageVector) {
    PLAYBACK("Playback", "How your music sounds.", Icons.Rounded.GraphicEq),
    LYRICS("Lyrics", "The full-window lyrics view.", Icons.Rounded.Lyrics),
    APPEARANCE("Appearance", "Colours and the look of the window.", Icons.Rounded.Palette),
    WINDOW("Window & system", "How Daydreamin fits in with the rest of your computer.", Icons.Rounded.DesktopWindows),
    OFFLINE("Downloads & storage", "Songs and pictures kept on this computer.", Icons.Rounded.DownloadForOffline),
    LIBRARY("Library & history", "Back up your music, and what Daydreamin remembers.", Icons.Rounded.LibraryMusic),
    SHORTCUTS("Keyboard shortcuts", "Everything you can do without the mouse.", Icons.Rounded.Keyboard),
    ABOUT("About", "Version, updates and the person behind it.", Icons.Rounded.Info),
}

/**
 * Settings, laid out for a window: the pages down the left, the one you pick on the right — each
 * on its own, in grouped cards, like a computer's own settings.
 */
@Composable
fun SettingsPage(start: SettingsSection? = null) {
    var section by rememberSaveable { mutableStateOf(start ?: SettingsSection.PLAYBACK) }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(236.dp).fillMaxHeight().padding(start = 12.dp, top = 24.dp, end = 8.dp)) {
            Text("Settings", style = DType.pageTitle, modifier = Modifier.padding(start = 12.dp, bottom = 16.dp))
            SettingsSection.entries.forEach { s ->
                val selected = section == s
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) DColors.selected else Color.Transparent)
                        .desktopClickable(RoundedCornerShape(6.dp)) { section = s }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(s.icon, contentDescription = null, tint = if (selected) Color.White else DColors.iconIdle, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(s.label, style = DType.itemTitle.copy(color = if (selected) Color.White else Color.White.copy(alpha = 0.75f)))
                }
            }
        }
        Crossfade(section, Modifier.weight(1f).fillMaxHeight(), animationSpec = tween(160), label = "settings") { s ->
            val scroll = rememberScrollState()
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 16.dp, end = DSpace.pagePad, top = 28.dp, bottom = 80.dp)) {
                    Column(Modifier.widthIn(max = 760.dp)) {
                        Text(s.label, style = DType.pageTitle)
                        Text(s.blurb, style = DType.body, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
                        when (s) {
                            SettingsSection.PLAYBACK -> PlaybackSettings()
                            SettingsSection.LYRICS -> LyricsSettings()
                            SettingsSection.APPEARANCE -> AppearanceSettings()
                            SettingsSection.WINDOW -> WindowSettings()
                            SettingsSection.OFFLINE -> StorageSettings()
                            SettingsSection.LIBRARY -> LibrarySettings()
                            SettingsSection.SHORTCUTS -> ShortcutList()
                            SettingsSection.ABOUT -> AboutSettings()
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun PlaybackSettings() {
    val vm: SettingsViewModel = composeViewModel { SettingsViewModel() }
    val normalization by vm.audioNormalization.collectAsState()
    val eqPreset by vm.equalizerPreset.collectAsState()
    val crossfade by vm.crossfadeSeconds.collectAsState()
    Group {
        val preset = EqPreset.fromLabel(eqPreset)
        ChoiceSetting("Equalizer", eqDescription(preset), EqPreset.entries.map { it.label }, EqPreset.entries.indexOf(preset)) { vm.setEqualizerPreset(EqPreset.entries[it].label) }
        Divider()
        ChoiceSetting("Crossfade", crossfadeDescription(crossfade), CROSSFADE_CHOICES.map { if (it == 0) "Off" else "${it}s" }, CROSSFADE_CHOICES.indexOf(crossfade).coerceAtLeast(0)) { vm.setCrossfadeSeconds(CROSSFADE_CHOICES[it]) }
        Divider()
        ToggleSetting("Loudness boost", "Lifts quieter songs by about 3 dB. Most music is mastered loud already, so leave this off for the cleanest sound.", normalization, onChange = vm::setAudioNormalization)
    }
}

@Composable
private fun LyricsSettings() {
    val meta by PlayerController.meta.collectAsState()
    Group {
        ChoiceSetting("Text size", "How big the lyrics are in the full-window view.", LyricsSize.entries.map { it.label }, DesktopSettings.lyricsSize.ordinal) { DesktopSettings.lyricsSize = LyricsSize.entries[it] }
        Divider()
        ToggleSetting("Soften the other lines", "Lines before and after the one being sung blur a little, the further away the softer — Apple Music's depth. Scrolling the lyrics brings them all into focus.", DesktopSettings.lyricsBlur) { DesktopSettings.lyricsBlur = it }
        Divider()
        ActionSetting("Open the lyrics", "Also the lyrics button in the player bar, or ${cmd}L. Click a line to jump there.", "Show lyrics", enabled = meta.currentSong != null) { DesktopUi.toggleLyrics() }
    }
    Spacer(Modifier.height(20.dp))
    Text("PREVIEW", style = DType.label, modifier = Modifier.padding(bottom = 8.dp))
    // The setting as it'll look.
    Column(Modifier.fillMaxWidth().desktopGlass(RoundedCornerShape(10.dp), tint = Color.White.copy(alpha = 0.04f)).padding(horizontal = 24.dp, vertical = 18.dp)) {
        val size = DesktopSettings.lyricsSize.fontSize
        val style = TextStyle(fontSize = size, lineHeight = size * 1.18f, fontWeight = FontWeight.ExtraBold, color = Color.White)
        listOf("A line already sung" to 0.42f, "The line being sung" to 1f, "The next line, coming up" to 0.3f).forEachIndexed { i, (text, alpha) ->
            val soft = DesktopSettings.lyricsBlur && i != 1
            Text(text, style = style.copy(color = Color.White.copy(alpha = alpha)), modifier = Modifier.padding(vertical = 6.dp).then(if (soft) Modifier.blur(1.4.dp, BlurredEdgeTreatment.Unbounded) else Modifier))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettings() {
    val theme: ThemeCustomizeViewModel = composeViewModel { ThemeCustomizeViewModel() }
    val accentName by theme.accentName.collectAsState()
    Group {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Text("Accent colour", style = DType.itemTitleStrong)
            Text("The play button, what's playing, and selected things.", style = DType.small, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentPalette.forEach { option -> AccentSwatch(option.name, option.color, option.name == accentName) { theme.setAccent(option.name) } }
            }
        }
        Divider()
        ChoiceSetting(
            "Window background",
            "What the glass panels sit on. The lyrics view always glows in the song's colours.",
            Backdrop.entries.map { it.label }, DesktopSettings.backdrop.ordinal,
        ) { DesktopSettings.backdrop = Backdrop.entries[it] }
        Divider()
        ToggleSetting("Compact sidebar", "Just icons down the left, for more room — however wide the window.", DesktopSettings.compactSidebar) { DesktopSettings.compactSidebar = it }
    }
}

@Composable
private fun WindowSettings() {
    val meta by PlayerController.meta.collectAsState()
    val place = if (System.getProperty("os.name").lowercase().contains("mac")) "menu bar" else "notification area"
    var atLogin by remember { mutableStateOf(LaunchAtLogin.isEnabled()) }
    val tray = DesktopSettings.trayIcon
    Group {
        ToggleSetting("Show in the $place", "A Daydreamin icon up there with play/pause, next and previous, wherever you are.", tray) { DesktopSettings.trayIcon = it }
        Divider()
        ToggleSetting(
            "Keep playing when the window is closed",
            "Closing the window leaves the music on; the $place icon brings Daydreamin back. Off: closing the window quits." + if (!tray) " Needs the $place icon." else "",
            DesktopSettings.keepPlayingWhenClosed && tray,
            enabled = tray,
        ) { DesktopSettings.keepPlayingWhenClosed = it }
        Divider()
        ToggleSetting(
            "Song notifications",
            "A notification with each new song while Daydreamin isn't in front." + if (!tray) " Needs the $place icon." else "",
            DesktopSettings.songNotifications && tray,
            enabled = tray,
        ) { DesktopSettings.songNotifications = it }
        Divider()
        ToggleSetting(
            "Open Daydreamin when you sign in",
            if (LaunchAtLogin.available) "Starts with your computer, ready to play." else "Available in the installed app.",
            atLogin,
            enabled = LaunchAtLogin.available,
        ) { if (LaunchAtLogin.setEnabled(it)) atLogin = LaunchAtLogin.isEnabled() }
        Divider()
        ActionSetting("Mini player", "A small player that stays on top of your other windows, in place of this one.", "Open mini player", enabled = meta.currentSong != null) { DesktopUi.miniPlayer = true }
    }
}

@Composable
private fun StorageSettings() {
    val vm: SettingsViewModel = composeViewModel { SettingsViewModel() }
    val downloadLiked by vm.downloadLiked.collectAsState()
    val storage by vm.storage.collectAsState()
    val downloads by Downloads.items.collectAsState()
    LaunchedEffect(Unit) { vm.refreshStorage() }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    Group {
        ToggleSetting("Download liked songs", "Off: songs download only when you choose Download. On: every song you like downloads by itself.", downloadLiked, onChange = vm::setDownloadLiked)
        Divider()
        val done = downloads.values.count { it.status == DownloadStatus.DONE }
        val bytes = remember(downloads.size, done) { Downloads.bytesUsed() }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 24.dp)) {
                Text("Downloads", style = DType.itemTitleStrong)
                Text(if (done == 0) "Nothing downloaded yet." else "$done ${if (done == 1) "song" else "songs"}, ${formatBytes(bytes)} on this computer.", style = DType.small, modifier = Modifier.padding(top = 2.dp))
            }
            DButton("Show folder", { runCatching { java.awt.Desktop.getDesktop().open(Downloads.folder) } })
            Spacer(Modifier.width(8.dp))
            DButton("Remove all", { confirmRemove = true }, modifier = Modifier.dim(done > 0))
        }
        Divider()
        ActionSetting("Artwork cache", (storage?.let { "${formatBytes(it.artworkBytes)} of cover images. " } ?: "Cover images. ") + "They download again as you browse.", "Clear") { confirmClear = true }
    }
    if (confirmRemove && downloads.isNotEmpty()) ConfirmDialog(
        title = "Remove all downloads?",
        body = "Downloaded songs will need a connection to play again. Liked songs stop downloading too; you can turn that back on here.",
        confirm = "Remove all",
        onDismiss = { confirmRemove = false },
        onConfirm = { confirmRemove = false; vm.removeAllDownloads() },
    )
    if (confirmClear) ConfirmDialog(
        title = "Clear artwork cache?",
        body = "Cover images will download again as you browse. Your music, library and offline songs aren't affected.",
        confirm = "Clear",
        onDismiss = { confirmClear = false },
        onConfirm = { confirmClear = false; vm.clearArtworkCache(); Toaster.show("Artwork cache cleared") },
    )
}

@Composable
private fun LibrarySettings() {
    val vm: SettingsViewModel = composeViewModel { SettingsViewModel() }
    val backupStatus by vm.backupStatus.collectAsState()
    val history by AppGraph.prefs.history.collectAsState(initial = emptyList())
    val searches by AppGraph.prefs.searchHistory.collectAsState(initial = emptyList())
    val exportLauncher = rememberPicker(PickKind.SAVE_BACKUP) { file -> if (file != null) vm.exportLibrary(file) }
    val importLauncher = rememberPicker(PickKind.OPEN_BACKUP) { file -> if (file != null) vm.importLibrary(file) }
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    Group {
        ActionSetting("Export library", "Save your liked songs, playlists and history to a file you keep — or take them to another computer or your phone.", "Export…") {
            exportLauncher.launch("daydreamin-library-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json")
        }
        Divider()
        ActionSetting("Import library", "Add songs and playlists from a Daydreamin backup. Importing only adds — nothing here is removed.", "Import…") { importLauncher.launch("") }
        backupStatus?.let { Text(it, style = DType.small.copy(color = Color.White.copy(alpha = 0.8f)), modifier = Modifier.padding(bottom = 12.dp)) }
    }
    Spacer(Modifier.height(16.dp))
    Group {
        ActionSetting("Listening history", if (history.isEmpty()) "Nothing played yet." else "${history.size} songs in Recently played. Home's mixes learn from what you play.", "Clear…", enabled = history.isNotEmpty()) { confirm = "history" }
        Divider()
        ActionSetting("Search history", if (searches.isEmpty()) "No recent searches." else "${searches.size} recent searches, shown when the search box is empty.", "Clear…", enabled = searches.isNotEmpty()) { confirm = "searches" }
    }
    when (confirm) {
        "history" -> ConfirmDialog(
            "Clear listening history?", "Recently played empties. Your liked songs, playlists and downloads stay.", "Clear",
            onDismiss = { confirm = null },
            onConfirm = { confirm = null; scope.launch { AppGraph.prefs.clearHistory(); Toaster.show("Listening history cleared") } },
        )
        "searches" -> ConfirmDialog(
            "Clear search history?", "Your recent searches are forgotten.", "Clear",
            onDismiss = { confirm = null },
            onConfirm = { confirm = null; scope.launch { AppGraph.prefs.clearSearchHistory(); Toaster.show("Search history cleared") } },
        )
    }
}

@Composable
private fun ShortcutList() {
    val shortcuts = listOf(
        "Play / pause" to "Space",
        "Next song" to "$cmd→",
        "Previous song" to "$cmd←",
        "Forward 10 seconds" to "Shift →",
        "Back 10 seconds" to "Shift ←",
        "Search" to "${cmd}K  or  ${cmd}F",
        "Lyrics, full window" to "${cmd}L",
        "Go back" to "$cmd[",
        "Go forward" to "$cmd]",
        "Settings" to "$cmd,",
        "Close the lyrics, a menu or a dialog" to "Esc",
    )
    Group {
        shortcuts.forEachIndexed { i, (what, keys) ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(what, style = DType.itemTitle, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(keys, style = DType.small.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
    Text("Your keyboard's media keys (play/pause, next, previous) work too, even when Daydreamin isn't in front.", style = DType.small, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun AboutSettings() {
    val vm: SettingsViewModel = composeViewModel { SettingsViewModel() }
    val extractor by vm.extractorState.collectAsState()
    Column(Modifier.desktopGlass(RoundedCornerShape(10.dp), tint = Color.White.copy(alpha = 0.03f)).padding(vertical = 8.dp)) {
        AboutCard(); SupportRow(); UpiRow(); CreatorRow(); UpdateRow(); ExtractorRow(extractor, onCheck = vm::testExtractor)
    }
}

/** Settings that belong together, on one card. */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().desktopGlass(RoundedCornerShape(10.dp), tint = Color.White.copy(alpha = 0.03f)).padding(horizontal = 18.dp), content = content)
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DColors.divider))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceSetting(title: String, body: String, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(title, style = DType.itemTitleStrong)
        Text(body, style = DType.small, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label -> ViewChip(label, i == selected) { onSelect(i) } }
        }
    }
}

@Composable
private fun ToggleSetting(title: String, body: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .desktopClickable(RoundedCornerShape(8.dp), highlight = false, enabled = enabled) { onChange(!checked) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 24.dp)) {
            Text(title, style = DType.itemTitleStrong.copy(color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)))
            Text(body, style = DType.small, modifier = Modifier.padding(top = 2.dp))
        }
        Box(Modifier.dim(enabled)) { GlassSwitch(checked) }
    }
}

@Composable
private fun ActionSetting(title: String, body: String, action: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 24.dp)) {
            Text(title, style = DType.itemTitleStrong)
            Text(body, style = DType.small, modifier = Modifier.padding(top = 2.dp))
        }
        DButton(action, { if (enabled) onClick() }, modifier = Modifier.dim(enabled))
    }
}

/** Faded when it can't be used right now. */
private fun Modifier.dim(enabled: Boolean) = if (enabled) this else this.alpha(0.4f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccentSwatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    com.daydreamin.app.desktop.components.WithTooltip(name) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .border(if (selected) 2.dp else if (hovered) 1.dp else 0.dp, Color.White.copy(alpha = if (selected) 1f else 0.6f), CircleShape)
                .hoverable(source)
                .pointerHoverIcon(PointerIcon.Hand)
                .combinedClickable(interactionSource = source, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Rounded.Check, contentDescription = "$name, chosen", tint = Color.Black, modifier = Modifier.size(20.dp)) }
    }
}
