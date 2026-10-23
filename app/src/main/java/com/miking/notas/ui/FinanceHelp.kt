package com.miking.notas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miking.notas.finance.OverallBudget
import com.miking.notas.finance.Projection
import com.miking.notas.finance.ProjectionMethod
import com.miking.notas.finance.Soles
import kotlin.math.roundToInt

/**
 * Explica la proyección paso a paso con las cifras reales del usuario,
 * para que pueda rehacer la cuenta a mano.
 */
@Composable
internal fun ProjectionHelpDialog(
    projection: Projection,
    onDismiss: () -> Unit,
) {
    val fixedTotal = projection.fixedPaidCents + projection.fixedPendingCents

    HelpDialog(title = "¿Qué es la proyección?", onDismiss = onDismiss) {
        HelpParagraph(
            "Es una estimación de cuánto habrás gastado el último día del mes si sigues gastando " +
                "como hasta ahora. No es dinero gastado: sirve para frenar a tiempo.",
        )

        HelpHeading("Tus números de este mes")
        HelpRow("Llevas gastado", Soles.format(projection.spentCents))
        if (fixedTotal > 0) {
            HelpRow("· Pagos fijos ya pagados", Soles.format(projection.fixedPaidCents))
            HelpRow("· Gasto variable", Soles.format(projection.variableSpentCents))
        }
        HelpRow("Día del mes", "${projection.daysElapsed} de ${projection.daysInMonth}")

        HelpHeading("Paso 1 · Pagos fijos")
        if (fixedTotal > 0) {
            HelpParagraph(
                "El alquiler, internet o el sueldo que marcaste como «Repetir cada mes» se pagan una sola " +
                    "vez, así que no se estiran al resto del mes: se suman tal cual.",
            )
            HelpRow("Ya pagados", Soles.format(projection.fixedPaidCents))
            if (projection.fixedPendingCents > 0) {
                HelpRow("Faltan este mes", Soles.format(projection.fixedPendingCents))
            }
        } else {
            HelpParagraph(
                "No tienes pagos fijos este mes. Si marcas el alquiler o internet como «Repetir cada mes», " +
                    "se contarán una sola vez y la proyección será más exacta.",
            )
        }

        HelpHeading("Paso 2 · Gasto variable")
        when (projection.method) {
            ProjectionMethod.LINEAR -> {
                HelpParagraph(
                    "Todavía no hay al menos 2 meses anteriores con datos, así que se usa tu ritmo " +
                        "de este mes: lo gastado entre los días que van, por los días del mes.",
                )
                HelpFormula(
                    "${Soles.format(projection.variableSpentCents)} ÷ ${projection.daysElapsed} días = " +
                        "${Soles.format(projection.variableDailyCents)} al día",
                )
                HelpFormula(
                    "${Soles.format(projection.variableDailyCents)} × ${projection.daysInMonth} días ≈ " +
                        Soles.format(projection.variableProjectedCents),
                )
                HelpParagraph(
                    "Esa cifra diaria es un promedio: unos días gastas más y otros nada. " +
                        "No significa que vayas a gastar exactamente eso cada día.",
                )
            }

            ProjectionMethod.PATTERN -> {
                val share = projection.usualShareByToday ?: 1.0
                HelpParagraph(
                    "Se usa cómo gastaste en tus últimos ${projection.patternMonths} meses: a estas alturas " +
                        "del mes (día ${projection.daysElapsed}) solías llevar el ${(share * 100).roundToInt()} % " +
                        "de tu gasto variable. Si este mes es parecido, lo que llevas es esa parte del total.",
                )
                HelpFormula(
                    "${Soles.format(projection.variableSpentCents)} ÷ ${(share * 100).roundToInt()} % ≈ " +
                        Soles.format(projection.variableProjectedCents),
                )
                HelpParagraph(
                    "Así se tiene en cuenta si sueles gastar más a principio o a fin de mes, " +
                        "en vez de suponer un ritmo parejo.",
                )
            }
        }

        HelpHeading("Resultado")
        if (fixedTotal > 0) {
            HelpFormula(
                "${Soles.format(fixedTotal)} fijos + ${Soles.format(projection.variableProjectedCents)} variables ≈ " +
                    Soles.format(projection.totalCents),
            )
        } else {
            HelpFormula("Proyección ≈ ${Soles.format(projection.totalCents)}")
        }
        HelpParagraph(
            "Ojo: una compra grande y puntual (un electrodoméstico, un viaje) sube la proyección como si fuera " +
                "a repetirse. Si es algo que se repite cada mes, márcalo como pago fijo.",
        )
    }
}

@Composable
internal fun OverallBudgetHelpDialog(
    overall: OverallBudget,
    onDismiss: () -> Unit,
) {
    HelpDialog(title = "Presupuesto general", onDismiss = onDismiss) {
        HelpParagraph(
            "Es el tope de todo lo que quieres gastar en el mes, sumando todas las categorías. " +
                "Los presupuestos por categoría son opcionales y sirven para vigilar gastos concretos dentro de ese tope.",
        )

        HelpHeading("Tus números")
        HelpRow("Tope del mes", Soles.format(overall.limitCents))
        HelpRow("Gastado", Soles.format(overall.spentCents))
        HelpRow("Queda", Soles.format(overall.remainingCents))

        overall.dailyAllowanceCents?.let { daily ->
            HelpHeading("Cuánto puedes gastar al día")
            if (overall.fixedPendingCents > 0) {
                HelpParagraph("Primero se apartan los pagos fijos que aún faltan este mes, porque ya están comprometidos.")
                HelpFormula(
                    "${Soles.format(overall.remainingCents)} − ${Soles.format(overall.fixedPendingCents)} fijos = " +
                        Soles.format(overall.remainingCents - overall.fixedPendingCents),
                )
            }
            HelpParagraph("Lo que queda se reparte entre los días que faltan, contando hoy.")
            HelpFormula(
                "${Soles.format((overall.remainingCents - overall.fixedPendingCents).coerceAtLeast(0))} ÷ " +
                    "${overall.daysLeft} días ≈ ${Soles.format(daily)} al día",
            )
        }

        HelpHeading("Avisos")
        HelpParagraph(
            "Te aviso cuando llegues al 80 % del tope y cuando te pases. Si tus presupuestos por categoría " +
                "suman más que el general, también lo verás en la tarjeta.",
        )
    }
}

@Composable
private fun HelpDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido") }
        },
    )
}

@Composable
private fun HelpHeading(text: String) {
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
}

@Composable
private fun HelpParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun HelpRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HelpFormula(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
