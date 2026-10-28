package com.daydreamin.app.desktop.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.daydreamin.app.desktop.theme.DColors
import com.daydreamin.app.desktop.theme.DSize
import com.daydreamin.app.desktop.theme.DType

/** Whether the pointer is over this element. */
@Composable
fun rememberHover(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return source to hovered
}

/**
 * Something clickable with a mouse: the hand cursor, a soft highlight while the pointer is over it,
 * a slightly stronger one while pressed. No shrink-on-press — that's touch feedback.
 */
fun Modifier.desktopClickable(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp),
    highlight: Boolean = true,
    role: Role = Role.Button,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val bg by animateColorAsState(
        when {
            !highlight || !enabled -> Color.Transparent
            pressed -> DColors.pressed
            hovered -> DColors.hover
            else -> Color.Transparent
        },
        tween(90),
        label = "hover",
    )
    this
        .clip(shape)
        .background(bg)
        .hoverable(source, enabled)
        .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
        .clickable(interactionSource = source, indication = null, role = role, enabled = enabled, onClick = onClick)
}

/** A small dark label that appears after a moment over an icon-only control — what it does, and its shortcut. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithTooltip(text: String?, shortcut: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (text == null) { Box(modifier) { content() }; return }
    TooltipArea(
        modifier = modifier,
        tooltip = {
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DColors.popup)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text, style = DType.small.copy(color = DColors.ink))
                if (shortcut != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(shortcut, style = DType.small.copy(color = DColors.ink.copy(alpha = 0.5f)))
                }
            }
        },
        delayMillis = 450,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 18.dp)),
    ) { content() }
}

/**
 * An icon-only button: dimmed until the pointer is over it, always with a tooltip.
 * [active] marks a toggle that's on (shuffle, the open panel) in the accent colour.
 */
@Composable
fun DIconButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = DSize.iconButton,
    iconSize: Dp = DSize.icon,
    active: Boolean = false,
    activeColor: Color = DColors.ink,
    shortcut: String? = null,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val (source, hovered) = rememberHover()
    val color by animateColorAsState(
        when {
            !enabled -> DColors.ink.copy(alpha = 0.25f)
            active -> activeColor
            tint != null -> tint
            hovered -> DColors.iconHover
            else -> DColors.iconIdle
        },
        tween(90),
        label = "icon",
    )
    WithTooltip(tooltip, shortcut) {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .hoverable(source, enabled)
                .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                .clickable(interactionSource = source, indication = null, role = Role.Button, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = tooltip, tint = color, modifier = Modifier.size(iconSize))
            if (active) Box(Modifier.align(Alignment.BottomCenter).offset(y = (-1).dp).size(4.dp).clip(CircleShape).background(activeColor))
        }
    }
}

/** A text button. [primary]: white pill (the main action on a page); otherwise outlined. */
@Composable
fun DButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, icon: ImageVector? = null) {
    val (source, hovered) = rememberHover()
    val bg = when {
        primary && hovered -> DColors.ink
        primary -> DColors.ink.copy(alpha = 0.92f)
        hovered -> DColors.ink.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val fg = if (primary) DColors.onInk else DColors.ink
    Row(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (!primary) Modifier.border(1.dp, DColors.ink.copy(alpha = if (hovered) 0.6f else 0.3f), RoundedCornerShape(50)) else Modifier)
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = DType.button.copy(color = fg))
    }
}

/**
 * A thin horizontal slider (the timeline, volume): a line that thickens and shows its knob while
 * the pointer is over it, accent-filled up to [value]. Click anywhere to jump, or drag.
 * [onValueChange] is called while dragging; [onCommit] once, when the drag or click ends.
 */
@Composable
fun DSlider(
    value: Float,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChange: ((Float) -> Unit)? = null,
    fill: Color = DColors.ink,
) {
    val (source, hovered) = rememberHover()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else value.coerceIn(0f, 1f)
    val commit by rememberUpdatedState(onCommit)
    val change by rememberUpdatedState(onValueChange)
    val emphasised = hovered || dragging
    BoxWithConstraints(
        modifier
            .height(16.dp)
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures { offset -> commit((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> dragging = true; dragValue = (offset.x / size.width).coerceIn(0f, 1f); change?.invoke(dragValue) },
                    onDragEnd = { dragging = false; commit(dragValue) },
                    onDragCancel = { dragging = false },
                    onDrag = { pointer, _ -> dragValue = (pointer.position.x / size.width).coerceIn(0f, 1f); change?.invoke(dragValue) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val track = if (emphasised) 5.dp else 4.dp
        Box(Modifier.fillMaxWidth().height(track).clip(RoundedCornerShape(50)).background(DColors.ink.copy(alpha = 0.22f)))
        Box(Modifier.fillMaxWidth(shown).height(track).clip(RoundedCornerShape(50)).background(if (emphasised) LocalAccent.current else fill))
        if (emphasised) {
            val knob = 12.dp
            Box(
                Modifier
                    .offset(x = (maxWidth - knob) * shown)
                    .size(knob)
                    .clip(CircleShape)
                    .background(DColors.ink),
            )
        }
    }
}

/** The app's accent colour, for focus, active toggles and filled sliders. */
val LocalAccent = androidx.compose.runtime.staticCompositionLocalOf { Color(0xFF8B7CF6) }

/** Thin scrollbars that brighten under the pointer — visible, as desktop scrolling expects. */
@Composable
fun DesktopScrollbars(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalScrollbarStyle provides ScrollbarStyle(
            minimalHeight = 32.dp,
            thickness = 8.dp,
            shape = RoundedCornerShape(4.dp),
            hoverDurationMillis = 200,
            unhoverColor = DColors.ink.copy(alpha = 0.16f),
            hoverColor = DColors.ink.copy(alpha = 0.34f),
        ),
    ) { content() }
}
