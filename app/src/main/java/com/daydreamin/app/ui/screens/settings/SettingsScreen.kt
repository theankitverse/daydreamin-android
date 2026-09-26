package com.daydreamin.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.daydreamin.app.BuildConfig
import com.daydreamin.app.R
import com.daydreamin.app.player.EqPreset
import com.daydreamin.app.ui.components.Creator
import com.daydreamin.app.ui.components.Toaster
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Motion
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.Space
import com.daydreamin.app.ui.theme.glass
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

private val TopBarHeight = 56.dp
private val Muted = Color.White.copy(alpha = 0.55f)
/** Row text starts here (gutter + icon + gap) — hairlines and follow-on lines align to it. */
private val TextStart = 64.dp

/**
 * Settings: only things that actually do something, grouped into Sound, Offline, Your library and
 * About. Rows are plain type on black; glass is kept for the controls themselves — switches, the
 * equalizer selector, action pills, the confirm dialog.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = composeViewModel()
    val context = LocalContext.current
    val normalization by vm.audioNormalization.collectAsState()
    val wifiOnly by vm.downloadWifiOnly.collectAsState()
    val eqPreset by vm.equalizerPreset.collectAsState()
    val extractor by vm.extractorState.collectAsState()
    val backupStatus by vm.backupStatus.collectAsState()
    val storage by vm.storage.collectAsState()
    LaunchedEffect(Unit) { vm.refreshStorage(context) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportLibrary(context.contentResolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importLibrary(context.contentResolver, uri)
    }

    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollY = { if (listState.firstVisibleItemIndex > 0) 2_000f else listState.firstVisibleItemScrollOffset.toFloat() }
    val haze = remember { HazeState() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(BgBase)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = statusTop + TopBarHeight, bottom = navBottom + 40.dp),
        ) {
            item {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier.padding(start = Space.gutter, top = Space.xs, bottom = Space.xs),
                )
            }

            // ---- Sound
            item { Section("Sound") }
            item {
                val preset = EqPreset.fromLabel(eqPreset)
                Column(Modifier.padding(horizontal = Space.gutter, vertical = 10.dp)) {
                    RowHeader(Icons.Rounded.GraphicEq, "Equalizer", eqDescription(preset), animateBody = true)
                    Spacer(Modifier.height(12.dp))
                    EqSelector(selected = preset, onSelect = { vm.setEqualizerPreset(it.label) })
                }
            }
            item { Hairline() }
            item {
                ToggleRow(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    title = "Loudness boost",
                    body = "Lifts overall volume by about 6 dB without clipping. It doesn’t even out loudness between songs — YouTube doesn’t provide the data for that.",
                    checked = normalization,
                    onChange = vm::setAudioNormalization,
                )
            }

            // ---- Offline
            item { Section("Offline") }
            item {
                ToggleRow(
                    icon = Icons.Rounded.Wifi,
                    title = "Save offline on Wi-Fi only",
                    body = "Liked songs, and the start of songs you’re likely to tap, are saved so they play instantly and work offline. Turn off to also save over mobile data.",
                    checked = wifiOnly,
                    onChange = vm::setDownloadWifiOnly,
                )
            }
            item { Hairline() }
            item { StorageRow(storage) }
            item { Hairline() }
            item {
                ActionRow(
                    icon = Icons.Rounded.Image,
                    title = "Clear artwork cache",
                    body = (storage?.let { "${formatBytes(it.artworkBytes)} of cover images. " } ?: "Cover images. ") + "They download again as you browse.",
                    action = "Clear",
                    onClick = { confirmClear = true },
                )
            }

            // ---- Library
            item { Section("Your library") }
            item {
                ActionRow(
                    icon = Icons.Rounded.FileUpload,
                    title = "Export library",
                    body = "Save your liked songs, playlists and history to a file you keep.",
                    action = "Export",
                    onClick = { exportLauncher.launch("daydreamin-library-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json") },
                )
            }
            item { Hairline() }
            item {
                ActionRow(
                    icon = Icons.Rounded.FileDownload,
                    title = "Import library",
                    body = "Add songs and playlists from a Daydreamin backup. Importing only adds — nothing here is removed.",
                    action = "Import",
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }
            item {
                // The last export/import result stays readable here (not only as a passing note).
                AnimatedContent(targetState = backupStatus, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "backupStatus") { status ->
                    if (status != null) {
                        Text(status, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(start = TextStart, end = Space.gutter, bottom = 8.dp))
                    } else {
                        Spacer(Modifier.height(0.dp))
                    }
                }
            }

            // ---- About
            item { Section("About") }
            item { AboutCard() }
            item { CreatorRow() }
            item { ExtractorRow(extractor, onCheck = vm::testExtractor) }
        }

        TopBar(haze = haze, statusTop = statusTop, scrollY = scrollY, onBack = onBack)
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear artwork cache?",
            body = "Cover images will download again as you browse. Your music, library and offline songs aren’t affected.",
            confirm = "Clear",
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                vm.clearArtworkCache(context)
                Toaster.show("Artwork cache cleared")
            },
        )
    }
}

// ---------------------------------------------------------------- rows

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = Space.gutter, top = 34.dp, bottom = 6.dp),
    )
}

@Composable
private fun Hairline() {
    Box(Modifier.padding(start = TextStart, end = Space.gutter).fillMaxWidth().height(0.7.dp).background(Color.White.copy(alpha = 0.07f)))
}

@Composable
private fun RowHeader(icon: ImageVector, title: String, body: String?, animateBody: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        RowIcon(icon)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            when {
                body == null -> {}
                // Only a body that actually changes (the equalizer's) pays for a transition.
                animateBody -> AnimatedContent(targetState = body, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "rowBody") { b ->
                    Text(b, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                else -> Text(body, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Box(Modifier.size(30.dp).glass(RoundedCornerShape(9.dp), Glass.Clear), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(17.dp))
    }
}

/** A setting you turn on or off. The whole row is the touch target, not just the switch. */
@Composable
private fun ToggleRow(icon: ImageVector, title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) { stateDescription = if (checked) "On" else "Off" }
            .pressable(role = Role.Switch, onClick = { onChange(!checked) })
            .padding(horizontal = Space.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { RowHeader(icon, title, body) }
        Spacer(Modifier.width(16.dp))
        GlassSwitch(checked)
    }
}

