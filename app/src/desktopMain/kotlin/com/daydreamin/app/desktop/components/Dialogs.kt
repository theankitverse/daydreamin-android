package com.daydreamin.app.desktop.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.daydreamin.app.data.model.Song
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.ui.components.SongActions

/**
 * A dialog the desktop way: a compact glass card in the middle of the window over a dimmed app,
 * the actions at the bottom right, Esc to cancel and Enter for the main action. (The phone's are
 * full-width sheets sized for a thumb.)
 */
@Composable
fun DesktopDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
    onEnter: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(140)) }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { e ->
            when {
                e.type != KeyEventType.KeyDown -> false
                e.key == Key.Escape -> { onDismiss(); true }
                (e.key == Key.Enter || e.key == Key.NumPadEnter) && onEnter != null -> { onEnter(); true }
                else -> false
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = appear.value }
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(420.dp)
                    .graphicsLayer { val s = 0.96f + 0.04f * appear.value; scaleX = s; scaleY = s }
                    .shadow(32.dp, RoundedCornerShape(14.dp))
                    .background(Color(0xFF17171E), RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
                    // Clicks inside the card stay in it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(24.dp),
            ) {
                Text(title, style = DType.section.copy(fontSize = 19.sp))
                Spacer(Modifier.height(14.dp))
                content()
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) { actions() }
            }
        }
    }
}

/** Asks for a name (a new playlist, a rename). */
@Composable
fun NameDialog(title: String, confirm: String, initial: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val submit = { if (value.text.isNotBlank()) onConfirm(value.text.trim()) }
    DesktopDialog(
        title = title,
        onDismiss = onDismiss,
        onEnter = submit,
        actions = {
            DButton("Cancel", onDismiss)
            DButton(confirm, submit, primary = true)
        },
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it.copy(text = it.text.take(60)) },
            singleLine = true,
            textStyle = DType.body.copy(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onDismiss(); true } else false },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(DColors.field, RoundedCornerShape(8.dp))
                        .border(1.dp, if (focused) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.text.isEmpty()) Text("Playlist name", style = DType.body.copy(fontSize = 15.sp, color = Color.White.copy(alpha = 0.4f)))
                    inner()
                }
            },
        )
    }
}

/** A new playlist with [song] as its first. */
@Composable
fun NewPlaylistDialog(song: Song, onDismiss: () -> Unit, onCreated: () -> Unit) {
    NameDialog("New playlist", "Create", onDismiss = onDismiss, onConfirm = { SongActions.createPlaylist(it, firstSong = song); onCreated() })
}

/** Are you sure? — for things that can't be undone. */
@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    DesktopDialog(
        title = title,
        onDismiss = onDismiss,
        onEnter = onConfirm,
        actions = {
            DButton("Cancel", onDismiss)
            DButton(confirm, onConfirm, primary = true)
        },
    ) {
        Text(body, style = DType.body.copy(color = Color.White.copy(alpha = 0.75f)))
    }
}
