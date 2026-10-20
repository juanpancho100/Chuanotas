package com.miking.notas.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AiModelsTest {

    private fun localDateTimeOf(millis: Long): java.time.LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    @Test
    fun `interpreta una fecha local con hora`() {
        val millis = parseLocalDateTime("2026-10-21T09:00:00")
        assertNotNull(millis)

        val dateTime = localDateTimeOf(millis!!)
        assertEquals(2026, dateTime.year)
        assertEquals(10, dateTime.monthValue)
        assertEquals(21, dateTime.dayOfMonth)
        assertEquals(9, dateTime.hour)
        assertEquals(0, dateTime.minute)
    }

    @Test
    fun `interpreta una fecha con espacio entre fecha y hora`() {
        val millis = parseLocalDateTime("2026-10-21 18:30")
        assertNotNull(millis)

        val dateTime = localDateTimeOf(millis!!)
        assertEquals(18, dateTime.hour)
        assertEquals(30, dateTime.minute)
    }

    @Test
    fun `una fecha sin hora se coloca a las nueve`() {
        val millis = parseLocalDateTime("2026-10-21")
        assertNotNull(millis)

        val dateTime = localDateTimeOf(millis!!)
        assertEquals(9, dateTime.hour)
        assertEquals(21, dateTime.dayOfMonth)
    }

    @Test
    fun `un texto que no es una fecha no se interpreta`() {
        assertNull(parseLocalDateTime("mañana por la mañana"))
        assertNull(parseLocalDateTime(""))
    }

    @Test
    fun `descarta un recordatorio de una sola vez que ya ha pasado`() {
        val now = 1_800_000_000_000L
        val json = JSONObject(
            """
            {
              "notes": [
                {
                  "title": "Cita con el doctor",
                  "body": "",
                  "category": "Salud",
                  "reminderAt": "2020-01-01T09:00:00",
                  "repeatDays": []
                }
              ]
            }
            """.trimIndent(),
        )

        val plan = parseAiPlan(json, "texto", now)
        assertEquals(1, plan.drafts.size)
        assertNull(plan.drafts.first().reminderAt)
        assertTrue(plan.drafts.first().repeatDays.isEmpty())
    }

    @Test
    fun `conserva un recordatorio repetido aunque el ancla sea pasada`() {
        val now = 1_800_000_000_000L
        val json = JSONObject(
            """
            {
              "notes": [
                {
                  "title": "Pasta",
                  "body": "",
                  "category": "",
                  "reminderAt": "2020-01-01T09:00:00",
                  "repeatDays": [1, 2, 3]
                }
              ]
            }
            """.trimIndent(),
        )

        val plan = parseAiPlan(json, "texto", now)
        val draft = plan.drafts.first()
        assertNotNull(draft.reminderAt)
        assertEquals(setOf(1, 2, 3), draft.repeatDays)
    }

    @Test
    fun `descarta los dias de repeticion que no son validos`() {
        val now = 1_800_000_000_000L
        val json = JSONObject(
            """
            {
              "notes": [
                {
                  "title": "Gimnasio",
                  "body": "",
                  "category": "",
                  "reminderAt": "2026-10-21T09:00:00",
                  "repeatDays": [1, 0, 8, 7]
                }
              ]
            }
            """.trimIndent(),
        )

        val plan = parseAiPlan(json, "texto", now)
        assertEquals(setOf(1, 7), plan.drafts.first().repeatDays)
    }

    @Test
    fun `no crea una nota sin titulo ni cuerpo`() {
        val now = 1_800_000_000_000L
        val json = JSONObject(
            """
            {
              "notes": [
                { "title": "  ", "body": "", "reminderAt": null },
                { "title": "Comprar pan", "body": "En la panadería" }
              ]
            }
            """.trimIndent(),
        )

        val plan = parseAiPlan(json, "texto", now)
        assertEquals(1, plan.drafts.size)
        assertEquals("Comprar pan", plan.drafts.first().title)
    }

    @Test
    fun `un recordatorio nulo deja la nota sin alarma`() {
        val now = 1_800_000_000_000L
        val json = JSONObject(
            """
            {
              "notes": [
                {
                  "title": "Comprar pan",
                  "body": "",
                  "category": "Compras",
                  "reminderAt": null,
                  "repeatDays": [1, 2]
                }
              ]
            }
            """.trimIndent(),
        )

        val plan = parseAiPlan(json, "texto", now)
        val draft = plan.drafts.first()
        assertNull(draft.reminderAt)
        assertTrue(draft.repeatDays.isEmpty())
    }

    @Test
    fun `divide un dictado en varias notas`() {
        val now = 1_800_000_000_000L
        val json = JSONObject(
            """
            {
              "notes": [
                { "title": "Cita con el doctor", "reminderAt": "2026-10-21T10:00:00" },
                { "title": "Comprar pan", "reminderAt": "2026-10-20T19:00:00" }
              ],
              "summary": "Tienes dos cosas"
            }
            """.trimIndent(),
        )

        val plan = parseAiPlan(json, "mañana tengo cita", now)
        assertEquals(2, plan.drafts.size)
        assertEquals("Tienes dos cosas", plan.summary)
        assertEquals("mañana tengo cita", plan.transcript)
    }

    @Test
    fun `lee la respuesta y sus fuentes`() {
        val json = JSONObject(
            """
            {
              "answer": "Hoy tienes una cita con el doctor.",
              "sources": ["Cita con el doctor", "Comprar pan"]
            }
            """.trimIndent(),
        )

        val answer = parseAiAnswer(json, "qué tengo que hacer hoy", "respaldo")
        assertEquals("Hoy tienes una cita con el doctor.", answer.answer)
        assertEquals(listOf("Cita con el doctor", "Comprar pan"), answer.sources)
    }

    @Test
    fun `si falta la respuesta usa el texto plano como respaldo`() {
        val json = JSONObject("""{ "sources": [] }""")

        val answer = parseAiAnswer(json, "pregunta", "texto plano")
        assertEquals("texto plano", answer.answer)
        assertTrue(answer.sources.isEmpty())
    }
}
