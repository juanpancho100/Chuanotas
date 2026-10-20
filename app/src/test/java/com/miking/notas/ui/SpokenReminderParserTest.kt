package com.miking.notas.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SpokenReminderParserTest {

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

    private val mondayMorning = at(2026, 10, 19, 10, 0)

    @Test
    fun mananaALasNueve() {
        assertEquals(at(2026, 10, 20, 9, 0), parseSpokenReminder("mañana a las 9", mondayMorning))
    }

    @Test
    fun hoyPorLaTarde() {
        assertEquals(at(2026, 10, 19, 18, 30), parseSpokenReminder("hoy a las 18:30", mondayMorning))
    }

    @Test
    fun proximoViernes() {
        assertEquals(
            at(2026, 10, 23, 18, 30),
            parseSpokenReminder("el viernes a las 18:30", mondayMorning),
        )
    }

    @Test
    fun fechaConMes() {
        assertEquals(
            at(2026, 12, 25, 10, 0),
            parseSpokenReminder("25 de diciembre a las 10", mondayMorning),
        )
    }

    @Test
    fun fechaNumerica() {
        assertEquals(at(2026, 12, 25, 10, 0), parseSpokenReminder("25/12 a las 10", mondayMorning))
    }

    @Test
    fun enDosHoras() {
        assertEquals(mondayMorning + 2 * 60 * 60_000L, parseSpokenReminder("en 2 horas", mondayMorning))
    }

    @Test
    fun enMediaHora() {
        assertEquals(mondayMorning + 30 * 60_000L, parseSpokenReminder("en media hora", mondayMorning))
    }

    @Test
    fun enUnaHoraYMedia() {
        assertEquals(
            mondayMorning + 90 * 60_000L,
            parseSpokenReminder("en una hora y media", mondayMorning),
        )
    }

    @Test
    fun enTresDiasALasNueve() {
        assertEquals(
            at(2026, 10, 22, 9, 0),
            parseSpokenReminder("en 3 días a las 9", mondayMorning),
        )
    }

    @Test
    fun horaSinFechaSiYaPasoPasaAManana() {
        assertEquals(at(2026, 10, 20, 9, 0), parseSpokenReminder("a las 9", mondayMorning))
    }

    @Test
    fun horaConPeriodoNoche() {
        assertEquals(
            at(2026, 10, 19, 21, 0),
            parseSpokenReminder("a las 9 de la noche", mondayMorning),
        )
    }

    @Test
    fun horaConPeriodoManana() {
        assertEquals(
            at(2026, 10, 19, 9, 0),
            parseSpokenReminder("a las 9 de la mañana", at(2026, 10, 19, 8, 0)),
        )
    }

    @Test
    fun horaYMedia() {
        assertEquals(
            at(2026, 10, 19, 9, 30),
            parseSpokenReminder("a las 9 y media", at(2026, 10, 19, 8, 0)),
        )
    }

    @Test
    fun horaMenosCuarto() {
        assertEquals(
            at(2026, 10, 19, 8, 45),
            parseSpokenReminder("a las 9 menos cuarto", at(2026, 10, 19, 8, 0)),
        )
    }

    @Test
    fun lunesConHoraPasadaPasaAOtroLunes() {
        assertEquals(
            at(2026, 10, 26, 9, 0),
            parseSpokenReminder("el lunes a las 9", mondayMorning),
        )
    }

    @Test
    fun pasadoManana() {
        assertEquals(
            at(2026, 10, 21, 8, 0),
            parseSpokenReminder("pasado mañana a las 8", mondayMorning),
        )
    }

    @Test
    fun diaSueltoSiYaPasoPasaAlSiguienteMes() {
        assertEquals(at(2026, 11, 5, 9, 0), parseSpokenReminder("el 5 a las 9", mondayMorning))
    }

    @Test
    fun hoyConHoraPasadaNoSePrograma() {
        assertNull(parseSpokenReminder("hoy a las 9", mondayMorning))
    }

    @Test
    fun estaNocheConHoraPasadaNoSePrograma() {
        assertNull(parseSpokenReminder("esta noche a las 9", at(2026, 10, 19, 22, 0)))
    }

    @Test
    fun estaTardeSeProgramaHoy() {
        assertEquals(
            at(2026, 10, 19, 18, 0),
            parseSpokenReminder("esta tarde a las 6", mondayMorning),
        )
    }

    @Test
    fun fechaSinHoraNoSePrograma() {
        assertNull(parseSpokenReminder("25 de diciembre", mondayMorning))
    }

    @Test
    fun mananaSinHoraUsaLaHoraActual() {
        assertEquals(at(2026, 10, 20, 10, 0), parseSpokenReminder("mañana", mondayMorning))
    }

    @Test
    fun nocheSinALas() {
        assertEquals(
            at(2026, 10, 19, 21, 0),
            parseSpokenReminder("9 de la noche", mondayMorning),
        )
    }

    @Test
    fun doceDeLaNoche() {
        assertEquals(
            at(2026, 10, 20, 0, 0),
            parseSpokenReminder("a las 12 de la noche", mondayMorning),
        )
    }

    @Test
    fun horaAmPm() {
        assertEquals(
            at(2026, 10, 19, 21, 0),
            parseSpokenReminder("a las 9 pm", mondayMorning),
        )
        assertEquals(
            at(2026, 10, 20, 9, 0),
            parseSpokenReminder("a las 9 am", mondayMorning),
        )
    }

    @Test
    fun comandosParaOmitir() {
        assertTrue(isSkipReminderCommand("sin recordatorio"))
        assertTrue(isSkipReminderCommand("no, gracias"))
        assertTrue(isSkipReminderCommand("omitir"))
        assertTrue(isSkipReminderCommand("no recordatorio"))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis
}
