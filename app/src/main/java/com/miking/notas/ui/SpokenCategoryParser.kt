package com.miking.notas.ui

import com.miking.notas.data.CategoryNames
import java.text.Normalizer
import java.util.Locale

/**
 * Interpreta la categoría dictada en el cuarto paso de la nota por voz.
 * Devuelve el nombre existente que coincide sin distinguir mayúsculas, el
 * nombre limpio si hay que crearlo, o null si se ha pedido omitir la categoría.
 */
internal fun resolveSpokenCategory(
    raw: String,
    existing: Iterable<String>,
): String? {
    val text = raw.trim()
    if (text.isEmpty() || isSkipCategoryCommand(text)) return null

    val withoutPrefix = CATEGORY_PREFIX_REGEX.replace(text, "")
        .trim()
        .trim('"', '\'', '.', ',', ';', ':', '-')
        .trim()

    return CategoryNames.resolve(existing, withoutPrefix)
}

private fun isSkipCategoryCommand(raw: String): Boolean {
    val text = normalizeCommand(raw)
    if (text.isBlank()) return false

    val exact = setOf(
        "no",
        "no gracias",
        "ninguna",
        "ninguno",
        "nada",
        "omitir",
        "saltar",
        "sin categoria",
        "sin etiqueta",
        "sin grupo",
        "no categoria",
        "no etiqueta",
        "no quiero",
    )
    if (text in exact) return true

    return text.contains("sin categoria") ||
        text.contains("sin etiqueta") ||
        text.contains("sin grupo") ||
        text.contains("omitir categoria") ||
        text.contains("omitir etiqueta") ||
        text.contains("no quiero categoria")
}

private fun normalizeCommand(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Quita fórmulas habituales como «la categoría es…», «para la categoría…»
 * o «categoría: …» sin alterar el nombre dictado.
 */
private val CATEGORY_PREFIX_REGEX = Regex(
    "(?i)^\\s*(?:(?:pon|ponme|asigna|asignar|guarda|guardar|quiero|es|sera|de|para|en)\\s+)?" +
        "(?:(?:la|el|una|un|mi|nueva|nuevo)\\s+)?" +
        "(?:categor[ií]a|etiqueta|grupo)" +
        "(?:\\s+(?:es|de|para|llamada|llamado|sea))?\\s*[:,-]?\\s*",
)
