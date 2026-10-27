package com.daydreamin.app.desktop.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.daydreamin.app.desktop.theme.DType
import com.daydreamin.app.ui.components.Creator

/** "Made by Ankit @handle" — the handle in the accent colour, opening Instagram. */
@Composable
fun MadeBy(modifier: Modifier = Modifier) {
    val (source, hovered) = rememberHover()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Made by ${Creator.NAME} ", style = DType.small)
        Text(
            "@${Creator.INSTAGRAM_HANDLE}",
            style = DType.small.copy(color = LocalAccent.current, fontWeight = FontWeight.SemiBold, textDecoration = if (hovered) TextDecoration.Underline else null),
            maxLines = 1,
            modifier = Modifier.hoverable(source).pointerHoverIcon(PointerIcon.Hand).clickable(interactionSource = source, indication = null) { Creator.openInstagram() },
        )
    }
}
