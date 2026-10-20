package com.miking.notas.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Días ISO: 1 = lunes, 7 = domingo. */
object ReminderRepeat {

    val ALL_DAYS: Set<Int> = (1..7).toSet()
    val WEEKDAYS: Set<Int> = (1..5).toSet()
    val WEEKEND: Set<Int> = setOf(6, 7)

    fun sanitize(days: Set<Int>): Set<Int> =
        days.filterTo(linkedSetOf()) { it in 1..7 }

    /**
     * Calcula la próxima vez que debe sonar una nota.
     *
     * [anchor] conserva la fecha y la hora elegidas originalmente. Si la nota
     * se repite, solo importan la hora y los días seleccionados; la fecha sirve
     * como primer día posible.
     */
    fun nextOccurrence(anchor: Long, days: Set<Int>, now: Long): Long? {
        val repeatDays = sanitize(days)
        if (repeatDays.isEmpty()) return anchor.takeIf { it > now }

        val zone = ZoneId.systemDefault()
        val anchorDateTime = Instant.ofEpochMilli(anchor).atZone(zone)
        val anchorDate = anchorDateTime.toLocalDate()
        val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var date = if (anchorDate.isAfter(nowDate)) anchorDate else nowDate

        // Con días semanales, en 8 intentos siempre se encuentra el siguiente.
        repeat(8) {
            if (date.dayOfWeek.value in repeatDays) {
                val candidate = date
                    .atTime(anchorDateTime.toLocalTime())
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()

                if (candidate > now) return candidate
            }
            date = date.plusDays(1)
        }

        return null
    }

    /** Devuelve la hora del recordatorio si cae en [date], o null si no. */
    fun occurrenceOn(anchor: Long, days: Set<Int>, date: LocalDate): Long? {
        val repeatDays = sanitize(days)
        val zone = ZoneId.systemDefault()
        val anchorDateTime = Instant.ofEpochMilli(anchor).atZone(zone)

        if (repeatDays.isEmpty()) {
            val anchorDate = anchorDateTime.toLocalDate()
            return anchor.takeIf { anchorDate == date }
        }

        if (date.isBefore(anchorDateTime.toLocalDate())) return null
        if (date.dayOfWeek.value !in repeatDays) return null

        return date
            .atTime(anchorDateTime.toLocalTime())
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }
}

data class NoteOccurrence(
    val note: Note,
    val at: Long,
)

fun Note.nextReminderAt(now: Long = System.currentTimeMillis()): Long? {
    val anchor = reminderAt ?: return null
    return ReminderRepeat.nextOccurrence(anchor, repeatDays, now)
}

fun Note.reminderAtOn(date: LocalDate): Long? {
    val anchor = reminderAt ?: return null
    return ReminderRepeat.occurrenceOn(anchor, repeatDays, date)
}

fun Note.occurrencesOn(date: LocalDate): NoteOccurrence? {
    val at = reminderAtOn(date) ?: return null
    return NoteOccurrence(note = this, at = at)
}

val Note.isRepeating: Boolean
    get() = repeatDays.isNotEmpty()
