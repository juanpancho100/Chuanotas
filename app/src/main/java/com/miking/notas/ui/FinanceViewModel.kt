package com.miking.notas.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miking.notas.ai.AiConfig
import com.miking.notas.ai.DeepSeekClient
import com.miking.notas.finance.BudgetLevel
import com.miking.notas.finance.EntryType
import com.miking.notas.finance.FinanceAdvisor
import com.miking.notas.finance.FinanceCategory
import com.miking.notas.finance.FinanceData
import com.miking.notas.finance.FinanceRepository
import com.miking.notas.finance.FinanceStats
import com.miking.notas.finance.Goal
import com.miking.notas.finance.GoalContribution
import com.miking.notas.finance.Recurring
import com.miking.notas.finance.RecurringEngine
import com.miking.notas.finance.SpendingGroup
import com.miking.notas.finance.Soles
import com.miking.notas.finance.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Estado de los consejos de IA sobre las finanzas. */
data class FinanceAdviceState(
    val working: Boolean = false,
    val text: String? = null,
    val error: String? = null,
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FinanceRepository(application)
    private val saveQueue = Channel<FinanceData>(Channel.CONFLATED)

    private val _data = MutableStateFlow(repository.load())
    private val _month = MutableStateFlow(YearMonth.now())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val _advice = MutableStateFlow(FinanceAdviceState())

    val data: StateFlow<FinanceData> = _data.asStateFlow()
    val month: StateFlow<YearMonth> = _month.asStateFlow()
    val messages = _messages.asSharedFlow()
    val advice: StateFlow<FinanceAdviceState> = _advice.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (snapshot in saveQueue) {
                repository.save(snapshot)
            }
        }
        applyRecurring()
    }

    /** Crea los movimientos de pagos fijos que ya tocaban. Se llama al volver a la app. */
    fun applyRecurring() {
        val current = _data.value
        val due = RecurringEngine.due(current.recurrings, current.transactions, LocalDate.now())
        if (due.isNotEmpty()) {
            update { it.copy(transactions = it.transactions + due) }
        }
    }

    /* ------------------------------- Mes ------------------------------- */

    fun previousMonth() = _month.update { it.minusMonths(1) }

    fun nextMonth() = _month.update { it.plusMonths(1) }

    fun currentMonth() {
        _month.value = YearMonth.now()
    }

    /* ---------------------------- Movimientos ---------------------------- */

    /**
     * Crea o actualiza un movimiento. Si [monthly] es true y es nuevo, además
     * crea un pago fijo que se repetirá cada mes ese mismo día.
     */
    fun saveTransaction(transaction: Transaction, monthly: Boolean = false) {
        val before = budgetLevel(transaction.categoryId, transaction.month)
        val overallBefore = FinanceStats.overallBudget(_data.value, transaction.month, LocalDate.now())?.level

        if (monthly && transaction.recurringId == null) {
            val recurring = Recurring(
                type = transaction.type,
                amountCents = transaction.amountCents,
                categoryId = transaction.categoryId,
                note = transaction.note,
                dayOfMonth = transaction.date.dayOfMonth,
                start = transaction.month,
            )
            val linked = transaction.copy(recurringId = recurring.id)
            update { data ->
                data.copy(
                    transactions = data.transactions.filterNot { it.id == linked.id } + linked,
                    recurrings = data.recurrings + recurring,
                )
            }
        } else {
            update { data ->
                val exists = data.transactions.any { it.id == transaction.id }
                data.copy(
                    transactions = if (exists) {
                        data.transactions.map { if (it.id == transaction.id) transaction else it }
                    } else {
                        data.transactions + transaction
                    },
                )
            }
        }
        applyRecurring()

        if (transaction.type == EntryType.EXPENSE) {
            warnIfBudgetCrossed(transaction.categoryId, transaction.month, before)
            warnIfOverallCrossed(transaction.month, overallBefore)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        update { data ->
            data.copy(
                transactions = data.transactions.filterNot { it.id == transaction.id },
                // Si venía de un pago fijo, ese mes no se vuelve a generar.
                recurrings = data.recurrings.map { recurring ->
                    if (recurring.id == transaction.recurringId) {
                        recurring.copy(skippedMonths = recurring.skippedMonths + transaction.month)
                    } else {
                        recurring
                    }
                },
            )
        }
    }

    fun restoreTransaction(transaction: Transaction) {
        update { data ->
            data.copy(
                transactions = data.transactions.filterNot { it.id == transaction.id } + transaction,
                recurrings = data.recurrings.map { recurring ->
                    if (recurring.id == transaction.recurringId) {
                        recurring.copy(skippedMonths = recurring.skippedMonths - transaction.month)
                    } else {
                        recurring
                    }
                },
            )
        }
    }

    /** Deja de generar un pago fijo. Los movimientos ya creados se conservan. */
    fun stopRecurring(id: String) {
        update { data -> data.copy(recurrings = data.recurrings.filterNot { it.id == id }) }
        emit("Pago fijo detenido")
    }

    /* ----------------------------- Categorías ----------------------------- */

    /** Devuelve false si el nombre está vacío o ya existe para ese tipo. */
    fun addCategory(name: String, type: EntryType, group: SpendingGroup): Boolean {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return false
        val current = _data.value.categories
        if (current.any { it.type == type && it.name.equals(clean, ignoreCase = true) }) return false

        val category = FinanceCategory(
            id = "custom-" + UUID.randomUUID().toString(),
            name = clean,
            type = type,
            group = group,
            color = current.size,
        )
        update { it.copy(categories = it.categories + category) }
        return true
    }

    fun updateCategory(category: FinanceCategory) {
        update { data -> data.copy(categories = data.categories.map { if (it.id == category.id) category else it }) }
    }

    /**
     * Borra una categoría propia. Sus movimientos pasan a «Otros gastos» u
     * «Otros ingresos» para que los totales sigan cuadrando.
     */
    fun deleteCategory(category: FinanceCategory) {
        if (category.builtIn) return
        val fallback = if (category.type == EntryType.INCOME) "inc-other" else "exp-other"
        update { data ->
            data.copy(
                categories = data.categories.filterNot { it.id == category.id },
                transactions = data.transactions.map {
                    if (it.categoryId == category.id) it.copy(categoryId = fallback) else it
                },
                recurrings = data.recurrings.map {
                    if (it.categoryId == category.id) it.copy(categoryId = fallback) else it
                },
                budgets = data.budgets - category.id,
            )
        }
        emit("Categoría eliminada")
    }

    /* ---------------------------- Presupuestos ---------------------------- */

    /** [limitCents] null o cero quita el presupuesto. */
    fun setBudget(categoryId: String, limitCents: Long?) {
        update { data ->
            data.copy(
                budgets = if (limitCents == null || limitCents <= 0) {
                    data.budgets - categoryId
                } else {
                    data.budgets + (categoryId to limitCents)
                },
            )
        }
    }

    /** Tope de gasto total del mes. [limitCents] null o cero lo quita. */
    fun setMonthlyLimit(limitCents: Long?) {
        update { it.copy(monthlyLimitCents = limitCents?.takeIf { cents -> cents > 0 }) }
    }

    /* ------------------------------- Metas ------------------------------- */

    fun saveGoal(goal: Goal) {
        update { data ->
            val exists = data.goals.any { it.id == goal.id }
            data.copy(goals = if (exists) data.goals.map { if (it.id == goal.id) goal else it } else data.goals + goal)
        }
    }

    fun deleteGoal(goal: Goal) {
        update { data -> data.copy(goals = data.goals.filterNot { it.id == goal.id }) }
        emit("Meta eliminada")
    }

    /** [amountCents] negativo sirve para retirar dinero de la meta. */
    fun contribute(goal: Goal, amountCents: Long) {
        if (amountCents == 0L) return
        val updated = goal.copy(
            contributions = goal.contributions + GoalContribution(amountCents = amountCents, date = LocalDate.now()),
        )
        saveGoal(updated)
        if (!goal.reached && updated.reached) emit("¡Meta «${goal.name}» conseguida!")
    }

    /* ------------------------------- IA ------------------------------- */

    fun requestAdvice(apiKey: String, model: String) {
        if (apiKey.isBlank()) {
            _advice.value = FinanceAdviceState(error = "Añade tu API key de DeepSeek en Ajustes")
            return
        }
        _advice.update { it.copy(working = true, error = null) }

        viewModelScope.launch {
            val prompt = FinanceAdvisor.buildPrompt(_data.value, _month.value, LocalDate.now())
            val result = runCatching {
                DeepSeekClient(apiKey, AiConfig.normalizeModel(model)).complete(
                    system = FinanceAdvisor.SYSTEM_PROMPT,
                    user = prompt,
                    maxTokens = 1200,
                )
            }
            _advice.value = result.fold(
                onSuccess = { FinanceAdviceState(text = it.trim()) },
                onFailure = { FinanceAdviceState(error = it.message ?: "No se pudo contactar con DeepSeek") },
            )
        }
    }

    fun clearAdvice() {
        _advice.value = FinanceAdviceState()
    }

    /* ----------------------------- Internos ----------------------------- */

    private fun budgetLevel(categoryId: String, month: YearMonth): BudgetLevel? =
        FinanceStats.budgetStatuses(_data.value, month, LocalDate.now())
            .firstOrNull { it.category.id == categoryId }
            ?.level

    private fun warnIfBudgetCrossed(categoryId: String, month: YearMonth, before: BudgetLevel?) {
        val status = FinanceStats.budgetStatuses(_data.value, month, LocalDate.now())
            .firstOrNull { it.category.id == categoryId } ?: return
        if (status.level == before) return

        when (status.level) {
            BudgetLevel.EXCEEDED -> emit(
                "Te pasaste del presupuesto de ${status.category.name} por ${Soles.format(-status.remainingCents)}",
            )
            BudgetLevel.WARNING -> emit(
                "Llevas el ${(status.ratio * 100).toInt()} % del presupuesto de ${status.category.name}",
            )
            BudgetLevel.OK -> Unit
        }
    }

    private fun warnIfOverallCrossed(month: YearMonth, before: BudgetLevel?) {
        val overall = FinanceStats.overallBudget(_data.value, month, LocalDate.now()) ?: return
        if (overall.level == before) return

        when (overall.level) {
            BudgetLevel.EXCEEDED -> emit("Te pasaste del presupuesto general del mes por ${Soles.format(-overall.remainingCents)}")
            BudgetLevel.WARNING -> emit("Llevas el ${(overall.ratio * 100).toInt()} % de tu presupuesto general del mes")
            BudgetLevel.OK -> Unit
        }
    }

    private fun update(transform: (FinanceData) -> FinanceData) {
        _data.update(transform)
        saveQueue.trySend(_data.value)
    }

    private fun emit(message: String) {
        _messages.tryEmit(message)
    }
}
