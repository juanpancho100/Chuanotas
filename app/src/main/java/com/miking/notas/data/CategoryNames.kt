package com.miking.notas.data

/**
 * Reglas sencillas para los nombres de categoría:
 * sin espacios sobrantes, sin duplicados y ordenados alfabéticamente.
 */
object CategoryNames {

    fun clean(value: String): String? = value
        .trim()
        .replace(Regex("\\s+"), " ")
        .takeIf { it.isNotEmpty() }

    fun contains(values: Iterable<String>, name: String): Boolean =
        values.any { it.equals(name, ignoreCase = true) }

    /**
     * Devuelve la categoría existente que coincide sin distinguir mayúsculas
     * o, si no existe, el nombre limpio para crearla.
     */
    fun resolve(values: Iterable<String>, name: String): String? {
        val clean = clean(name) ?: return null
        return values.firstOrNull { it.equals(clean, ignoreCase = true) } ?: clean
    }

    fun normalize(values: Iterable<String>): List<String> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()

        values.forEach { raw ->
            val clean = clean(raw) ?: return@forEach
            if (seen.add(clean.lowercase())) result += clean
        }

        return result.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
}
