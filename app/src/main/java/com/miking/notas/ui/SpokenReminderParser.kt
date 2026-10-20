package com.miking.notas.ui

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

/**
 * Interpreta expresiones habituales en español para un recordatorio:
 * «mañana a las 9», «el viernes a las 18:30», «25 de diciembre a las 10»,
 * «en 2 horas» o «en 3 días».
 *
 * Devuelve milisegundos epoch, o null si no se reconoce una fecha y hora.
 */
internal fun parseSpokenReminder(
    raw: String,
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    val text = normalizeSpokenText(raw)
    if (text.isBlank()) return null

    parseSubDayRelative(text, nowMillis)?.let { return it }

    val time = parseSpokenTime(text)
    val date = parseSpokenDate(text)

    if (date == null && time == null) return null
    if (date != null && time == null && date !is DateSpec.DayOffset) return null

    return buildReminder(date, time, nowMillis)
}

internal fun isSkipReminderCommand(raw: String): Boolean {
    val text = normalizeSpokenText(raw)
    if (text.isBlank()) return false

    val exact = setOf(
        "no",
        "no gracias",
        "ninguno",
        "ninguna",
        "omitir",
        "saltar",
        "sin recordatorio",
        "sin alarma",
        "sin aviso",
        "no recordatorio",
        "nada",
        "no quiero",
    )
    if (text in exact) return true

    return text.contains("sin recordatorio") ||
        text.contains("sin alarma") ||
        text.contains("sin aviso") ||
        text.contains("no recordatorio") ||
        text.contains("no quiero recordatorio") ||
        text.contains("omitir recordatorio")
}

/* ------------------------------- Fecha/hora ------------------------------- */

private sealed interface DateSpec {
    data object Today : DateSpec
    data class DayOffset(val days: Int) : DateSpec
    data class Weekday(val dayOfWeek: Int, val forceNextWeek: Boolean) : DateSpec
    data class DayMonth(
        val day: Int,
        val month: Int,
        val year: Int?,
        val yearExplicit: Boolean,
    ) : DateSpec

    data class DayOnly(val day: Int) : DateSpec
}

private data class SpokenTime(val hour: Int, val minute: Int)

