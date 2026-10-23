package com.miking.notas.finance

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.math.sqrt

data class MonthSummary(
    val month: YearMonth,
    val incomeCents: Long,
    val expenseCents: Long,
) {
    val balanceCents: Long get() = incomeCents - expenseCents

    /** (ingresos − gastos) / ingresos. Null si no hubo ingresos. */
    val savingsRate: Double? get() = if (incomeCents > 0) balanceCents.toDouble() / incomeCents else null
}

data class CategoryTotal(
    val category: FinanceCategory,
    val cents: Long,
    /** Parte del total del mes, de 0 a 1. */
    val share: Double,
)

data class CategoryChange(
    val category: FinanceCategory,
    val currentCents: Long,
    val previousCents: Long,
) {
    /** Variación respecto al mes anterior (0.3 = +30 %). Null si antes no hubo gasto. */
    val change: Double? get() = if (previousCents > 0) (currentCents - previousCents).toDouble() / previousCents else null
}

enum class BudgetLevel { OK, WARNING, EXCEEDED }

data class BudgetStatus(
    val category: FinanceCategory,
    val limitCents: Long,
    val spentCents: Long,
    /** Lo que se habrá gastado a fin de mes al ritmo actual. */
    val projectedCents: Long,
) {
    val ratio: Float get() = if (limitCents <= 0) 0f else spentCents.toFloat() / limitCents
    val remainingCents: Long get() = limitCents - spentCents
    val level: BudgetLevel
        get() = when {
            spentCents > limitCents -> BudgetLevel.EXCEEDED
            ratio >= FinanceStats.BUDGET_WARNING_RATIO -> BudgetLevel.WARNING
            else -> BudgetLevel.OK
        }
    val willExceed: Boolean get() = projectedCents > limitCents
}

data class Anomaly(
    val transaction: Transaction,
    val category: FinanceCategory,
    /** Media del resto de gastos de esa categoría. */
    val usualCents: Long,
)

data class GroupSplit(
    val group: SpendingGroup,
    val cents: Long,
    /** Parte de los ingresos del mes. */
    val shareOfIncome: Double,
    /** La parte recomendada por la regla 50/30/20. */
    val target: Double,
)

data class SuggestedCut(
    val category: FinanceCategory,
    val monthlyCents: Long,
)

data class GoalPlan(
    val goal: Goal,
    /** Cuánto habría que ahorrar cada mes para llegar a la fecha objetivo. */
    val requiredMonthlyCents: Long?,
    /** Meses que quedan hasta la fecha objetivo (mínimo 1). */
    val monthsLeft: Int?,
    /** Ahorro medio de los últimos meses (ingresos − gastos). */
    val averageSavingsCents: Long,
    /** Cuándo se alcanzaría al ritmo de ahorro actual. Null si no se ahorra. */
    val estimatedDate: LocalDate?,
    /** Recortes en gustos que cubrirían lo que falta cada mes. */
    val cuts: List<SuggestedCut>,
) {
    val onTrack: Boolean
        get() = requiredMonthlyCents == null || averageSavingsCents >= requiredMonthlyCents
}

/**
 * Las cuentas de la pestaña de finanzas. Son funciones puras para poder
 * probarlas sin Android.
 */
object FinanceStats {

    const val BUDGET_WARNING_RATIO = 0.8f

    fun summary(transactions: List<Transaction>, month: YearMonth): MonthSummary {
        val inMonth = transactions.filter { it.month == month }
        return MonthSummary(
            month = month,
            incomeCents = inMonth.filter { it.type == EntryType.INCOME }.sumOf { it.amountCents },
            expenseCents = inMonth.filter { it.type == EntryType.EXPENSE }.sumOf { it.amountCents },
        )
    }

    /** Los últimos [count] meses terminando en [last], del más antiguo al más reciente. */
    fun history(transactions: List<Transaction>, last: YearMonth, count: Int): List<MonthSummary> =
        (count - 1 downTo 0).map { summary(transactions, last.minusMonths(it.toLong())) }

    fun expensesByCategory(data: FinanceData, month: YearMonth): List<CategoryTotal> =
        totalsByCategory(data, month, EntryType.EXPENSE)

    fun totalsByCategory(data: FinanceData, month: YearMonth, type: EntryType): List<CategoryTotal> {
        val totals = data.transactions
            .filter { it.month == month && it.type == type }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        val sum = totals.values.sum().takeIf { it > 0 } ?: return emptyList()

        return totals.mapNotNull { (id, cents) ->
            val category = data.category(id) ?: unknownCategory(id, type)
            CategoryTotal(category, cents, cents.toDouble() / sum)
        }.sortedByDescending { it.cents }
    }

