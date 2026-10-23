package com.miking.notas.data

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCompletionTest {

    @Test
    fun seGuardaYSeRecuperaElEstadoCompletada() {
        val original = Note(
            id = "nota",
            title = "Comprar pan",
            completed = true,
        )

        val restored = Note.fromJson(JSONObject(original.toJson().toString()))

        assertTrue(restored.completed)
    }

    @Test
    fun lasNotasAntiguasSiguenPendientes() {
        val json = JSONObject().apply {
            put("id", "antigua")
            put("title", "Nota")
        }

        assertFalse(Note.fromJson(json).completed)
    }

    @Test
    fun unaNotaCompletadaNoProgramaRecordatorios() {
        val note = Note(
            id = "nota",
            reminderAt = System.currentTimeMillis() + 60_000L,
            repeatDays = setOf(1, 2, 3),
            completed = true,
        )

        assertNull(note.nextReminderAt())
        assertNull(note.reminderAtOn(java.time.LocalDate.now()))
    }
}