private fun buildReminder(
    date: DateSpec?,
    time: SpokenTime?,
    nowMillis: Long,
): Long? {
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }

    when (date) {
        null -> {
            val target = time ?: return null
            cal.setTimeFields(target)
            if (cal.timeInMillis <= nowMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        DateSpec.Today -> {
            val target = time ?: return null
            cal.setTimeFields(target)
            if (cal.timeInMillis <= nowMillis) return null
        }

        is DateSpec.DayOffset -> {
            cal.add(Calendar.DAY_OF_YEAR, date.days)
            if (time != null) {
                cal.setTimeFields(time)
            } else {
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
        }

        is DateSpec.Weekday -> {
            val target = time ?: return null
            val today = cal.get(Calendar.DAY_OF_WEEK)
            var delta = (date.dayOfWeek - today + 7) % 7
            if (delta == 0 && date.forceNextWeek) delta = 7

            cal.add(Calendar.DAY_OF_YEAR, delta)
            cal.setTimeFields(target)
            if (cal.timeInMillis <= nowMillis) cal.add(Calendar.DAY_OF_YEAR, 7)
        }

        is DateSpec.DayMonth -> {
            val target = time ?: return null
            var year = date.year ?: now.get(Calendar.YEAR)

            if (!cal.setDate(year, date.month, date.day)) {
                if (date.year != null) return null
                year += 1
                if (!cal.setDate(year, date.month, date.day)) return null
            }
            cal.setTimeFields(target)

            if (cal.timeInMillis <= nowMillis) {
                if (date.yearExplicit) return null
                year += 1
                if (!cal.setDate(year, date.month, date.day)) return null
                cal.setTimeFields(target)
            }
        }

        is DateSpec.DayOnly -> {
            val target = time ?: return null
            var year = now.get(Calendar.YEAR)
            var month = now.get(Calendar.MONTH)

            if (!cal.setDate(year, month, date.day)) {
                month += 1
                if (month > 11) {
                    month = 0
                    year += 1
                }
                if (!cal.setDate(year, month, date.day)) return null
            }
            cal.setTimeFields(target)

            if (cal.timeInMillis <= nowMillis) {
                month += 1
                if (month > 11) {
                    month = 0
                    year += 1
                }
                if (!cal.setDate(year, month, date.day)) return null
                cal.setTimeFields(target)
                if (cal.timeInMillis <= nowMillis) return null
            }
        }
    }

    return cal.timeInMillis.takeIf { it > nowMillis }
}

private fun Calendar.setTimeFields(time: SpokenTime) {
    set(Calendar.HOUR_OF_DAY, time.hour)
    set(Calendar.MINUTE, time.minute)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun Calendar.setDate(year: Int, month: Int, day: Int): Boolean {
    val test = Calendar.getInstance().apply {
        isLenient = false
        clear()
        set(year, month, day)
    }

    return try {
        test.timeInMillis
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
}

private fun parseSubDayRelative(text: String, nowMillis: Long): Long? {
    if (MEDIA_HORA_REGEX.containsMatchIn(text)) {
        return nowMillis + 30 * 60_000L
    }

    HOUR_AND_HALF_REGEX.find(text)?.let { match ->
        val amount = parseNumberToken(match.groupValues[1]) ?: return@let
        return nowMillis + (amount * 60 + 30) * 60_000L
    }

    val match = SUB_DAY_RELATIVE_REGEX.find(text) ?: return null
    val amount = parseNumberToken(match.groupValues[1]) ?: return null
    val minutes = if (match.groupValues[2].startsWith("hora")) {
        amount * 60
    } else {
        amount
    }
    return nowMillis + minutes * 60_000L
}

private fun parseSpokenDate(text: String): DateSpec? {
    val dateText = PERIOD_REGEX.replace(text, " ")

    RELATIVE_DAYS_REGEX.find(dateText)?.let { match ->
        val amount = parseNumberToken(match.groupValues[1]) ?: return@let
        val unit = match.groupValues[2]
        val days = if (unit.startsWith("semana")) amount * 7 else amount
        return DateSpec.DayOffset(days)
    }

    if (ESTA_PERIOD_REGEX.containsMatchIn(text)) return DateSpec.Today
    if (dateText.contains("pasado manana")) return DateSpec.DayOffset(2)
    if (STANDALONE_MANANA_REGEX.containsMatchIn(dateText)) return DateSpec.DayOffset(1)
    if (HOY_REGEX.containsMatchIn(dateText)) return DateSpec.Today

    WEEKDAY_REGEX.find(dateText)?.let { match ->
        val day = WEEKDAY_MAP[match.groupValues[1]] ?: return@let
        val forceNextWeek = NEXT_WEEK_REGEX.containsMatchIn(dateText)
        return DateSpec.Weekday(day, forceNextWeek)
    }

    NUMERIC_DATE_REGEX.find(dateText)?.let { match ->
        val day = match.groupValues[1].toIntOrNull() ?: return@let
        val month = (match.groupValues[2].toIntOrNull() ?: return@let) - 1
        val yearRaw = match.groupValues[3]
        val year = yearRaw.toIntOrNull()?.let { if (it < 100) 2000 + it else it }
        if (day in 1..31 && month in 0..11) {
            return DateSpec.DayMonth(day, month, year, year != null)
        }
    }

    MONTH_DATE_REGEX.find(dateText)?.let { match ->
        val day = match.groupValues[1].toIntOrNull() ?: return@let
        val month = MONTH_MAP[match.groupValues[2]] ?: return@let
        val year = match.groupValues[3].toIntOrNull()
        if (day in 1..31) {
            return DateSpec.DayMonth(day, month, year, year != null)
        }
    }

    DAY_ONLY_REGEX.find(dateText)?.let { match ->
        val day = match.groupValues[1].toIntOrNull() ?: return@let
        if (day in 1..31) return DateSpec.DayOnly(day)
    }

    return null
}

private fun parseSpokenTime(text: String): SpokenTime? {
    val period = PERIOD_REGEX.find(text)?.groupValues?.get(1)
    val meridiem = normalizeMeridiem(MERIDIEM_REGEX.find(text)?.groupValues?.get(1))

    A_LAS_REGEX.find(text)?.let { match ->
        val hour = parseNumberToken(match.groupValues[1]) ?: return null
        var minute = match.groupValues[2].toIntOrNull() ?: 0

        when {
            LESS_QUARTER_REGEX.containsMatchIn(text) -> {
                return makeTime((hour + 23) % 24, 45, meridiem, period)
            }

            QUARTER_REGEX.containsMatchIn(text) -> minute = 15
            HALF_REGEX.containsMatchIn(text) -> minute = 30
            else -> {
                Y_MINUTE_REGEX.find(text)?.let { minuteMatch ->
                    minute = parseNumberToken(minuteMatch.groupValues[1]) ?: minute
                }
            }
        }

        return makeTime(hour, minute, meridiem, period)
    }

    STANDALONE_MERIDIEM_REGEX.find(text)?.let { match ->
        val hour = parseNumberToken(match.groupValues[1]) ?: return null
        return makeTime(hour, 0, normalizeMeridiem(match.groupValues[2]), period)
    }

    COLON_TIME_REGEX.find(text)?.let { match ->
        val hour = parseNumberToken(match.groupValues[1]) ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return makeTime(hour, minute, meridiem, period)
    }

    HOUR_H_REGEX.find(text)?.let { match ->
        val hour = parseNumberToken(match.groupValues[1]) ?: return null
        return makeTime(hour, 0, meridiem, period)
    }

    HOUR_PERIOD_REGEX.find(text)?.let { match ->
        val hour = parseNumberToken(match.groupValues[1]) ?: return null
        return makeTime(hour, 0, meridiem, match.groupValues[2])
    }

    if (NOON_REGEX.containsMatchIn(text)) return makeTime(12, 0, meridiem, period)
    if (MIDNIGHT_REGEX.containsMatchIn(text)) return makeTime(0, 0, meridiem, period)

    return null
}

private fun makeTime(
    hour: Int,
    minute: Int,
    meridiem: String?,
    period: String?,
): SpokenTime? {
    if (hour !in 0..23 || minute !in 0..59) return null

    val adjustedHour = applyPeriod(applyMeridiem(hour, meridiem), period)
    if (adjustedHour !in 0..23) return null

    return SpokenTime(adjustedHour, minute)
}

private fun applyMeridiem(hour: Int, meridiem: String?): Int = when (meridiem) {
    "am" -> if (hour == 12) 0 else hour
    "pm" -> if (hour in 1..11) hour + 12 else hour
    else -> hour
}

private fun applyPeriod(hour: Int, period: String?): Int = when (period) {
    "tarde" -> if (hour in 1..11) hour + 12 else hour
    "noche" -> when (hour) {
        12 -> 0
        in 1..11 -> hour + 12
        else -> hour
    }

    "madrugada" -> if (hour == 12) 0 else hour
    else -> hour
}

private fun normalizeMeridiem(raw: String?): String? {
    val value = raw
        ?.replace(Regex("[\\s.]"), "")
        ?.lowercase(Locale.ROOT)
        ?: return null

    return when (value) {
        "am" -> "am"
        "pm" -> "pm"
        else -> null
    }
}

private fun parseNumberToken(token: String): Int? {
    token.toIntOrNull()?.let { return it }
    return NUMBER_WORDS[token]
}

private fun normalizeSpokenText(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9:/.\\s-]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/* ------------------------------- Patrones -------------------------------- */

private val NUMBER_WORDS = mapOf(
    "cero" to 0,
    "un" to 1,
    "una" to 1,
    "uno" to 1,
    "dos" to 2,
    "tres" to 3,
    "cuatro" to 4,
    "cinco" to 5,
    "seis" to 6,
    "siete" to 7,
    "ocho" to 8,
    "nueve" to 9,
    "diez" to 10,
    "once" to 11,
    "doce" to 12,
    "trece" to 13,
    "catorce" to 14,
    "quince" to 15,
    "dieciseis" to 16,
    "diecisiete" to 17,
    "dieciocho" to 18,
    "diecinueve" to 19,
    "veinte" to 20,
    "veintiuno" to 21,
    "veintiuna" to 21,
    "veintiun" to 21,
    "veintidos" to 22,
    "veintitres" to 23,
    "veinticuatro" to 24,
    "veinticinco" to 25,
    "veintiseis" to 26,
    "veintisiete" to 27,
    "veintiocho" to 28,
    "veintinueve" to 29,
    "treinta" to 30,
    "cuarenta" to 40,
    "cincuenta" to 50,
)

private const val NUMBER_WORD_PATTERN =
    "veinticinco|veinticuatro|veintitres|veintidos|veintiuno|veintiuna|veintiun|veintinueve|" +
        "veintiocho|" +
        "veintisiete|veintiseis|veinte|diecinueve|dieciocho|diecisiete|dieciseis|quince|catorce|" +
        "trece|doce|once|diez|nueve|ocho|siete|seis|cinco|cuatro|tres|dos|una|uno|un|cero|" +
        "treinta|cuarenta|cincuenta"

private val NUMBER_PATTERN = "(?:\\d{1,3}|$NUMBER_WORD_PATTERN)"
private val HOUR_PATTERN = "(?:\\d{1,2}|$NUMBER_WORD_PATTERN)"
private val MINUTE_PATTERN = "(?:\\d{1,2}|$NUMBER_WORD_PATTERN)"
private val PERIOD_PATTERN = "(manana|tarde|noche|madrugada)"
private val MERIDIEM_PATTERN = "(a\\s*\\.?\\s*m\\.?|p\\s*\\.?\\s*m\\.?)"

private val PERIOD_REGEX = Regex(
    "\\b(?:(?:de|por)\\s+la|esta)\\s+$PERIOD_PATTERN\\b",
)

private val MERIDIEM_REGEX = Regex("\\b$MERIDIEM_PATTERN\\b")

private val MEDIA_HORA_REGEX = Regex("\\b(?:en|dentro de)\\s+media\\s+hora\\b")
private val HOUR_AND_HALF_REGEX = Regex(
    "\\b(?:en|dentro de)\\s+($NUMBER_PATTERN)\\s+horas?\\s+y\\s+media\\b",
)
private val SUB_DAY_RELATIVE_REGEX = Regex(
    "\\b(?:en|dentro de)\\s+($NUMBER_PATTERN)\\s+(minuto|minutos|hora|horas)\\b",
)
private val RELATIVE_DAYS_REGEX = Regex(
    "\\b(?:en|dentro de)\\s+($NUMBER_PATTERN)\\s+(dia|dias|semana|semanas)\\b",
)

private val STANDALONE_MANANA_REGEX = Regex("\\bmanana\\b")
private val ESTA_PERIOD_REGEX = Regex("\\besta\\s+(manana|tarde|noche|madrugada)\\b")
private val HOY_REGEX = Regex("\\bhoy\\b")
private val NEXT_WEEK_REGEX = Regex("\\b(?:proximo|siguiente|que viene)\\b")
private val WEEKDAY_REGEX = Regex(
    "\\b(?:el\\s+)?(?:proximo\\s+|siguiente\\s+)?" +
        "(lunes|martes|miercoles|jueves|viernes|sabado|domingo)\\b",
)
private val NUMERIC_DATE_REGEX = Regex(
    "\\b(\\d{1,2})\\s*[/.-]\\s*(\\d{1,2})(?:\\s*[/.-]\\s*(\\d{2,4}))?\\b",
)
private val MONTH_DATE_REGEX = Regex(
    "\\b(\\d{1,2})\\s+(?:de\\s+)?([a-z]+)(?:\\s+(?:de\\s+)?(\\d{4}))?\\b",
)
private val DAY_ONLY_REGEX = Regex("\\b(?:el|dia)\\s+(\\d{1,2})\\b")

private val A_LAS_REGEX = Regex(
    "\\ba\\s+las?\\s+($HOUR_PATTERN)(?:\\s*[:.]\\s*(\\d{1,2}))?\\b",
)
private val LESS_QUARTER_REGEX = Regex("\\bmenos\\s+cuarto\\b")
private val QUARTER_REGEX = Regex("\\by\\s+cuarto\\b")
private val HALF_REGEX = Regex("\\by\\s+media\\b")
private val Y_MINUTE_REGEX = Regex("\\by\\s+($MINUTE_PATTERN)\\b")
private val STANDALONE_MERIDIEM_REGEX = Regex("\\b($HOUR_PATTERN)\\s*$MERIDIEM_PATTERN\\b")
private val COLON_TIME_REGEX = Regex("\\b($HOUR_PATTERN)\\s*[:.]\\s*(\\d{1,2})\\b")
private val HOUR_H_REGEX = Regex("\\b($HOUR_PATTERN)\\s*(?:h|hs|horas)\\b")
private val HOUR_PERIOD_REGEX = Regex(
    "\\b($HOUR_PATTERN)\\s+(?:de|por)\\s+la\\s+$PERIOD_PATTERN\\b",
)
private val NOON_REGEX = Regex("\\bmediodia\\b")
private val MIDNIGHT_REGEX = Regex("\\bmedianoche\\b")

private val MONTH_MAP = mapOf(
    "enero" to 0,
    "febrero" to 1,
    "marzo" to 2,
    "abril" to 3,
    "mayo" to 4,
    "junio" to 5,
    "julio" to 6,
    "agosto" to 7,
    "septiembre" to 8,
    "setiembre" to 8,
    "octubre" to 9,
    "noviembre" to 10,
    "diciembre" to 11,
)

private val WEEKDAY_MAP = mapOf(
    "domingo" to Calendar.SUNDAY,
    "lunes" to Calendar.MONDAY,
    "martes" to Calendar.TUESDAY,
    "miercoles" to Calendar.WEDNESDAY,
    "jueves" to Calendar.THURSDAY,
    "viernes" to Calendar.FRIDAY,
    "sabado" to Calendar.SATURDAY,
)