/** Off: a glass track with a pale thumb. On: the track fills with light and the thumb springs across. */
@Composable
private fun GlassSwitch(checked: Boolean) {
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.62f, stiffness = 520f), label = "thumbX")
    val fill by animateFloatAsState(if (checked) 1f else 0f, Motion.settle(), label = "switchFill")
    val thumb by animateColorAsState(if (checked) Color.Black else Color.White.copy(alpha = 0.9f), Motion.settle(), label = "thumbColor")
    Box(Modifier.size(width = 50.dp, height = 30.dp).glass(Radius.pill, Glass.Regular, tint = Color.White.copy(alpha = 0.9f * fill))) {
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 2.dp.roundToPx()) }
                .size(26.dp)
                // A painted contact shadow rather than a real elevation shadow: a real one here
                // tripled the slow frames while scrolling (measured), for a 3dp softness.
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(0.7f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent, center = center.copy(y = center.y + 1.dp.toPx()), radius = size.minDimension / 2 + 2.dp.toPx()),
                        radius = size.minDimension / 2 + 2.dp.toPx(),
                        center = center.copy(y = center.y + 1.dp.toPx()),
                    )
                }
                .clip(CircleShape)
                .background(thumb),
        )
    }
}

/** Four presets in one glass selector; the lens springs to the chosen one. */
@Composable
private fun EqSelector(selected: EqPreset, onSelect: (EqPreset) -> Unit) {
    val presets = EqPreset.entries
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = TextStart - Space.gutter).height(42.dp).glass(Radius.pill, Glass.Clear).padding(4.dp)) {
        val w = maxWidth / presets.size
        val x by animateDpAsState(w * selected.ordinal, spring(dampingRatio = 0.75f, stiffness = 480f), label = "eqLens")
        Box(Modifier.offset { IntOffset(x.roundToPx(), 0) }.width(w).fillMaxHeight().clip(Radius.pill).background(Color.White.copy(alpha = 0.95f)))
        Row(Modifier.fillMaxSize()) {
            presets.forEach { p ->
                val isSel = p == selected
                val color by animateColorAsState(if (isSel) Color.Black else Color.White.copy(alpha = 0.75f), Motion.settle(), label = "eqText")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = p.label }
                        .pressable(role = Role.RadioButton, onClick = { onSelect(p) }),
                    contentAlignment = Alignment.Center,
                ) { Text(p.label.removeSuffix(" Boost"), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1) }
            }
        }
    }
}

