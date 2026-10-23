package com.miking.notas.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ProjectionTest {

    private val october = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 23)

    private fun tx(amount: Long, date: LocalDate, recurringId: String? = null) = Transaction(
        type = EntryType.EXPENSE,
        amountCents = amount,
        categoryId = "exp-food",
        date = date,
        recurringId = recurringId,
    )

    @Test
    fun sinHistorialUsaElRitmoDiario() {
        // El caso del usuario: S/ 1,000 al día 23 de 31 → ≈ S/ 1,347.83.
        val data = FinanceData(transactions = listOf(tx(100000, october.atDay(10))))
        val projection = FinanceStats.projectExpenses(data, october, today)
        assertEquals(ProjectionMethod.LINEAR, projection.method)
        assertEquals(134783L, projection.totalCents)
    }

    @Test
    fun losPagosFijosSeCuentanUnaSolaVez() {
        val rent = Recurring(
            id = "rent",
            type = EntryType.EXPENSE,
            amountCents = 60000,
            categoryId = "exp-home",
            dayOfMonth = 1,
            start = october,
        )
        val data = FinanceData(
            transactions = listOf(tx(60000, october.atDay(1), "rent"), tx(40000, october.atDay(10))),
            recurrings = listOf(rent),
        )
        val projection = FinanceStats.projectExpenses(data, october, today)
        assertEquals(60000L, projection.fixedPaidCents)
        assertEquals(40000L, projection.variableSpentCents)
        // 400 ÷ 23 × 31 ≈ 539.13 variables + 600 fijos.
        assertEquals(60000L + 53913L, projection.totalCents)
    }

    @Test
    fun sumaLosPagosFijosQueFaltanEsteMes() {
        val internet = Recurring(
            id = "net",
            type = EntryType.EXPENSE,
            amountCents = 9000,
            categoryId = "exp-services",
            dayOfMonth = 28,
            start = october,
        )
        val data = FinanceData(transactions = listOf(tx(23000, october.atDay(5))), recurrings = listOf(internet))
        val projection = FinanceStats.projectExpenses(data, october, today)
        assertEquals(9000L, projection.fixedPendingCents)
        assertEquals(9000L + 31000L, projection.totalCents)
    }

    @Test
    fun conHistorialUsaElPatronDelUsuario() {
        // En agosto y septiembre, al día 23 ya llevaba el 80 % del gasto variable.
        val history = listOf(8L, 9L).flatMap { monthNumber ->
            val month = YearMonth.of(2026, monthNumber.toInt())
            listOf(tx(80000, month.atDay(10)), tx(20000, month.atDay(28)))
        }
        val data = FinanceData(transactions = history + tx(100000, october.atDay(10)))
        val projection = FinanceStats.projectExpenses(data, october, today)
        assertEquals(ProjectionMethod.PATTERN, projection.method)
        assertEquals(2, projection.patternMonths)
        assertEquals(0.8, projection.usualShareByToday!!, 0.0001)
        assertEquals(125000L, projection.totalCents)
    }

    @Test
    fun unSoloMesDeHistorialNoBastaParaElPatron() {
        val data = FinanceData(
            transactions = listOf(tx(80000, LocalDate.of(2026, 9, 10)), tx(100000, october.atDay(10))),
        )
        assertEquals(ProjectionMethod.LINEAR, FinanceStats.projectExpenses(data, october, today).method)
    }

    @Test
    fun presupuestoGeneralYGastoDiarioPermitido() {
        val internet = Recurring(
            id = "net",
            type = EntryType.EXPENSE,
            amountCents = 9000,
            categoryId = "exp-services",
            dayOfMonth = 28,
            start = october,
        )
        val data = FinanceData(
            transactions = listOf(tx(100000, october.atDay(10))),
            recurrings = listOf(internet),
            budgets = mapOf("exp-food" to 150000L, "exp-fun" to 80000L),
            monthlyLimitCents = 200000,
        )
        val overall = FinanceStats.overallBudget(data, october, today)!!
        assertEquals(100000L, overall.remainingCents)
        assertEquals(9, overall.daysLeft) // del 23 al 31, contando hoy
        // (1000 − 90 fijos pendientes) ÷ 9 días ≈ 101.11.
        assertEquals(10111L, overall.dailyAllowanceCents)
        assertEquals(BudgetLevel.OK, overall.level)
        assertTrue(overall.categoryBudgetsCents > overall.limitCents)
    }

    @Test
    fun sinPresupuestoGeneralDevuelveNull() {
        assertNull(FinanceStats.overallBudget(FinanceData(), october, today))
    }

    @Test
    fun elPresupuestoPorCategoriaNoEstiraLosFijos() {
        val data = FinanceData(
            transactions = listOf(tx(60000, october.atDay(1), "rent")),
            budgets = mapOf("exp-food" to 70000L),
        )
        val status = FinanceStats.budgetStatuses(data, october, today).single()
        assertEquals(60000L, status.projectedCents)
    }
}
