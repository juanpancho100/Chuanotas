package com.miking.notas.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmCycleTest {

    @Test
    fun suenaLosPrimerosDiezMinutos() {
        assertTrue(AlarmCycle.isRinging(0L))
        assertTrue(AlarmCycle.isRinging(AlarmCycle.RING_DURATION_MS - 1))
    }

    @Test
    fun descansaCincoMinutosDespues() {
        assertFalse(AlarmCycle.isRinging(AlarmCycle.RING_DURATION_MS))
        assertFalse(AlarmCycle.isRinging(AlarmCycle.TOTAL_DURATION_MS - 1))
    }

    @Test
    fun elCicloSeRepiteIndefinidamente() {
        assertTrue(AlarmCycle.isRinging(AlarmCycle.TOTAL_DURATION_MS))
        assertTrue(AlarmCycle.isRinging(AlarmCycle.TOTAL_DURATION_MS * 3 + 60_000L))
        assertFalse(
            AlarmCycle.isRinging(
                AlarmCycle.TOTAL_DURATION_MS +
                    AlarmCycle.RING_DURATION_MS +
                    1_000L,
            ),
        )
    }

    @Test
    fun calculaElTiempoRestanteDeCadaFase() {
        assertEquals(
            AlarmCycle.RING_DURATION_MS,
            AlarmCycle.remainingInPhase(0L),
        )
        assertEquals(
            1_000L,
            AlarmCycle.remainingInPhase(AlarmCycle.RING_DURATION_MS - 1_000L),
        )
        assertEquals(
            AlarmCycle.PAUSE_DURATION_MS,
            AlarmCycle.remainingInPhase(AlarmCycle.RING_DURATION_MS),
        )
        assertEquals(
            1_000L,
            AlarmCycle.remainingInPhase(AlarmCycle.TOTAL_DURATION_MS - 1_000L),
        )
    }
}
