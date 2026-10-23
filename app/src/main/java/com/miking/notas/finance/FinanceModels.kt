package com.miking.notas.finance

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class EntryType { EXPENSE, INCOME }

/**
 * Para la regla 50/30/20: en qué bolsa cae cada categoría de gasto.
 * Las de ingreso no lo usan.
 */
enum class SpendingGroup(val label: String) {
    NEED("Necesidad"),
    WANT("Gusto"),
    SAVING("Ahorro"),
}

/**
 * Categoría de finanzas, independiente de las categorías de notas.
 * [builtIn] marca las que vienen de fábrica: se pueden ocultar del uso pero
 * no borrar, para que los movimientos antiguos no se queden huérfanos.
 */
data class FinanceCategory(
    val id: String,
    val name: String,
    val type: EntryType,
    val group: SpendingGroup = SpendingGroup.NEED,
    val color: Int = 0,
    val builtIn: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        put("group", group.name)
        put("color", color)
        if (builtIn) put("builtIn", true)
    }

    companion object {
        fun fromJson(json: JSONObject): FinanceCategory? {
            val id = json.optString("id").ifBlank { return null }
            val name = json.optString("name").trim().ifBlank { return null }
            return FinanceCategory(
                id = id,
                name = name,
                type = enumOrNull<EntryType>(json.optString("type")) ?: EntryType.EXPENSE,
                group = enumOrNull<SpendingGroup>(json.optString("group")) ?: SpendingGroup.NEED,
                color = json.optInt("color", 0),
                builtIn = json.optBoolean("builtIn", false),
            )
        }
    }
}

/** Un gasto o ingreso. [date] es el día en que ocurrió. */
data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val type: EntryType,
    val amountCents: Long,
    val categoryId: String,
    val note: String = "",
    val date: LocalDate,
    /** Si lo generó un pago fijo mensual, su id. */
    val recurringId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val month: YearMonth get() = YearMonth.from(date)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("amount", amountCents)
        put("category", categoryId)
        if (note.isNotBlank()) put("note", note)
        put("date", date.toString())
        recurringId?.let { put("recurring", it) }
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Transaction? {
            val amount = json.optLong("amount", 0L).takeIf { it > 0 } ?: return null
            val date = runCatching { LocalDate.parse(json.optString("date")) }.getOrNull() ?: return null
            return Transaction(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                type = enumOrNull<EntryType>(json.optString("type")) ?: EntryType.EXPENSE,
                amountCents = amount,
                categoryId = json.optString("category"),
                note = json.optString("note"),
                date = date,
                recurringId = json.optString("recurring").ifBlank { null },
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            )
        }
    }
}

/**
 * Pago o cobro fijo que se repite cada mes el día [dayOfMonth]
 * (alquiler, internet, sueldo…). Genera un [Transaction] por mes.
 */
data class Recurring(
    val id: String = UUID.randomUUID().toString(),
    val type: EntryType,
    val amountCents: Long,
    val categoryId: String,
    val note: String = "",
    val dayOfMonth: Int,
    /** Primer mes en el que aplica. */
    val start: YearMonth,
    val active: Boolean = true,
    /** Meses cuyo movimiento generado se borró a mano: no se vuelven a crear. */
    val skippedMonths: Set<YearMonth> = emptySet(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("amount", amountCents)
        put("category", categoryId)
        if (note.isNotBlank()) put("note", note)
        put("day", dayOfMonth)
        put("start", start.toString())
        put("active", active)
        if (skippedMonths.isNotEmpty()) {
            put("skipped", JSONArray().apply { skippedMonths.sorted().forEach { put(it.toString()) } })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Recurring? {
            val amount = json.optLong("amount", 0L).takeIf { it > 0 } ?: return null
            val start = runCatching { YearMonth.parse(json.optString("start")) }.getOrNull() ?: return null
            return Recurring(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                type = enumOrNull<EntryType>(json.optString("type")) ?: EntryType.EXPENSE,
                amountCents = amount,
                categoryId = json.optString("category"),
                note = json.optString("note"),
                dayOfMonth = json.optInt("day", 1).coerceIn(1, 31),
                start = start,
                active = json.optBoolean("active", true),
                skippedMonths = json.optJSONArray("skipped")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        runCatching { YearMonth.parse(array.optString(index)) }.getOrNull()
                    }.toSet()
                }.orEmpty(),
            )
        }
    }
}

data class GoalContribution(
    val id: String = UUID.randomUUID().toString(),
    val amountCents: Long,
    val date: LocalDate,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("amount", amountCents)
        .put("date", date.toString())

    companion object {
        fun fromJson(json: JSONObject): GoalContribution? {
            val date = runCatching { LocalDate.parse(json.optString("date")) }.getOrNull() ?: return null
            val amount = json.optLong("amount", 0L).takeIf { it != 0L } ?: return null
            return GoalContribution(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                amountCents = amount,
                date = date,
            )
        }
    }
}

