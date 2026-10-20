package com.miking.notas.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Una nota guardada en el dispositivo.
 *
 * [category] es el nombre de la categoría, o vacío si no tiene.
 * [color] es el índice de la paleta (0 = color del tema, 1..5 = pasteles).
 * [reminderAt] es la marca de tiempo del recordatorio, o null si no tiene.
 * [repeatDays] contiene los días ISO (1 = lunes, 7 = domingo) en los que se
 * repite. Vacío significa que suena una sola vez.
 */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val category: String = "",
    val color: Int = 0,
    val pinned: Boolean = false,
    val reminderAt: Long? = null,
    val repeatDays: Set<Int> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val displayTitle: String
        get() = title.trim().ifBlank { "Nota sin título" }

    val preview: String
        get() = body.trim().replace(Regex("\\s+"), " ")

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("body", body)
        if (category.isNotBlank()) put("category", category.trim())
        put("color", color)
        put("pinned", pinned)
        if (reminderAt != null) put("reminderAt", reminderAt)
        if (reminderAt != null && repeatDays.isNotEmpty()) {
            put(
                "repeatDays",
                JSONArray().apply {
                    repeatDays.sorted().forEach { put(it) }
                },
            )
        }
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Note {
            val reminderAt = if (json.has("reminderAt") && !json.isNull("reminderAt")) {
                json.optLong("reminderAt").takeIf { it > 0L }
            } else {
                null
            }

            return Note(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = json.optString("title"),
                body = json.optString("body"),
                category = json.optString("category").trim(),
                color = json.optInt("color", 0).coerceIn(0, 5),
                pinned = json.optBoolean("pinned", false),
                reminderAt = reminderAt,
                repeatDays = if (reminderAt == null) {
                    emptySet()
                } else {
                    json.optJSONArray("repeatDays")?.let { array ->
                        buildSet {
                            for (index in 0 until array.length()) {
                                val day = array.optInt(index, 0)
                                if (day in 1..7) add(day)
                            }
                        }
                    } ?: emptySet()
                },
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
    }
}