    /**
     * Proyección lineal a fin de mes: lo gastado ÷ días transcurridos × días
     * del mes. Para meses pasados devuelve lo gastado; para futuros, cero.
     */
    fun projectMonthEnd(spentCents: Long, month: YearMonth, today: LocalDate): Long {
        val current = YearMonth.from(today)
        return when {
            month.isBefore(current) -> spentCents
            month.isAfter(current) -> 0L
            else -> (spentCents.toDouble() / today.dayOfMonth * month.lengthOfMonth()).roundToLong()
        }
    }

    fun budgetStatuses(data: FinanceData, month: YearMonth, today: LocalDate): List<BudgetStatus> {
        val spent = data.transactions
            .filter { it.month == month && it.type == EntryType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }

        return data.budgets.mapNotNull { (id, limit) ->
            val category = data.category(id) ?: return@mapNotNull null
            val used = spent[id] ?: 0L
            BudgetStatus(category, limit, used, projectMonthEnd(used, month, today))
        }.sortedByDescending { it.ratio }
    }

    /** Gasto por categoría este mes frente al anterior, ordenado por la mayor subida. */
    fun monthOverMonth(data: FinanceData, month: YearMonth): List<CategoryChange> {
        val current = expensesByCategory(data, month).associate { it.category.id to it }
        val previous = expensesByCategory(data, month.minusMonths(1)).associate { it.category.id to it }

        return (current.keys + previous.keys).map { id ->
            val category = current[id]?.category ?: previous.getValue(id).category
            CategoryChange(category, current[id]?.cents ?: 0L, previous[id]?.cents ?: 0L)
        }.sortedByDescending { it.currentCents - it.previousCents }
    }

    /**
     * Media móvil del gasto de los [window] meses anteriores a [month]
     * (sin incluirlo, porque el mes en curso está incompleto). Solo cuenta
     * meses con algún movimiento para no hundir la media al empezar a usar la app.
     */
    fun movingAverageExpense(transactions: List<Transaction>, month: YearMonth, window: Int = 3): Long? {
        val months = (1..window).map { summary(transactions, month.minusMonths(it.toLong())) }
            .filter { it.incomeCents > 0 || it.expenseCents > 0 }
        if (months.isEmpty()) return null
        return months.sumOf { it.expenseCents } / months.size
    }

    /** Ahorro medio (ingresos − gastos) de los [window] meses anteriores con datos. */
    fun averageSavings(transactions: List<Transaction>, month: YearMonth, window: Int = 3): Long {
        val months = (1..window).map { summary(transactions, month.minusMonths(it.toLong())) }
            .filter { it.incomeCents > 0 || it.expenseCents > 0 }
        if (months.isEmpty()) {
            // Sin historial se usa el mes en curso para tener al menos una idea.
            return summary(transactions, month).balanceCents
        }
        return months.sumOf { it.balanceCents } / months.size
    }

    /**
     * Análisis de Pareto: las pocas categorías que juntas suman al menos el
     * [threshold] del gasto. Es donde más rinde recortar.
     */
    fun pareto(totals: List<CategoryTotal>, threshold: Double = 0.8): List<CategoryTotal> {
        var accumulated = 0.0
        val result = mutableListOf<CategoryTotal>()
        for (total in totals.sortedByDescending { it.cents }) {
            result += total
            accumulated += total.share
            if (accumulated >= threshold) break
        }
        return result
    }

    /**
     * Gastos atípicos: los del mes que superan la media de su categoría en
     * más de [sigmas] desviaciones típicas. La media se calcula con el resto
     * de gastos de los últimos [lookbackMonths] meses y hacen falta al menos
     * [minSamples] para que la comparación tenga sentido.
     */
    fun anomalies(
        data: FinanceData,
        month: YearMonth,
        lookbackMonths: Long = 6,
        sigmas: Double = 2.0,
        minSamples: Int = 5,
    ): List<Anomaly> {
        val from = month.minusMonths(lookbackMonths)
        val expenses = data.transactions.filter {
            it.type == EntryType.EXPENSE && !it.month.isBefore(from) && !it.month.isAfter(month)
        }

        return expenses.filter { it.month == month }.mapNotNull { candidate ->
            val others = expenses.filter { it.categoryId == candidate.categoryId && it.id != candidate.id }
            if (others.size < minSamples) return@mapNotNull null

            val values = others.map { it.amountCents.toDouble() }
            val mean = values.average()
            val deviation = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
            // Con gastos casi idénticos la desviación es ~0: se exige además el doble de la media.
            val limit = maxOf(mean + sigmas * deviation, mean * 2)

            if (candidate.amountCents > limit) {
                val category = data.category(candidate.categoryId)
                    ?: unknownCategory(candidate.categoryId, EntryType.EXPENSE)
                Anomaly(candidate, category, mean.roundToLong())
            } else {
                null
            }
        }.sortedByDescending { it.transaction.amountCents }
    }

