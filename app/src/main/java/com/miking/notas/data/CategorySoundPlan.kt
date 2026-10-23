package com.miking.notas.data

/**
 * Reparte los tonos disponibles entre las categorías.
 *
 * Conserva los sonidos ya elegidos o asignados y completa solo las categorías
 * que aún no tienen uno. Mientras haya tonos libres, cada categoría recibe uno
 * distinto. Si el teléfono tiene menos tonos que categorías, usa [fallback] para
 * las restantes; después se puede cambiar el sonido a mano.
 */
internal fun planCategorySounds(
    categories: List<String>,
    existing: Map<String, String>,
    available: List<String>,
    fallback: String,
): Map<String, String> {
    val ordered = CategoryNames.normalize(categories)
    val candidates = available
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    val used = mutableSetOf<String>()
    val result = linkedMapOf<String, String>()

    ordered.forEach { category ->
        val sound = existing.entries
            .firstOrNull { it.key.equals(category, ignoreCase = true) }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (sound != null) {
            result[category] = sound
            used += sound
        }
    }

    ordered.forEach { category ->
        if (result.containsKey(category)) return@forEach

        val sound = candidates.firstOrNull { it !in used } ?: fallback
        result[category] = sound
        used += sound
    }

    return result
}
