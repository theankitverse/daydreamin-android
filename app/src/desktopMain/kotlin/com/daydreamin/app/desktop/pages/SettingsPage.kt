package com.daydreamin.app.desktop.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.daydreamin.app.data.download.DownloadStatus
import com.daydreamin.app.data.download.Downloads
import com.daydreamin.app.desktop.components.DButton
import com.daydreamin.app.desktop.components.LocalAccent
import com.daydreamin.app.desktop.components.desktopClickable
import com.daydreamin.app.desktop.components.desktopGlass
import com.daydreamin.app.desktop.components.rememberHover
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DSpace
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.player.EqPreset
import com.daydreamin.app.platform.PickKind
import com.daydreamin.app.platform.rememberPicker
import com.daydreamin.app.ui.components.Toaster
import com.daydreamin.app.ui.screens.settings.AboutCard
import com.daydreamin.app.ui.screens.settings.CROSSFADE_CHOICES
import com.daydreamin.app.ui.screens.settings.ConfirmDialog
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

/** The parts of Settings, in the order they appear; the list on the left jumps to each. */
enum class SettingsSection(val label: String, val icon: ImageVector) {
    PLAYBACK("Playback", Icons.Rounded.GraphicEq),
    APPEARANCE("Appearance", Icons.Rounded.Palette),
    OFFLINE("Offline", Icons.Rounded.DownloadForOffline),
    LIBRARY("Your library", Icons.Rounded.LibraryMusic),
    ABOUT("About", Icons.Rounded.Info),
}

