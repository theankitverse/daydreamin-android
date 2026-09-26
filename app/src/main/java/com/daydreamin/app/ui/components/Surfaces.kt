package com.daydreamin.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daydreamin.app.ui.theme.Glass
import com.daydreamin.app.ui.theme.Motion
import com.daydreamin.app.ui.theme.Radius
import com.daydreamin.app.ui.theme.Space
import com.daydreamin.app.ui.theme.TextPrimary
import com.daydreamin.app.ui.theme.TextSecondary
import com.daydreamin.app.ui.theme.glass

/**
 * The app's one touch response: the element physically gives under your finger and springs
 * back, instead of a ripple washing over glass (ripples read as flat Material, not glass).
 * The scale is read inside graphicsLayer, so pressing never recomposes the content.
 */
fun Modifier.pressable(
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) Motion.pressedScale else 1f, Motion.press(), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, role = role, onClick = onClick)
}

/**
 * A section's heading. [subtitle] sits under the title in quiet sentence case — context, not a
 * second headline. (All-caps overlines are reserved for a single featured item, where they label
 * rather than describe.)
 */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.gutter),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Box(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

/** Filter chip in glass; the selected one becomes a solid bright pill — unmistakable at a glance. */
@Composable
fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(if (selected) Color.White.copy(alpha = 0.95f) else Color.Transparent, Motion.settle(), label = "chipFill")
    val text by animateColorAsState(if (selected) Color.Black else TextPrimary.copy(alpha = 0.82f), Motion.settle(), label = "chipText")
    Box(
        modifier = modifier
            .height(34.dp)
            .pressable(role = Role.Tab, onClick = onClick)
            .glass(Radius.pill, Glass.Clear)
            .background(fill, Radius.pill)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = text)
    }
}

/** Compact bright call-to-action pill (e.g. "Play") — the one solid element on a glass surface. */
@Composable
fun SolidPillButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .pressable(onClick = onClick)
            .clip(Radius.pill)
            .background(Color.White)
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
        Box(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.Black)
    }
}

/** Round glass icon button for top bars. */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .pressable(onClick = onClick)
            .glass(Radius.pill, Glass.Clear),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = TextPrimary, modifier = Modifier.size(21.dp))
    }
}
