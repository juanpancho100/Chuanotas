package com.miking.notas.ai

import com.miking.notas.data.ReminderRepeat
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Una nota que la IA propone a partir de un dictado libre.
 *
 * [reminderAt] es la fecha ancla en milisegundos epoch, ya validada: si está
 * en el pasado y la nota no se repite, es null.
 */
data class AiNoteDraft(
    val title: String,
    val body: String,
    val category: String,
    val reminderAt: Long?,
    val repeatDays: Set<Int>,
) {
    val hasReminder: Boolean
        get() = reminderAt != null
}

/** Lo que la IA entendió de un dictado: las notas propuestas y un resumen. */
data class AiPlan(
    val drafts: List<AiNoteDraft>,
    val summary: String,
    val transcript: String,
)

/** La respuesta a una pregunta sobre las notas del usuario. */
data class AiAnswer(
    val question: String,
    val answer: String,
    val sources: List<String>,
)

/**
 * Convierte la respuesta json del modelo en un plan de notas.
 *
 * Descarta lo que no se puede entender: títulos vacíos, fechas imposibles de
 * interpretar y recordatorios de una sola vez que ya han pasado. Las fechas
 * con repetición se conservan aunque el ancla sea pasada, porque el
 * programador de recordatorios las lleva al siguiente día seleccionado.
 */
internal fun parseAiPlan(json: JSONObject, transcript: String, now: Long): AiPlan {
    val array = json.optJSONArray("notes")
    val drafts = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val draft = item.toDraft(now) ?: continue
            add(draft)
        }
    }

    return AiPlan(
        drafts = drafts,
        summary = json.optString("summary").trim(),
        transcript = transcript,
    )
}

private fun JSONObject.toDraft(now: Long): AiNoteDraft? {
    val title = optString("title").trim()
    val body = optString("body").trim()
    if (title.isEmpty() && body.isEmpty()) return null

    val rawDays = optJSONArray("repeatDays")?.let { array ->
        buildSet {
            for (index in 0 until array.length()) {
                val day = array.optInt(index, 0)
                if (day in 1..7) add(day)
            }
        }
    } ?: emptySet()

    val parsedReminder = optIsoMillis("reminderAt")
    val days = ReminderRepeat.sanitize(rawDays)

    val reminder = when {
        parsedReminder == null -> null
        days.isNotEmpty() -> parsedReminder
        parsedReminder > now -> parsedReminder
        else -> null
    }

    return AiNoteDraft(
        title = title.ifEmpty { "Nota" },
        body = body,
        category = optString("category").trim().take(24),
        reminderAt = reminder,
        repeatDays = if (reminder == null) emptySet() else days,
    )
}

/** Lee la respuesta a una pregunta. Si falta el campo answer, se usa el texto plano. */
internal fun parseAiAnswer(json: JSONObject, question: String, fallback: String): AiAnswer {
    val answer = json.optString("answer").trim().ifEmpty { fallback.trim() }

    val sources = buildList {
        val array = json.optJSONArray("sources")
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) add(value)
        }
    }

    return AiAnswer(question = question, answer = answer, sources = sources)
}

/**
 * Interpreta una fecha y hora local que devuelve el modelo.
 *
 * Acepta "2026-10-21T09:00:00", "2026-10-21 09:00", "2026-10-21T09:00:00Z" y
 * "2026-10-21". Una fecha sin hora se coloca a las 09:00, que es el valor por
 * defecto que se le pide al modelo.
 */
internal fun parseLocalDateTime(raw: String): Long? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    val zone = ZoneId.systemDefault()
    val normalized = text.replace(' ', 'T')

    runCatching { OffsetDateTime.parse(normalized) }.getOrNull()?.let {
        return it.toInstant().toEpochMilli()
    }

    runCatching { LocalDateTime.parse(normalized) }.getOrNull()?.let {
        return it.atZone(zone).toInstant().toEpochMilli()
    }

    runCatching { LocalDate.parse(normalized) }.getOrNull()?.let {
        return it.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
    }

    return null
}

private fun JSONObject.optIsoMillis(key: String): Long? {
    val raw = optString(key).trim()
    if (raw.isEmpty() || raw.equals("null", ignoreCase = true)) return null
    return parseLocalDateTime(raw)
}