/**
 * Settings, laid out for a window: the sections on the left, everything on the right in one
 * scrolling column (scrolling highlights where you are; clicking a section jumps to it). The same
 * settings as the phone app, minus what a computer doesn't need (mobile data, phone storage).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(start: SettingsSection? = null) {
    val vm: SettingsViewModel = composeViewModel { SettingsViewModel() }
    val theme: ThemeCustomizeViewModel = composeViewModel { ThemeCustomizeViewModel() }
    val normalization by vm.audioNormalization.collectAsState()
    val eqPreset by vm.equalizerPreset.collectAsState()
    val crossfade by vm.crossfadeSeconds.collectAsState()
    val downloadLiked by vm.downloadLiked.collectAsState()
    val extractor by vm.extractorState.collectAsState()
    val backupStatus by vm.backupStatus.collectAsState()
    val storage by vm.storage.collectAsState()
    val accentName by theme.accentName.collectAsState()
    val downloads by Downloads.items.collectAsState()
    LaunchedEffect(Unit) { vm.refreshStorage() }
    var confirmRemoveDownloads by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val exportLauncher = rememberPicker(PickKind.SAVE_BACKUP) { file -> if (file != null) vm.exportLibrary(file) }
    val importLauncher = rememberPicker(PickKind.OPEN_BACKUP) { file -> if (file != null) vm.importLibrary(file) }

    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Each section's heading is item "s-<name>"; its index is where the list jumps.
    val sectionIndex = remember { mutableMapOf<SettingsSection, Int>() }
    val current by remember { derivedStateOf { sectionIndex.entries.filter { it.value <= list.firstVisibleItemIndex + 1 }.maxByOrNull { it.value }?.key ?: SettingsSection.PLAYBACK } }
    LaunchedEffect(start) { start?.let { s -> sectionIndex[s]?.let { list.scrollToItem(it) } } }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(220.dp).fillMaxHeight().padding(start = 12.dp, top = 24.dp, end = 8.dp)) {
            Text("Settings", style = DType.pageTitle, modifier = Modifier.padding(start = 12.dp, bottom = 16.dp))
            SettingsSection.entries.forEach { s ->
                val selected = current == s
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) DColors.selected else Color.Transparent)
                        .desktopClickable(RoundedCornerShape(6.dp)) { scope.launch { sectionIndex[s]?.let { list.animateScrollToItem(it) } } }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(s.icon, contentDescription = null, tint = if (selected) Color.White else DColors.iconIdle, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(s.label, style = DType.itemTitle.copy(color = if (selected) Color.White else Color.White.copy(alpha = 0.75f)))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = DSpace.pagePad, top = 24.dp, bottom = 120.dp)) {
                var index = 0
                fun section(s: SettingsSection) {
                    sectionIndex[s] = index
                    item(key = "s-${s.name}") { SectionTitle(s.label) }
                    index++
                }
                fun row(key: String, content: @Composable () -> Unit) { item(key = key) { Box(Modifier.widthIn(max = 760.dp)) { content() } }; index++ }

                section(SettingsSection.PLAYBACK)
                row("eq") {
                    val preset = EqPreset.fromLabel(eqPreset)
                    SettingRow("Equalizer", eqDescription(preset)) {
                        Choices(EqPreset.entries.map { it.label }, EqPreset.entries.indexOf(preset)) { vm.setEqualizerPreset(EqPreset.entries[it].label) }
                    }
                }
                row("crossfade") {
                    SettingRow("Crossfade", crossfadeDescription(crossfade)) {
                        Choices(CROSSFADE_CHOICES.map { if (it == 0) "Off" else "${it}s" }, CROSSFADE_CHOICES.indexOf(crossfade).coerceAtLeast(0)) { vm.setCrossfadeSeconds(CROSSFADE_CHOICES[it]) }
                    }
                }
                row("loudness") {
                    ToggleSetting("Loudness boost", "Lifts quieter songs by about 3 dB. Most music is mastered loud already, so leave this off for the cleanest sound.", normalization, vm::setAudioNormalization)
                }

                section(SettingsSection.APPEARANCE)
                row("accent") {
                    SettingRow("Accent colour", "Used for the play button, what's playing, and selected things.") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AccentPalette.forEach { option -> AccentSwatch(option.name, option.color, option.name == accentName) { theme.setAccent(option.name) } }
                        }
                    }
                }

                section(SettingsSection.OFFLINE)
                row("download-liked") {
                    ToggleSetting("Download liked songs", "Off: songs download only when you choose Download. On: every song you like downloads by itself.", downloadLiked, vm::setDownloadLiked)
                }
                row("downloads") {
                    val done = downloads.values.count { it.status == DownloadStatus.DONE }
                    val bytes = remember(downloads.size, done) { Downloads.bytesUsed() }
                    ActionSetting("Downloads", if (done == 0) "Nothing downloaded yet." else "$done ${if (done == 1) "song" else "songs"}, ${formatBytes(bytes)} on this computer.", "Remove all") { if (downloads.isNotEmpty()) confirmRemoveDownloads = true }
                }
                row("artwork") {
                    ActionSetting("Artwork cache", (storage?.let { "${formatBytes(it.artworkBytes)} of cover images. " } ?: "Cover images. ") + "They download again as you browse.", "Clear") { confirmClear = true }
                }

                section(SettingsSection.LIBRARY)
                row("export") {
                    ActionSetting("Export library", "Save your liked songs, playlists and history to a file you keep.", "Export…") {
                        exportLauncher.launch("daydreamin-library-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json")
                    }
                }
                row("import") {
                    ActionSetting("Import library", "Add songs and playlists from a Daydreamin backup. Importing only adds — nothing here is removed.", "Import…") { importLauncher.launch("") }
                }
                row("backup-status") { backupStatus?.let { Text(it, style = DType.small.copy(color = Color.White.copy(alpha = 0.8f)), modifier = Modifier.padding(bottom = 8.dp)) } }

                section(SettingsSection.ABOUT)
                row("about") { Column(Modifier.desktopGlass(RoundedCornerShape(10.dp), tint = Color.White.copy(alpha = 0.03f)).padding(vertical = 8.dp)) { AboutCard(); SupportRow(); UpiRow(); CreatorRow(); UpdateRow(); ExtractorRow(extractor, onCheck = vm::testExtractor) } }
            }
            VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
        }
    }

    if (confirmRemoveDownloads) {
        ConfirmDialog(
            title = "Remove all downloads?",
            body = "Downloaded songs will need a connection to play again. Liked songs stop downloading too; you can turn that back on here.",
            confirm = "Remove all",
            onDismiss = { confirmRemoveDownloads = false },
            onConfirm = { confirmRemoveDownloads = false; vm.removeAllDownloads() },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear artwork cache?",
            body = "Cover images will download again as you browse. Your music, library and offline songs aren’t affected.",
            confirm = "Clear",
            onDismiss = { confirmClear = false },
            onConfirm = { confirmClear = false; vm.clearArtworkCache(); Toaster.show("Artwork cache cleared") },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = DType.section, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

/** A setting: its name and what it does on the left, the control under it. */
@Composable
private fun SettingRow(title: String, body: String, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, style = DType.itemTitleStrong)
        Text(body, style = DType.small, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        control()
    }
}

@Composable
private fun ToggleSetting(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).desktopClickable(RoundedCornerShape(8.dp)) { onChange(!checked) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 24.dp)) {
            Text(title, style = DType.itemTitleStrong)
            Text(body, style = DType.small, modifier = Modifier.padding(top = 2.dp))
        }
        GlassSwitch(checked)
    }
}

@Composable
private fun ActionSetting(title: String, body: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 24.dp)) {
            Text(title, style = DType.itemTitleStrong)
            Text(body, style = DType.small, modifier = Modifier.padding(top = 2.dp))
        }
        DButton(action, onClick)
    }
}

/** A row of mutually exclusive choices (the equalizer, crossfade). */
@Composable
private fun Choices(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label -> ViewChip(label, i == selected) { onSelect(i) } }
    }
}

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
