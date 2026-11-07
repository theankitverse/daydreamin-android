package com.daydreamin.app.ui.screens.recap

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The recap's charts, drawn the same on screen and on a share card. [reveal] runs 0 to 1 as they grow in. */
internal val MONTH_LETTERS = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
internal val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

private fun DrawScope.label(m: TextMeasurer, value: String, center: Offset, px: Float, color: Color) {
    val layout = m.measure(AnnotatedString(value), TextStyle(color = color, fontSize = (px / density).sp, fontWeight = FontWeight.SemiBold), density = Density(density))
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}

/** Bars side by side, one highlighted, with a letter under each. Fills the area given. */
internal fun DrawScope.drawBars(
    m: TextMeasurer, values: List<Long>, letters: List<String>, highlight: Int, area: Size, origin: Offset,
    color: Color, highlightColor: Color, labelColor: Color, labelPx: Float, reveal: Float = 1f,
) {
    val top = values.maxOrNull()?.takeIf { it > 0 } ?: return
    val slot = area.width / values.size
    val barWidth = slot * 0.62f
    val floor = origin.y + area.height - labelPx * 1.9f
    val tallest = floor - origin.y
    values.forEachIndexed { i, value ->
        val share = value.toFloat() / top
        val t = (reveal * 1.5f - i * 0.5f / values.size).coerceIn(0f, 1f)
        val height = maxOf(barWidth * 0.5f, tallest * share * t).takeIf { value > 0 } ?: (barWidth * 0.35f)
        val x = origin.x + slot * i + (slot - barWidth) / 2f
        drawRoundRect(
            if (i == highlight) highlightColor else color.copy(alpha = if (value > 0) color.alpha else color.alpha * 0.35f),
            Offset(x, floor - height), Size(barWidth, height), CornerRadius(barWidth / 2f),
        )
        label(m, letters[i], Offset(x + barWidth / 2f, floor + labelPx * 1.1f), labelPx, if (i == highlight) highlightColor else labelColor)
    }
}

/** A day as a dial: twenty-four spokes, midnight at the top, longer where you listen more. */
internal fun DrawScope.drawClock(
    m: TextMeasurer, hours: List<Long>, center: Offset, radius: Float,
    color: Color, highlightColor: Color, labelColor: Color, labelPx: Float, reveal: Float = 1f,
) {
    val top = hours.maxOrNull()?.takeIf { it > 0 } ?: return
    val peak = hours.indexOf(top)
    val inner = radius * 0.46f
    val reach = radius * 0.86f - inner
    val stroke = radius * 0.062f
    hours.forEachIndexed { hour, minutes ->
        val angle = (hour / 24f) * 2f * PI.toFloat() - PI.toFloat() / 2f
        val direction = Offset(cos(angle), sin(angle))
        val share = sqrt(minutes.toFloat() / top)
        val length = (reach * share * reveal).coerceAtLeast(stroke * 0.2f)
        drawLine(
            if (hour == peak) highlightColor else color,
            center + direction * inner, center + direction * (inner + length),
            strokeWidth = stroke, cap = StrokeCap.Round,
        )
    }
    listOf(0 to "12am", 6 to "6am", 12 to "12pm", 18 to "6pm").forEach { (hour, text) ->
        val angle = (hour / 24f) * 2f * PI.toFloat() - PI.toFloat() / 2f
        label(m, text, center + Offset(cos(angle), sin(angle)) * (radius * 1.0f), labelPx, labelColor)
    }
}

/** A year as twelve rows of dots, one a day, brighter the more you listened. */
internal fun DrawScope.drawYearGrid(
    m: TextMeasurer, year: Int, days: Map<LocalDate, Long>, area: Size, origin: Offset,
    color: Color, emptyColor: Color, labelColor: Color, labelPx: Float, reveal: Float = 1f,
) {
    val top = days.values.maxOrNull()?.takeIf { it > 0 } ?: return
    val gutter = labelPx * 1.6f
    val column = (area.width - gutter) / 31f
    val row = area.height / 12f
    val radius = minOf(column, row) * 0.38f
    for (month in 1..12) {
        val y = origin.y + row * (month - 0.5f)
        label(m, MONTH_LETTERS[month - 1], Offset(origin.x + labelPx * 0.5f, y), labelPx, labelColor)
        val shown = ((reveal * 14f) - (month - 1)).coerceIn(0f, 1f)
        if (shown <= 0f) continue
        for (day in 1..YearMonth.of(year, month).lengthOfMonth()) {
            val at = Offset(origin.x + gutter + column * (day - 0.5f), y)
            val minutes = days[LocalDate.of(year, month, day)]
            if (minutes == null) drawCircle(emptyColor.copy(alpha = emptyColor.alpha * shown), radius * 0.7f, at)
            else drawCircle(color.copy(alpha = (0.35f + 0.65f * sqrt(minutes.toFloat() / top)) * shown), radius, at)
        }
    }
}
