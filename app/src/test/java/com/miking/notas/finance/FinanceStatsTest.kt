package com.miking.notas.finance

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceStatsTest {

    private val october = YearMonth.of(2026, 10)

    private fun tx(
        amount: Long,
        category: String,
        date: LocalDate,
        type: EntryType = EntryType.EXPENSE,
    ) = Transaction(type = type, amountCents = amount, categoryId = category, date = date)

    private fun income(amount: Long, month: YearMonth) =
        tx(amount, "inc-salary", month.atDay(1), EntryType.INCOME)

    @Test
    fun resumenYTasaDeAhorro() {
        val data = listOf(
            income(300000, october),
            tx(100000, "exp-home", october.atDay(5)),
            tx(50000, "exp-food", october.atDay(6)),
        )
        val summary = FinanceStats.summary(data, october)
        assertEquals(300000L, summary.incomeCents)
        assertEquals(150000L, summary.expenseCents)
        assertEquals(0.5, summary.savingsRate!!, 0.0001)
    }

    @Test
    fun proyeccionLinealAFinDeMes() {
        // 10 días de 31 con S/ 100 gastados → S/ 310.
        assertEquals(31000L, FinanceStats.projectMonthEnd(10000, october, LocalDate.of(2026, 10, 10)))
        // Mes pasado: lo gastado.
        assertEquals(10000L, FinanceStats.projectMonthEnd(10000, october, LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun presupuestoConAvisoAl80PorCiento() {
        val data = FinanceData(
            transactions = listOf(tx(42000, "exp-food", october.atDay(20))),
            budgets = mapOf("exp-food" to 50000L),
        )
        val status = FinanceStats.budgetStatuses(data, october, LocalDate.of(2026, 10, 20)).single()
        assertEquals(BudgetLevel.WARNING, status.level)
        assertEquals(8000L, status.remainingCents)
        assertTrue(status.willExceed)

        val over = data.copy(transactions = data.transactions + tx(9000, "exp-food", october.atDay(21)))
        assertEquals(BudgetLevel.EXCEEDED, FinanceStats.budgetStatuses(over, october, LocalDate.of(2026, 10, 21)).single().level)
    }

    @Test
    fun mediaMovilIgnoraMesesSinDatos() {
        val data = listOf(
            tx(30000, "exp-food", LocalDate.of(2026, 9, 3)),
            tx(10000, "exp-food", LocalDate.of(2026, 8, 3)),
        )
        assertEquals(20000L, FinanceStats.movingAverageExpense(data, october))
        assertNull(FinanceStats.movingAverageExpense(emptyList(), october))
    }

    @Test
    fun paretoDevuelveLasCategoriasQueSuman80() {
        val data = FinanceData(
            transactions = listOf(
                tx(50000, "exp-home", october.atDay(1)),
                tx(30000, "exp-food", october.atDay(2)),
                tx(10000, "exp-fun", october.atDay(3)),
                tx(10000, "exp-shopping", october.atDay(4)),
            ),
        )
        val pareto = FinanceStats.pareto(FinanceStats.expensesByCategory(data, october))
        assertEquals(listOf("exp-home", "exp-food"), pareto.map { it.category.id })
    }

    @Test
    fun detectaGastoAtipico() {
        val usual = (1..6).map { tx(2000L + it * 50, "exp-food", LocalDate.of(2026, 9, it)) }
        val big = tx(15000, "exp-food", october.atDay(10))
        val normal = tx(2100, "exp-food", october.atDay(11))
        val data = FinanceData(transactions = usual + big + normal)

        val anomalies = FinanceStats.anomalies(data, october)
        assertEquals(listOf(big.id), anomalies.map { it.transaction.id })
    }

    @Test
    fun noMarcaAtipicosSinHistorialSuficiente() {
        val data = FinanceData(transactions = listOf(tx(2000, "exp-food", october.atDay(1)), tx(90000, "exp-food", october.atDay(2))))
        assertTrue(FinanceStats.anomalies(data, october).isEmpty())
    }

    @Test
    fun regla503020CuentaLoQueSobraComoAhorro() {
        val data = FinanceData(
            transactions = listOf(
                income(100000, october),
                tx(60000, "exp-home", october.atDay(2)),
                tx(20000, "exp-fun", october.atDay(3)),
            ),
        )
        val split = FinanceStats.rule503020(data, october)!!.associateBy { it.group }
        assertEquals(0.6, split.getValue(SpendingGroup.NEED).shareOfIncome, 0.0001)
        assertEquals(0.2, split.getValue(SpendingGroup.WANT).shareOfIncome, 0.0001)
        assertEquals(0.2, split.getValue(SpendingGroup.SAVING).shareOfIncome, 0.0001)
    }

    @Test
    fun planDeMetaConFechaYRecortes() {
        val today = LocalDate.of(2026, 10, 23)
        // Tres meses anteriores: ingresos 3000, gastos 2600 (de ellos 600 en ocio) → ahorro 400/mes.
        val history = (1..3).flatMap { offset ->
            val month = october.minusMonths(offset.toLong())
            listOf(
                income(300000, month),
                tx(200000, "exp-home", month.atDay(2)),
                tx(60000, "exp-fun", month.atDay(3)),
            )
        } + listOf(tx(60000, "exp-fun", october.atDay(3)))
        val goal = Goal(name = "Moto", targetCents = 600000, targetDate = LocalDate.of(2027, 3, 1))
        val data = FinanceData(transactions = history, goals = listOf(goal))

        val plan = FinanceStats.goalPlan(goal, data, today)
        assertEquals(6, plan.monthsLeft) // octubre a marzo, ambos incluidos
        assertEquals(100000L, plan.requiredMonthlyCents)
        assertEquals(40000L, plan.averageSavingsCents)
        assertEquals(LocalDate.of(2028, 1, 23), plan.estimatedDate) // 15 meses
        assertTrue(!plan.onTrack)
        // Faltan 600 al mes; ocio gasta 600 de media y se recorta como mucho la mitad.
        assertEquals(listOf("exp-fun" to 30000L), plan.cuts.map { it.category.id to it.monthlyCents })
    }

    @Test
    fun metaSinAhorroNoTieneFechaEstimada() {
        val goal = Goal(name = "Viaje", targetCents = 100000)
        val plan = FinanceStats.goalPlan(goal, FinanceData(), LocalDate.of(2026, 10, 1))
        assertNull(plan.estimatedDate)
        assertNull(plan.requiredMonthlyCents)
    }

    @Test
    fun jsonIdaYVuelta() {
        val goal = Goal(
            name = "Moto",
            targetCents = 800000,
            targetDate = LocalDate.of(2027, 12, 1),
            contributions = listOf(GoalContribution(amountCents = 50000, date = LocalDate.of(2026, 10, 1))),
        )
        val data = FinanceData(
            categories = DefaultFinanceCategories.all +
                FinanceCategory("custom-1", "Mascotas", EntryType.EXPENSE, SpendingGroup.NEED),
            transactions = listOf(tx(1250, "custom-1", october.atDay(4)).copy(note = "Comida del perro")),
            recurrings = listOf(
                Recurring(
                    type = EntryType.EXPENSE,
                    amountCents = 5000,
                    categoryId = "exp-services",
                    dayOfMonth = 15,
                    start = october,
                    skippedMonths = setOf(october),
                ),
            ),
            budgets = mapOf("exp-food" to 60000L),
            goals = listOf(goal),
        )

        val restored = FinanceData.fromJson(JSONObject(data.toJson().toString()))
        assertEquals(data, restored)
        assertEquals(50000L, restored.goals.single().savedCents)
    }

    @Test
    fun archivoAntiguoRecibeCategoriasNuevas() {
        val restored = FinanceData.fromJson(JSONObject("""{"categories":[],"transactions":[]}"""))
        assertNotNull(restored.category("exp-food"))
    }
}