private fun eqDescription(p: EqPreset) = when (p) {
    EqPreset.OFF -> "Flat — the song as it was mixed."
    EqPreset.BASS_BOOST -> "More weight in the low end."
    EqPreset.VOCAL_BOOST -> "Voices forward, a little clearer."
    EqPreset.TREBLE_BOOST -> "More air and detail up top."
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, body: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).pressable(onClick = onClick).padding(horizontal = Space.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { RowHeader(icon, title, body) }
        Spacer(Modifier.width(12.dp))
        Text(action, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.glass(Radius.pill, Glass.Clear).padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

/** How much offline audio is saved, against the fixed cap the cache keeps itself under. */
@Composable
private fun StorageRow(storage: SettingsViewModel.Storage?) {
    Column(Modifier.fillMaxWidth().padding(start = TextStart, end = Space.gutter, top = 14.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Offline audio", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
            Text(
                storage?.let { "${formatBytes(it.audioBytes)} of ${formatBytes(it.audioCapBytes)}" } ?: "—",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = Muted,
            )
        }
        Spacer(Modifier.height(10.dp))
        val fraction = storage?.let { (it.audioBytes.toFloat() / it.audioCapBytes).coerceIn(0f, 1f) } ?: 0f
        val shown by animateFloatAsState(fraction, spring(dampingRatio = 0.9f, stiffness = 120f), label = "storageBar")
        Box(Modifier.fillMaxWidth().height(6.dp).clip(Radius.pill).background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(shown.coerceAtLeast(0.004f)).fillMaxHeight().clip(Radius.pill).background(Color.White.copy(alpha = 0.85f)))
        }
        Spacer(Modifier.height(8.dp))
        Text("When it fills up, the songs you haven’t played in longest make room automatically.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun AboutCard() {
    Row(
        Modifier.padding(horizontal = Space.gutter).fillMaxWidth().glass(Radius.panelShape, Glass.Clear).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(painterResource(R.drawable.splash_logo), contentDescription = null, modifier = Modifier.size(48.dp))
        Column(Modifier.padding(start = 14.dp)) {
            Text("Daydreamin", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(6.dp))
            Text(
                "Music streamed straight from YouTube and found on this device — no account, and no server of ours in between.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

/** The maker's credit — the whole row opens his Instagram profile. */
@Composable
private fun CreatorRow() {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "Made by ${Creator.NAME}. Open @${Creator.INSTAGRAM_HANDLE} on Instagram" }
            .pressable(role = Role.Button, onClick = { Creator.openInstagram(context) })
            .padding(horizontal = Space.gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Monogram in the brand's violet-to-pink, like the logo.
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF9B6BFF), Color(0xFFF09AD8)))),
            contentAlignment = Alignment.Center,
        ) { Text(Creator.NAME.take(1), style = MaterialTheme.typography.labelLarge, color = Color.White) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Made by ${Creator.NAME}", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("@${Creator.INSTAGRAM_HANDLE} on Instagram", style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier.glass(Radius.pill, Glass.Clear).padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Follow", style = MaterialTheme.typography.labelLarge, color = Color.White)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
        }
    }
}

/** A real check: runs a tiny on-device YouTube search, which fails when YouTube changes its internals. */
@Composable
private fun ExtractorRow(state: ExtractorState, onCheck: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.align(Alignment.Top)) { RowIcon(Icons.Rounded.Sensors) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Playback engine", style = MaterialTheme.typography.titleMedium, color = Color.White)
            AnimatedContent(targetState = state, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "extractor") { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (s) {
                        ExtractorState.OK -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(15.dp))
                        ExtractorState.FAILED -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFFF6B61), modifier = Modifier.size(15.dp))
                        else -> {}
                    }
                    if (s == ExtractorState.OK || s == ExtractorState.FAILED) Spacer(Modifier.width(6.dp))
                    Text(
                        when (s) {
                            ExtractorState.UNKNOWN -> "Check that songs can still be found and played."
                            ExtractorState.CHECKING -> "Checking…"
                            ExtractorState.OK -> "Working normally."
                            ExtractorState.FAILED -> "Not responding — you may be offline, or YouTube changed something."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (state == ExtractorState.CHECKING) "…" else "Check",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .then(if (state != ExtractorState.CHECKING) Modifier.pressable(onClick = onCheck) else Modifier)
                .glass(Radius.pill, Glass.Clear)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

// ---------------------------------------------------------------- chrome

@Composable
private fun TopBar(haze: HazeState, statusTop: Dp, scrollY: () -> Float, onBack: () -> Unit) {
    val density = LocalDensity.current
    val style = remember { HazeStyle(backgroundColor = BgBase, tints = listOf(HazeTint(Color.Black.copy(alpha = 0.58f))), blurRadius = 30.dp, noiseFactor = 0.04f) }
    val solidUntil = with(density) { (statusTop + TopBarHeight - 6.dp).toPx() }
    val fadeEnd = with(density) { (statusTop + TopBarHeight + 16.dp).toPx() }
    val fade = with(density) { 60.dp.toPx() }
    val titleStart = with(density) { 30.dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(statusTop + TopBarHeight + 16.dp)
            .hazeEffect(haze, style) {
                progressive = HazeProgressive.verticalGradient(startY = solidUntil, startIntensity = 1f, endY = fadeEnd, endIntensity = 0f, preferPerformance = true)
                alpha = (scrollY() / fade).coerceIn(0f, 1f)
            },
    ) {
        Box(
            Modifier.padding(start = 12.dp, top = statusTop + 8.dp).size(40.dp).pressable(onClick = onBack).glass(Radius.pill, Glass.Clear),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(26.dp)) }
        Text(
            "Settings",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusTop + 17.dp)
                // Takes over from the large title as it scrolls away beneath the bar.
                .graphicsLayer { alpha = ((scrollY() - titleStart) / titleStart).coerceIn(0f, 1f) },
        )
    }
}

/** The app's glass confirmation card, for anything that removes something. */
@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), Glass.Regular, tint = Color(0xF2141418)).padding(22.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.65f))
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.pressable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 10.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    confirm,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.pressable(onClick = onConfirm).glass(Radius.pill, Glass.Clear, tint = Color(0xFFFF453A).copy(alpha = 0.55f)).padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024 -> String.format(Locale.US, "%.1f GB", mb / 1024)
        mb >= 10 -> String.format(Locale.US, "%.0f MB", mb)
        mb >= 0.1 -> String.format(Locale.US, "%.1f MB", mb)
        bytes > 0 -> "< 0.1 MB"
        else -> "0 MB"
    }
}