/** Una meta de ahorro, por ejemplo «Moto» por S/ 8,000 para diciembre. */
data class Goal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetCents: Long,
    val targetDate: LocalDate? = null,
    val contributions: List<GoalContribution> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val savedCents: Long get() = contributions.sumOf { it.amountCents }.coerceAtLeast(0)
    val remainingCents: Long get() = (targetCents - savedCents).coerceAtLeast(0)
    val progress: Float get() = if (targetCents <= 0) 0f else (savedCents.toFloat() / targetCents).coerceIn(0f, 1f)
    val reached: Boolean get() = savedCents >= targetCents

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("target", targetCents)
        targetDate?.let { put("targetDate", it.toString()) }
        put("contributions", JSONArray().apply { contributions.forEach { put(it.toJson()) } })
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Goal? {
            val name = json.optString("name").trim().ifBlank { return null }
            val target = json.optLong("target", 0L).takeIf { it > 0 } ?: return null
            val contributions = json.optJSONArray("contributions")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let(GoalContribution::fromJson)
                }
            }.orEmpty()
            return Goal(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = name,
                targetCents = target,
                targetDate = json.optString("targetDate").ifBlank { null }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                contributions = contributions,
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            )
        }
    }
}

/** Todo lo que se guarda en finance.json. [budgets] va de id de categoría a límite mensual. */
data class FinanceData(
    val categories: List<FinanceCategory> = DefaultFinanceCategories.all,
    val transactions: List<Transaction> = emptyList(),
    val recurrings: List<Recurring> = emptyList(),
    val budgets: Map<String, Long> = emptyMap(),
    val goals: List<Goal> = emptyList(),
) {
    fun category(id: String): FinanceCategory? = categories.firstOrNull { it.id == id }

    fun toJson(): JSONObject = JSONObject().apply {
        put("format", 1)
        put("categories", JSONArray().apply { categories.forEach { put(it.toJson()) } })
        put("transactions", JSONArray().apply { transactions.forEach { put(it.toJson()) } })
        put("recurrings", JSONArray().apply { recurrings.forEach { put(it.toJson()) } })
        put("budgets", JSONObject().apply { budgets.forEach { (id, cents) -> put(id, cents) } })
        put("goals", JSONArray().apply { goals.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JSONObject): FinanceData {
            val stored = json.optJSONArray("categories").mapObjects(FinanceCategory::fromJson)
            // Las categorías de fábrica nuevas aparecen aunque el archivo sea antiguo.
            val categories = stored + DefaultFinanceCategories.all.filter { default ->
                stored.none { it.id == default.id }
            }
            val budgets = json.optJSONObject("budgets")?.let { obj ->
                obj.keys().asSequence()
                    .mapNotNull { key -> obj.optLong(key, 0L).takeIf { it > 0 }?.let { key to it } }
                    .toMap()
            }.orEmpty()

            return FinanceData(
                categories = categories,
                transactions = json.optJSONArray("transactions").mapObjects(Transaction::fromJson),
                recurrings = json.optJSONArray("recurrings").mapObjects(Recurring::fromJson),
                budgets = budgets,
                goals = json.optJSONArray("goals").mapObjects(Goal::fromJson),
            )
        }
    }
}

object DefaultFinanceCategories {
    val all: List<FinanceCategory> = listOf(
        expense("exp-food", "Comida", SpendingGroup.NEED, 0),
        expense("exp-transport", "Transporte", SpendingGroup.NEED, 1),
        expense("exp-home", "Vivienda", SpendingGroup.NEED, 2),
        expense("exp-services", "Servicios", SpendingGroup.NEED, 3),
        expense("exp-health", "Salud", SpendingGroup.NEED, 4),
        expense("exp-education", "Educación", SpendingGroup.NEED, 5),
        expense("exp-fun", "Ocio", SpendingGroup.WANT, 6),
        expense("exp-shopping", "Compras", SpendingGroup.WANT, 7),
        expense("exp-subscriptions", "Suscripciones", SpendingGroup.WANT, 8),
        expense("exp-savings", "Ahorro e inversión", SpendingGroup.SAVING, 9),
        expense("exp-other", "Otros gastos", SpendingGroup.WANT, 10),
        income("inc-salary", "Sueldo", 0),
        income("inc-extra", "Trabajos extra", 1),
        income("inc-sales", "Ventas", 2),
        income("inc-other", "Otros ingresos", 3),
    )

    private fun expense(id: String, name: String, group: SpendingGroup, color: Int) =
        FinanceCategory(id, name, EntryType.EXPENSE, group, color, builtIn = true)

    private fun income(id: String, name: String, color: Int) =
        FinanceCategory(id, name, EntryType.INCOME, SpendingGroup.NEED, color, builtIn = true)
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }

private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
}
