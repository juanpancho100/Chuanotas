package com.miking.notas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miking.notas.finance.MonthSummary
import com.miking.notas.finance.Soles
import java.time.format.TextStyle
import java.util.Locale

/**
 * Colores de los gráficos de finanzas. Salen de la paleta categórica validada
 * (azul y naranja son los dos primeros huecos) y de la paleta de estados, con
 * pasos propios para el modo oscuro.
 */
@Immutable
internal data class FinanceColors(
    val income: Color,
    val expense: Color,
    val good: Color,
    val warning: Color,
    val critical: Color,
    val bar: Color,
    val track: Color,
    val grid: Color,
    val goodText: Color,
    val badText: Color,
)

@Composable
internal fun financeColors(): FinanceColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        FinanceColors(
            income = Color(0xFF3987E5),
            expense = Color(0xFFD95926),
            good = Color(0xFF0CA30C),
            warning = Color(0xFFFAB219),
            critical = Color(0xFFD03B3B),
            bar = Color(0xFF3987E5),
            track = Color(0xFF383835),
            grid = Color(0xFF2C2C2A),
            goodText = Color(0xFF0CA30C),
            badText = Color(0xFFE66767),
        )
    } else {
        FinanceColors(
            income = Color(0xFF2A78D6),
            expense = Color(0xFFEB6834),
            good = Color(0xFF0CA30C),
            warning = Color(0xFFFAB219),
            critical = Color(0xFFD03B3B),
            bar = Color(0xFF2A78D6),
            track = Color(0xFFE1E0D9),
            grid = Color(0xFFE1E0D9),
            goodText = Color(0xFF006300),
            badText = Color(0xFFD03B3B),
        )
    }
}

/**
 * Barra horizontal de progreso. [markerAt] dibuja una línea fina en esa
 * fracción (la meta de la regla 50/30/20, por ejemplo).
 */
@Composable
internal fun MeterBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    markerAt: Float? = null,
) {
    val colors = financeColors()
    val markerColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = colors.track, cornerRadius = radius)
        val filled = size.width * fraction.coerceIn(0f, 1f)
        if (filled > 0f) {
            drawRoundRect(
                color = color,
                size = Size(filled.coerceAtLeast(size.height), size.height),
                cornerRadius = radius,
            )
        }
        markerAt?.let { at ->
            val x = size.width * at.coerceIn(0f, 1f)
            drawLine(
                color = markerColor,
                start = Offset(x, -3.dp.toPx()),
                end = Offset(x, size.height + 3.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

/**
 * Ingresos frente a gastos de los últimos meses, en barras agrupadas sobre
 * un único eje. Al tocar un mes se muestran sus cifras.
 */
@Composable
internal fun MonthlyBarsChart(
    history: List<MonthSummary>,
    modifier: Modifier = Modifier,
) {
    val colors = financeColors()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = history.maxOfOrNull { maxOf(it.incomeCents, it.expenseCents) }?.takeIf { it > 0 } ?: 1L
    var selected by remember(history) { mutableIntStateOf(history.lastIndex) }

    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(color = colors.income, label = "Ingresos")
            LegendItem(color = colors.expense, label = "Gastos")
        }
        // Detalle del mes tocado (por defecto, el último).
        history.getOrNull(selected)?.let { month ->
            Text(
                text = "${monthShort(month).replaceFirstChar { it.uppercase() }}: " +
                    "ingresos ${Soles.formatShort(month.incomeCents)} · " +
                    "gastos ${Soles.formatShort(month.expenseCents)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .semantics {
                        contentDescription = history.joinToString(". ") {
                            "${monthShort(it)}: ingresos ${Soles.format(it.incomeCents)}, " +
                                "gastos ${Soles.format(it.expenseCents)}"
                        }
                    }
                    .pointerInput(history) {
                        detectTapGestures { offset ->
                            val slot = (offset.x / (size.width.toFloat() / history.size)).toInt()
                            selected = slot.coerceIn(0, history.lastIndex)
                        }
                    },
            ) {
                val slot = size.width / history.size
                val barWidth = (slot * 0.28f).coerceAtMost(18.dp.toPx())
                val gap = 2.dp.toPx()
                val radius = 4.dp.toPx()

                // Línea base, recesiva.
                drawLine(colors.grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())

                history.forEachIndexed { index, month ->
                    val center = slot * index + slot / 2
                    if (index == selected) {
                        drawRect(
                            color = colors.grid.copy(alpha = 0.5f),
                            topLeft = Offset(slot * index, 0f),
                            size = Size(slot, size.height),
                        )
                    }
                    drawTopRoundedBar(
                        left = center - gap / 2 - barWidth,
                        width = barWidth,
                        height = size.height * month.incomeCents / maxValue,
                        color = colors.income,
                        radius = radius,
                    )
                    drawTopRoundedBar(
                        left = center + gap / 2,
                        width = barWidth,
                        height = size.height * month.expenseCents / maxValue,
                        color = colors.expense,
                        radius = radius,
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            history.forEach { month ->
                Text(
                    text = monthShort(month),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun DrawScope.drawTopRoundedBar(
    left: Float,
    width: Float,
    height: Float,
    color: Color,
    radius: Float,
) {
    if (height <= 0f) return
    val top = size.height - height
    val r = radius.coerceAtMost(height).coerceAtMost(width / 2)
    val path = Path().apply {
        moveTo(left, size.height)
        lineTo(left, top + r)
        quadraticTo(left, top, left + r, top)
        lineTo(left + width - r, top)
        quadraticTo(left + width, top, left + width, top + r)
        lineTo(left + width, size.height)
        close()
    }
    drawPath(path, color)
}

@Composable
internal fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Fila con nombre, barra proporcional e importe. Sirve como gráfico de
 * barras horizontal y a la vez como tabla legible.
 */
@Composable
internal fun AmountBarRow(
    label: String,
    cents: Long,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Soles.format(cents),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        MeterBar(fraction = fraction, color = color)
    }
}

internal fun monthShort(month: MonthSummary): String =
    month.month.month.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es-PE")).trimEnd('.')
