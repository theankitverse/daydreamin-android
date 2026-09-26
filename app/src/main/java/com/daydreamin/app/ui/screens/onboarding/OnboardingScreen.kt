package com.daydreamin.app.ui.screens.onboarding

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daydreamin.app.DaydreaminApp
import com.daydreamin.app.R
import com.daydreamin.app.data.prefs.AutoBackup
import com.daydreamin.app.data.prefs.BackupFormatException
import com.daydreamin.app.data.prefs.restoreLibrary
import com.daydreamin.app.ui.components.UserAvatar
import com.daydreamin.app.ui.components.encodeAvatar
import com.daydreamin.app.ui.components.pressable
import com.daydreamin.app.ui.theme.AccentPalette
import com.daydreamin.app.ui.theme.BgBase
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.accentByName
import com.daydreamin.app.ui.theme.glass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class Step { WELCOME, YOU, THEME, DONE }

/**
 * First-run setup: welcome (or restore a backup), your name and photo, your accent colour, and a
 * last word on where your library is kept safe. Every step can be skipped; nothing here is
 * required to use the app. [onDone] runs once it's finished — the caller marks setup complete.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val prefs = DaydreaminApp.instance.prefs
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var name by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    var restoreNote by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    val accentName by prefs.accentName.collectAsState(initial = "Violet")
    val accent = accentByName(accentName)

    val restoreLauncher = rememberLauncherForActivityResult(OpenBackupDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        restoring = true
        scope.launch {
            restoreNote = try {
                val r = restoreLibrary(context, prefs, uri, keepBackingUpHere = true)
                val s = r.summary
                if (r.profileApplied) name = prefs.userName.first()
                step = if (r.profileApplied) Step.DONE else Step.YOU
                "Restored " + listOf(plural(s.likedAdded, "liked song"), plural(s.playlistsAdded, "playlist"), "${s.historyAdded} recently played").joinToString(", ") + "."
            } catch (e: BackupFormatException) {
                e.message
            } catch (e: Exception) {
                "Couldn’t read that file."
            }
            restoring = false
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val jpeg = encodeAvatar(context, uri) ?: return@launch
            prefs.setAvatar(jpeg)
            photo = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
        }
    }

    fun back() { step = Step.entries[(step.ordinal - 1).coerceAtLeast(0)] }
    BackHandler(enabled = step != Step.WELCOME) { back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBase)
            .drawBehind {
                // The chosen accent lights the room, so picking a colour is felt immediately.
                drawRect(Brush.radialGradient(0f to accent.copy(alpha = 0.34f), 1f to Color.Transparent, center = Offset(size.width * 0.15f, 0f), radius = size.width * 1.2f))
                drawRect(Brush.radialGradient(0f to accent.copy(alpha = 0.16f), 1f to Color.Transparent, center = Offset(size.width, size.height * 0.55f), radius = size.width * 0.9f))
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step != Step.WELCOME && step != Step.DONE) StepDots(step.ordinal - 1, 2, accent)
                Spacer(Modifier.weight(1f))
                if (step == Step.YOU || step == Step.THEME) {
                    Text(
                        "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.pressable(onClick = { focus.clearFocus(); step = Step.DONE }).padding(8.dp),
                    )
                }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(260, delayMillis = 60)) + slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 6 * dir }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 8 * dir })
                },
                label = "onboardingStep",
                modifier = Modifier.weight(1f),
            ) { s ->
                when (s) {
                    Step.WELCOME -> Welcome(
                        restoring = restoring,
                        note = restoreNote,
                        onStart = { step = Step.YOU },
                        onRestore = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    )
                    Step.YOU -> AboutYou(
                        name = name,
                        onName = { name = it.take(40) },
                        photo = photo,
                        onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onNext = {
                            focus.clearFocus()
                            scope.launch { if (name.isNotBlank()) prefs.setUserName(name) }
                            step = Step.THEME
                        },
                        accent = accent,
                    )
                    Step.THEME -> ThemeStep(
                        selected = accentName,
                        onSelect = { scope.launch { prefs.setAccentName(it) } },
                        onNext = { step = Step.DONE },
                        name = name,
                        photo = photo,
                    )
                    Step.DONE -> Done(name = name, photo = photo, note = restoreNote, onFinish = {
                        scope.launch { if (name.isNotBlank()) prefs.setUserName(name) }
                        onDone()
                    })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- steps

@Composable
private fun Welcome(restoring: Boolean, note: String?, onStart: () -> Unit, onRestore: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.splash_logo), contentDescription = null, modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(28.dp))
        Text("Welcome to\nDaydreamin", style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 40.sp), color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Music from everywhere, played beautifully. Let’s make it yours — it takes a few seconds.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1.2f))
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 14.dp))
        }
        PrimaryButton("Get started", onClick = onStart)
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            label = if (restoring) "Restoring…" else "Restore from a backup",
            icon = Icons.Rounded.Restore,
            onClick = { if (!restoring) onRestore() },
        )
        Text(
            if (AutoBackup.isSupported) "Used Daydreamin before? Your backup is in Download/Daydreamin." else "Used Daydreamin before? Pick the backup file you exported.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun AboutYou(
    name: String,
    onName: (String) -> Unit,
    photo: ImageBitmap?,
    onPickPhoto: () -> Unit,
    onNext: () -> Unit,
    accent: Color,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Title("What should we call you?", "Your name and photo stay on this phone.")
        Spacer(Modifier.height(36.dp))
        Box(Modifier.pressable(onClick = onPickPhoto).semantics { contentDescription = "Choose a profile photo" }) {
            UserAvatar(size = 132.dp, override = photo, overrideName = name)
            Box(
                Modifier.align(Alignment.BottomEnd).size(40.dp).clip(CircleShape).background(Color.White).border(3.dp, BgBase, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        Text(if (photo == null) "Add a photo" else "Change photo", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.pressable(onClick = onPickPhoto).padding(6.dp))
        Spacer(Modifier.height(28.dp))
        BasicTextField(
            value = name,
            onValueChange = onName,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.White, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().height(60.dp).glass(Radius.pill, Glass.Regular).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                    if (name.isEmpty()) Text("Your name", style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.35f))
                    inner()
                }
            },
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (name.isBlank()) "Continue without a name" else "Continue", onClick = onNext)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ThemeStep(selected: String, onSelect: (String) -> Unit, onNext: () -> Unit, name: String, photo: ImageBitmap?) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Title("Pick your colour", "It lights up what’s playing, your selections and the controls. Change it any time in Theme.")
        Spacer(Modifier.height(30.dp))
        // A little preview of what the colour touches.
        val accent = accentByName(selected)
        Row(
            Modifier.fillMaxWidth().glass(Radius.panelShape, Glass.Clear).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(size = 48.dp, override = photo, overrideName = name)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(if (name.isBlank()) "Good evening" else "Good evening, ${name.trim()}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Now playing", style = MaterialTheme.typography.bodySmall, color = accent)
            }
            Box(Modifier.size(width = 64.dp, height = 6.dp).clip(Radius.pill).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.size(width = 40.dp, height = 6.dp).clip(Radius.pill).background(accent))
            }
        }
        Spacer(Modifier.height(28.dp))
        val rows = AccentPalette.chunked(5)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { option ->
                    val isSel = option.name == selected
                    val ring by animateDpAsState(if (isSel) 3.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "swatchRing")
                    Box(
                        Modifier
                            .size(54.dp)
                            .semantics { contentDescription = option.name; this.selected = isSel }
                            .pressable(onClick = { onSelect(option.name) })
                            .border(ring, Color.White, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(option.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSel) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Text(selected, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", onClick = onNext)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Done(name: String, photo: ImageBitmap?, note: String?, onFinish: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.8f))
        UserAvatar(size = 112.dp, override = photo, overrideName = name)
        Spacer(Modifier.height(24.dp))
        Text(
            if (name.isBlank()) "You’re all set" else "You’re all set,\n${name.trim()}",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        if (note != null) {
            Spacer(Modifier.height(10.dp))
            Text(note, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        Fact(Icons.Rounded.FavoriteBorder, "Like songs to keep them", "Liked songs are saved for offline listening over Wi-Fi.")
        Spacer(Modifier.height(14.dp))
        Fact(
            Icons.Rounded.CloudDone,
            "Your library is kept safe",
            if (AutoBackup.isSupported) "A copy is saved in Download/Daydreamin, so a reinstall never loses your likes and playlists."
            else "Export a backup any time from Settings.",
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Start listening", onClick = onFinish)
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun Title(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
}

@Composable
private fun Fact(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), Glass.Clear).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun StepDots(index: Int, count: Int, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == index) 22.dp else 8.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "dot")
            val c by animateColorAsState(if (i == index) accent else Color.White.copy(alpha = 0.25f), label = "dotColor")
            Box(Modifier.size(width = w, height = 8.dp).clip(Radius.pill).background(c))
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).pressable(onClick = onClick).clip(Radius.pill).background(Color.White),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.titleMedium, color = Color.Black) }
}

@Composable
private fun SecondaryButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).pressable(onClick = onClick).glass(Radius.pill, Glass.Regular),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

/** The system file picker, opened right at Download/Daydreamin where the automatic backup lives. */
class OpenBackupDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: android.content.Context, input: Array<String>): android.content.Intent =
        super.createIntent(context, input).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, AutoBackup.pickerStartUri)
            }
        }
}

private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"
