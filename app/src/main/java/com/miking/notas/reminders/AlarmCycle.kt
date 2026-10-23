package com.miking.notas.reminders

/**
 * Cadencia de una alarma activa: 10 minutos de tono y 5 minutos de silencio.
 *
 * El ciclo se repite indefinidamente hasta que la persona pulsa «Abrir nota»
 * o «Detener alarma». Se calcula a partir del tiempo transcurrido para que la
 * lógica sea fácil de probar sin depender del reloj del dispositivo.
 */
internal object AlarmCycle {

    const val RING_DURATION_MS: Long = 10 * 60 * 1000L
    const val PAUSE_DURATION_MS: Long = 5 * 60 * 1000L
    const val TOTAL_DURATION_MS: Long = RING_DURATION_MS + PAUSE_DURATION_MS

    fun isRinging(elapsedMs: Long): Boolean {
        val position = elapsedMs.mod(TOTAL_DURATION_MS)
        return position < RING_DURATION_MS
    }

    /** Tiempo que queda en la fase actual antes de cambiar de sonar a pausa. */
    fun remainingInPhase(elapsedMs: Long): Long {
        val position = elapsedMs.mod(TOTAL_DURATION_MS)
        return if (position < RING_DURATION_MS) {
            RING_DURATION_MS - position
        } else {
            TOTAL_DURATION_MS - position
        }
    }
}
