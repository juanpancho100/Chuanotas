package com.miking.notas.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar
import java.util.TimeZone

class ReminderRepeatTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun unaVezDevuelveLaFechaSiEsFutura() {
        val anchor = at(2026, 10, 20, 9, 0)
        assertEquals(anchor, ReminderRepeat.nextOccurrence(anchor, emptySet(), at(2026, 10, 19, 10, 0)))
    }

    @Test
    fun unaVezVencidaNoSeReprograma() {
        val anchor = at(2026, 10, 19, 9, 0)
        assertNull(ReminderRepeat.nextOccurrence(anchor, emptySet(), at(2026, 10, 19, 10, 0)))
    }

    @Test
    fun lunesMiercolesViernesSaltaAlMiercoles() {
        val anchor = at(2026, 10, 19, 9, 0) // lunes
        val days = setOf(1, 3, 5)
        val now = at(2026, 10, 19, 10, 0) // lunes después de la hora

        assertEquals(
            at(2026, 10, 21, 9, 0),
            ReminderRepeat.nextOccurrence(anchor, days, now),
        )
    }

    @Test
    fun viernesDespuesDeLaHoraSaltaAlLunes() {
        val anchor = at(2026, 10, 19, 9, 0)
        val days = setOf(1, 3, 5)
        val now = at(2026, 10, 23, 10, 0) // viernes después de la hora

        assertEquals(
            at(2026, 10, 26, 9, 0),
            ReminderRepeat.nextOccurrence(anchor, days, now),
        )
    }

    @Test
    fun respetaLaFechaInicialElegida() {
        val anchor = at(2026, 10, 20, 8, 30) // martes
        val days = setOf(1, 3) // lunes y miércoles
        val now = at(2026, 10, 19, 12, 0)

        assertEquals(
            at(2026, 10, 21, 8, 30),
            ReminderRepeat.nextOccurrence(anchor, days, now),
        )
    }

    @Test
    fun occurrenceOnSoloDevuelveLosDiasSeleccionados() {
        val anchor = at(2026, 10, 19, 9, 0)
        val days = setOf(1, 5)

        assertEquals(
            at(2026, 10, 23, 9, 0),
            ReminderRepeat.occurrenceOn(anchor, days, LocalDate.of(2026, 10, 23)),
        )
        assertNull(ReminderRepeat.occurrenceOn(anchor, days, LocalDate.of(2026, 10, 21)))
        assertNull(ReminderRepeat.occurrenceOn(anchor, days, LocalDate.of(2026, 10, 18)))
    }

    @Test
    fun noteCalculaLaProximaRepeticion() {
        val anchor = at(2026, 10, 19, 9, 0)
        val note = Note(
            id = "nota",
            reminderAt = anchor,
            repeatDays = setOf(1, 5),
        )

        assertEquals(
            at(2026, 10, 23, 9, 0),
            note.nextReminderAt(at(2026, 10, 20, 12, 0)),
        )
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis
}