    /** Regla 50/30/20: reparto real del gasto frente a los ingresos del mes. */
    fun rule503020(data: FinanceData, month: YearMonth): List<GroupSplit>? {
        val income = summary(data.transactions, month).incomeCents.takeIf { it > 0 } ?: return null
        val byGroup = data.transactions
            .filter { it.month == month && it.type == EntryType.EXPENSE }
            .groupBy { data.category(it.categoryId)?.group ?: SpendingGroup.WANT }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }

        // Lo que no se gastó también cuenta como ahorro.
        val spent = byGroup.values.sum()
        val leftover = (income - spent).coerceAtLeast(0)

        return SpendingGroup.entries.map { group ->
            val cents = (byGroup[group] ?: 0L) + if (group == SpendingGroup.SAVING) leftover else 0L
            GroupSplit(
                group = group,
                cents = cents,
                shareOfIncome = cents.toDouble() / income,
                target = when (group) {
                    SpendingGroup.NEED -> 0.5
                    SpendingGroup.WANT -> 0.3
                    SpendingGroup.SAVING -> 0.2
                },
            )
        }
    }

    /** Meses enteros que quedan hasta [target], contando el actual (mínimo 1). */
    fun monthsUntil(today: LocalDate, target: LocalDate): Int {
        val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(target)).toInt()
        return (months + 1).coerceAtLeast(1)
    }

    fun goalPlan(goal: Goal, data: FinanceData, today: LocalDate): GoalPlan {
        val month = YearMonth.from(today)
        val averageSavings = averageSavings(data.transactions, month)
        val remaining = goal.remainingCents

        val monthsLeft = goal.targetDate?.let { monthsUntil(today, it) }
        val required = monthsLeft?.let { ceil(remaining.toDouble() / it).toLong() }

        val estimatedDate = when {
            remaining == 0L -> today
            averageSavings <= 0 -> null
            else -> {
                val months = ceil(remaining.toDouble() / averageSavings).toLong()
                today.plusMonths(months)
            }
        }

        val gap = if (required != null) required - averageSavings.coerceAtLeast(0) else 0L
        val cuts = if (gap > 0) suggestCuts(data, month, gap) else emptyList()

        return GoalPlan(
            goal = goal,
            requiredMonthlyCents = required,
            monthsLeft = monthsLeft,
            averageSavingsCents = averageSavings,
            estimatedDate = estimatedDate,
            cuts = cuts,
        )
    }

    /**
     * Reparte [gapCents] entre las categorías de gusto en proporción a lo que
     * se gasta en ellas (media de los últimos meses), sin pedir recortar más
     * del [maxCutRatio] de ninguna.
     */
    fun suggestCuts(
        data: FinanceData,
        month: YearMonth,
        gapCents: Long,
        maxCutRatio: Double = 0.5,
    ): List<SuggestedCut> {
        val window = (0..2).map { month.minusMonths(it.toLong()) }.toSet()
        val wants = data.transactions
            .filter { it.type == EntryType.EXPENSE && it.month in window }
            .filter { data.category(it.categoryId)?.group == SpendingGroup.WANT }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } / window.size }
            .filterValues { it > 0 }
        val total = wants.values.sum().takeIf { it > 0 } ?: return emptyList()

        return wants.mapNotNull { (id, monthly) ->
            val category = data.category(id) ?: return@mapNotNull null
            val proportional = ceil(gapCents.toDouble() * monthly / total).toLong()
            val cut = minOf(proportional, (monthly * maxCutRatio).roundToLong())
            SuggestedCut(category, cut).takeIf { cut > 0 }
        }.sortedByDescending { it.monthlyCents }
    }

    private fun unknownCategory(id: String, type: EntryType) =
        FinanceCategory(id = id, name = "Sin categoría", type = type, group = SpendingGroup.WANT, color = 11)
}
