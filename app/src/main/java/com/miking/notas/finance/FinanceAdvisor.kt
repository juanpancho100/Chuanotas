package com.miking.notas.finance

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * Prepara la consulta de consejos para DeepSeek. Solo se envían totales por
 * categoría y por mes, nunca los movimientos sueltos ni sus notas.
 */
internal object FinanceAdvisor {

    const val SYSTEM_PROMPT = """
Eres un asesor de finanzas personales para una persona en Perú. Los importes están en soles (S/).
Recibes un resumen de sus ingresos, gastos, presupuestos y metas.
Responde en español, en texto plano sin markdown ni tablas, con:
1. Un diagnóstico de 2 o 3 frases.
2. Entre 3 y 5 consejos concretos y numerados, cada uno con una cifra en soles
   (cuánto recortar, cuánto ahorrar o en qué categoría actuar).
3. Si hay metas, una frase sobre si va bien encaminada y qué haría falta.
Sé directo y realista. No inventes datos que no estén en el resumen.
"""

    fun buildPrompt(data: FinanceData, month: YearMonth, today: LocalDate): String = buildString {
        val summary = FinanceStats.summary(data.transactions, month)
        appendLine("Mes analizado: $month (hoy es $today)")
        appendLine("Ingresos: ${Soles.format(summary.incomeCents)}")
        appendLine("Gastos: ${Soles.format(summary.expenseCents)}")
        appendLine("Balance: ${Soles.format(summary.balanceCents)}")
        summary.savingsRate?.let { appendLine("Tasa de ahorro: ${percent(it)}") }

        val projected = FinanceStats.projectMonthEnd(summary.expenseCents, month, today)
        if (projected != summary.expenseCents) {
            appendLine("Gasto proyectado a fin de mes: ${Soles.format(projected)}")
        }
        FinanceStats.movingAverageExpense(data.transactions, month)?.let {
            appendLine("Gasto medio de los 3 meses anteriores: ${Soles.format(it)}")
        }

        appendLine()
        appendLine("Gasto por categoría este mes (grupo, importe, parte, variación vs mes anterior):")
        val changes = FinanceStats.monthOverMonth(data, month).associateBy { it.category.id }
        FinanceStats.expensesByCategory(data, month).forEach { total ->
            val change = changes[total.category.id]?.change?.let { " (${signedPercent(it)})" }.orEmpty()
            appendLine(
                "- ${total.category.name} [${total.category.group.label}]: " +
                    "${Soles.format(total.cents)}, ${percent(total.share)}$change",
            )
        }

        val budgets = FinanceStats.budgetStatuses(data, month, today)
        if (budgets.isNotEmpty()) {
            appendLine()
            appendLine("Presupuestos (gastado / límite):")
            budgets.forEach {
                appendLine("- ${it.category.name}: ${Soles.format(it.spentCents)} / ${Soles.format(it.limitCents)}")
            }
        }

        FinanceStats.rule503020(data, month)?.let { split ->
            appendLine()
            appendLine("Regla 50/30/20 (real vs recomendado sobre ingresos):")
            split.forEach { appendLine("- ${it.group.label}: ${percent(it.shareOfIncome)} vs ${percent(it.target)}") }
        }

        if (data.goals.isNotEmpty()) {
            appendLine()
            appendLine("Metas de ahorro:")
            data.goals.forEach { goal ->
                val plan = FinanceStats.goalPlan(goal, data, today)
                append("- ${goal.name}: ${Soles.format(goal.savedCents)} de ${Soles.format(goal.targetCents)}")
                goal.targetDate?.let { append(", para $it") }
                plan.requiredMonthlyCents?.let { append(", necesita ${Soles.format(it)} al mes") }
                append(", ahorro medio actual ${Soles.format(plan.averageSavingsCents)} al mes")
                appendLine()
            }
        }
    }

    private fun percent(value: Double) = "${(value * 100).roundToInt()} %"

    private fun signedPercent(value: Double): String {
        val rounded = (value * 100).roundToInt()
        return if (rounded > 0) "+$rounded %" else "$rounded %"
    }
}
