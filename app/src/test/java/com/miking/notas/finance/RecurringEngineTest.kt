package com.miking.notas.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurringEngineTest {

    private val rent = Recurring(
        id = "rent",
        type = EntryType.EXPENSE,
        amountCents = 80000,
        categoryId = "exp-home",
        dayOfMonth = 5,
        start = YearMonth.of(2026, 8),
    )

    @Test
    fun generaUnMovimientoPorMesHastaHoy() {
        val due = RecurringEngine.due(listOf(rent), emptyList(), LocalDate.of(2026, 10, 10))
        assertEquals(
            listOf(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5)),
            due.map { it.date },
        )
        assertTrue(due.all { it.recurringId == "rent" && it.amountCents == 80000L })
    }

    @Test
    fun noGeneraSiElDiaNoHaLlegado() {
        val due = RecurringEngine.due(listOf(rent), emptyList(), LocalDate.of(2026, 10, 4))
        assertEquals(listOf(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 5)), due.map { it.date })
    }

    @Test
    fun noDuplicaLosQueYaExisten() {
        val existing = Transaction(
            type = EntryType.EXPENSE,
            amountCents = 80000,
            categoryId = "exp-home",
            date = LocalDate.of(2026, 8, 5),
            recurringId = "rent",
        )
        val due = RecurringEngine.due(listOf(rent), listOf(existing), LocalDate.of(2026, 9, 10))
        assertEquals(listOf(LocalDate.of(2026, 9, 5)), due.map { it.date })
    }

    @Test
    fun respetaLosMesesBorrados() {
        val skipped = rent.copy(skippedMonths = setOf(YearMonth.of(2026, 9)))
        val due = RecurringEngine.due(listOf(skipped), emptyList(), LocalDate.of(2026, 10, 10))
        assertEquals(listOf(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 10, 5)), due.map { it.date })
    }

    @Test
    fun usaElUltimoDiaEnMesesCortos() {
        val endOfMonth = rent.copy(dayOfMonth = 31, start = YearMonth.of(2026, 2))
        val due = RecurringEngine.due(listOf(endOfMonth), emptyList(), LocalDate.of(2026, 2, 28))
        assertEquals(listOf(LocalDate.of(2026, 2, 28)), due.map { it.date })
    }
}
