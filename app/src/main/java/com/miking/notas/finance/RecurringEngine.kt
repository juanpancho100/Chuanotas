package com.miking.notas.finance

import java.time.LocalDate
import java.time.YearMonth

/** Convierte los pagos fijos mensuales en movimientos reales. */
object RecurringEngine {

    /**
     * Movimientos que faltan por crear hasta [today]: uno por mes desde el
     * inicio de cada pago fijo, siempre que su día ya haya llegado y no exista
     * todavía. Si el mes es más corto que el día elegido (un 31 en febrero),
     * se usa el último día del mes.
     */
    fun due(
        recurrings: List<Recurring>,
        transactions: List<Transaction>,
        today: LocalDate,
    ): List<Transaction> {
        val existing = transactions
            .mapNotNull { tx -> tx.recurringId?.let { it to tx.month } }
            .toSet()
        val currentMonth = YearMonth.from(today)

        return recurrings.filter { it.active }.flatMap { recurring ->
            generateSequence(recurring.start) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(currentMonth) }
                .mapNotNull { month ->
                    val date = month.atDay(recurring.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
                    if (date.isAfter(today) ||
                        month in recurring.skippedMonths ||
                        (recurring.id to month) in existing
                    ) {
                        null
                    } else {
                        Transaction(
                            type = recurring.type,
                            amountCents = recurring.amountCents,
                            categoryId = recurring.categoryId,
                            note = recurring.note,
                            date = date,
                            recurringId = recurring.id,
                        )
                    }
                }
                .toList()
        }
    }
}
