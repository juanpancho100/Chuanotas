package com.miking.notas.finance

import java.text.Normalizer
import java.time.LocalDate

/** Lo que se entendió de un dictado como «gasté 25 soles en comida». */
data class SpokenEntry(
    val type: EntryType,
    val amountCents: Long?,
    val categoryId: String?,
    val note: String,
    val date: LocalDate,
)

/**
 * Interpreta frases cortas para registrar un movimiento sin teclear.
 * Ejemplos: «gasté 25 en almuerzo», «ayer pagué 120 de luz»,
 * «me pagaron 1500 de sueldo», «taxi 12 con 50».
 */
object SpokenEntryParser {

    private val incomeWords = listOf(
        "cobre", "recibi", "me pagaron", "me depositaron", "ingreso", "gane", "vendi", "sueldo", "salario",
    )

    /** Palabras sueltas que apuntan a una categoría de fábrica. */
    private val keywords: Map<String, List<String>> = mapOf(
        "exp-food" to listOf(
            "comida", "almuerzo", "desayuno", "cena", "menu", "restaurante", "pollo", "mercado",
            "super", "supermercado", "pan", "cafe", "snack", "delivery", "chifa",
        ),
        "exp-transport" to listOf(
            "taxi", "pasaje", "bus", "combi", "uber", "gasolina", "combustible", "metro", "moto", "peaje",
            "transporte", "cabify", "didi",
        ),
        "exp-home" to listOf("alquiler", "renta", "casa", "departamento", "vivienda", "mantenimiento"),
        "exp-services" to listOf("luz", "agua", "internet", "telefono", "celular", "gas", "cable", "recarga", "servicio"),
        "exp-health" to listOf("farmacia", "medicina", "doctor", "medico", "clinica", "salud", "pastillas", "dentista"),
        "exp-education" to listOf("curso", "libro", "universidad", "colegio", "pension", "matricula", "clase"),
        "exp-fun" to listOf("cine", "fiesta", "salida", "cerveza", "trago", "juego", "concierto", "viaje", "ocio"),
        "exp-shopping" to listOf("ropa", "zapatillas", "compra", "regalo", "tienda", "celular nuevo"),
        "exp-subscriptions" to listOf("netflix", "spotify", "suscripcion", "disney", "youtube", "prime", "hbo"),
        "exp-savings" to listOf("ahorro", "inversion", "ahorre", "invertir"),
        "inc-salary" to listOf("sueldo", "salario", "quincena", "planilla"),
        "inc-extra" to listOf("cachuelo", "extra", "freelance", "proyecto", "chamba"),
        "inc-sales" to listOf("vendi", "venta", "ventas"),
    )

    fun parse(
        text: String,
        categories: List<FinanceCategory>,
        today: LocalDate = LocalDate.now(),
    ): SpokenEntry {
        val normalized = normalize(text)
        val type = if (incomeWords.any { normalized.contains(it) }) EntryType.INCOME else EntryType.EXPENSE

        val date = when {
            Regex("""\banteayer\b""").containsMatchIn(normalized) -> today.minusDays(2)
            Regex("""\bayer\b""").containsMatchIn(normalized) -> today.minusDays(1)
            else -> today
        }

        return SpokenEntry(
            type = type,
            amountCents = parseAmount(normalized),
            categoryId = matchCategory(normalized, type, categories),
            note = text.trim().replaceFirstChar { it.uppercase() },
            date = date,
        )
    }

    /** «25», «25.50», «25 con 50», «12 soles con 30 centimos», «1,200». */
    internal fun parseAmount(normalized: String): Long? {
        val withCents = Regex("""(\d+)\s*(?:soles|sol|lucas)?\s*con\s*(\d{1,2})""").find(normalized)
        if (withCents != null) {
            val whole = withCents.groupValues[1].toLongOrNull() ?: return null
            val cents = withCents.groupValues[2].padEnd(2, '0').toLong()
            return whole * 100 + cents
        }

        val number = Regex("""\d[\d.,]*""").findAll(normalized)
            .map { it.value.trimEnd('.', ',') }
            .firstOrNull { it.isNotEmpty() }
            ?: return null
        return Soles.parse(number)
    }

    private fun matchCategory(
        normalized: String,
        type: EntryType,
        categories: List<FinanceCategory>,
    ): String? {
        val candidates = categories.filter { it.type == type }

        // Primero por nombre: así funcionan también las categorías propias.
        candidates.firstOrNull { category ->
            val name = normalize(category.name)
            name.isNotBlank() && Regex("""\b${Regex.escape(name)}\b""").containsMatchIn(normalized)
        }?.let { return it.id }

        return keywords.entries.firstOrNull { (id, words) ->
            candidates.any { it.id == id } &&
                words.any { word -> Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(normalized) }
        }?.key
    }

    internal fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace("s/", " ")
            .trim()
}
