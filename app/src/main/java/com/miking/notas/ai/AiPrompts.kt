package com.miking.notas.ai

import com.miking.notas.data.Note
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Las instrucciones que se le dan al modelo y el contexto que se le envía.
 *
 * Todos los prompts van en español e incluyen la fecha y la hora actuales: sin
 * eso el modelo no puede resolver expresiones como "mañana" o "el viernes".
 */
internal object AiPrompts {

    /** Palabras vacías que no aportan a emparejar una nota con una pregunta. */
    private val STOP_WORDS = setOf(
        "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "u",
        "en", "con", "para", "por", "sin", "sobre", "entre", "del", "al", "a", "e",
        "es", "son", "fue", "ser", "estar", "he", "ha", "hay", "que", "qué", "quien",
        "mi", "me", "mis", "se", "su", "sus", "lo", "le", "les", "ya", "muy", "mas",
        "más", "pero", "como", "cómo", "cuando", "cuándo", "donde", "dónde", "todo",
        "nada", "este", "esta", "esto", "ese", "esa", "tengo", "tienes", "tenemos",
        "cosa", "cosas", "hacer", "tareas", "tarea", "pendientes", "pendiente",
        "favor", "gracias", "dime", "dime", "muestrame", "muestrame", "necesito",
    )

    /** Palabras que hablan de un momento concreto, no de un tema. */
    private val TEMPORAL_WORDS = setOf(
        "hoy", "mañana", "manana", "pasado", "anteayer", "ayer", "semana", "mes",
        "lunes", "martes", "miercoles", "miércoles", "jueves", "viernes", "sabado",
        "sábado", "domingo", "agenda", "dia", "dias", "día", "días", "tarde", "noche",
        "ano", "año", "fin", "proximos", "próximos", "siguiente", "tengo", "pendientes",
    )

    /**
     * Instrucciones para convertir un dictado libre en una lista de notas con
     * recordatorio. La palabra "json" aparece en el prompt porque DeepSeek solo
     * activa el modo json si la instrucción la menciona.
     */
    fun planSystemPrompt(now: LocalDateTime, zone: ZoneId): String = """
        Eres un asistente que convierte instrucciones habladas en una lista de notas
        con recordatorios para una app de notas en español.

        Hoy es ${now.toLocalDate()} (${weekdayName(now)}) y son las ${now.toLocalTime()}.
        Zona horaria: $zone.

        Reglas:
        - Divide el texto en una nota por cada tarea, evento o cosa que el usuario tenga
          que hacer. Si el texto trae varios asuntos, crea varias notas.
        - Convierte las referencias de tiempo ("mañana", "el viernes", "a las 7 de la
          noche", "en 2 horas", "pasado mañana") a una fecha y hora absolutas.
        - Si se menciona un día pero no una hora, usa las 09:00 de ese día.
        - Si no se menciona ningún momento, deja "reminderAt" a null.
        - "repeatDays" solo se rellena si el usuario dice que algo se repite. Usa los
          días ISO: 1 = lunes, 2 = martes, 3 = miércoles, 4 = jueves, 5 = viernes,
          6 = sábado, 7 = domingo. Si no se menciona repetición, deja la lista vacía.
        - No inventes tareas ni recordatorios que no aparezcan en el texto.
        - El título describe la tarea en un máximo de 60 caracteres.
        - La categoría es una palabra breve (por ejemplo "Salud", "Casa", "Trabajo",
          "Compras") o "" si no se deduce con claridad.
        - El cuerpo guarda el detalle de la tarea. Si el texto incluye una lista de
          pasos, consérvala con guiones.
        - "summary" es una frase corta que resume todo lo que hay que hacer.

        Responde solo con un objeto json con esta forma:
        {
          "notes": [
            {
              "title": "Cita con el doctor",
              "body": "Ir al centro de salud.",
              "category": "Salud",
              "reminderAt": "2026-10-21T09:00:00",
              "repeatDays": []
            }
          ],
          "summary": "Tienes una cita con el doctor y luego comprar pan."
        }
    """.trimIndent()

    /**
     * Instrucciones para responder una pregunta usando solo las notas del usuario.
     */
    fun answerSystemPrompt(now: LocalDateTime, zone: ZoneId): String = """
        Eres el asistente personal de una app de notas en español. Respondes a lo que
        el usuario pregunta usando únicamente las notas que se te entregan.

        Hoy es ${now.toLocalDate()} (${weekdayName(now)}) y son las ${now.toLocalTime()}.
        Zona horaria: $zone.

        Reglas:
        - Usa solo la información de las notas. Si no está, dilo con claridad.
        - Responde en español, de forma directa y con frases cortas.
        - Si hay varias cosas que hacer, usa una lista con guiones.
        - Si la pregunta abarca varios días, sepáralos por día ("Hoy", "Mañana",
          "Sin fecha").
        - No inventes recordatorios, fechas ni tareas que no aparezcan en las notas.
        - Cita el título de la nota entre paréntesis cuando aportes el dato, por
          ejemplo (Cita con el doctor).

        Responde solo con un objeto json con esta forma:
        {
          "answer": "La respuesta en texto plano, en español.",
          "sources": ["Título de la nota usada", "Otro título"]
        }
    """.trimIndent()

    /**
     * Elige las notas que se envían al modelo y las formatea en texto plano.
     *
     * Prioriza las notas con recordatorio próximo y las que comparten palabras con
     * la pregunta, y completa con las notas editadas hace más poco. Así la
     * pregunta "qué tengo que hacer hoy" trae la agenda del día, y "¿qué dijimos
     * del coche" trae las notas que mencionan el coche.
     */
    fun buildContext(
        notes: List<Note>,
        question: String,
        now: LocalDateTime,
        maxNotes: Int = 30,
    ): String {
        if (notes.isEmpty()) return ""

        val zone = ZoneId.systemDefault()
        val today = now.toLocalDate()
        val queryWords = keywords(question)

        val scored = notes.map { note ->
            var score = 0

            note.reminderAt?.let { reminder ->
                val date = Instant.ofEpochMilli(reminder).atZone(zone).toLocalDate()
                score += when {
                    date.isBefore(today) -> 0
                    date == today -> 6
                    date == today.plusDays(1) -> 5
                    date <= today.plusDays(7) -> 3
                    else -> 1
                }
            }

            val haystack = keywords("${note.title} ${note.body} ${note.category}")
            score += queryWords.count { it in haystack } * 2

            note to score
        }

        val byScore = scored
            .sortedWith(
                compareByDescending<Pair<Note, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt },
            )
            .map { it.first }

        val recent = notes.sortedByDescending { it.updatedAt }.take(8)

        val selected = (byScore + recent)
            .distinctBy { it.id }
            .take(maxNotes)

        return selected.joinToString("\n") { note -> formatNote(note, zone) }
    }

    private fun formatNote(note: Note, zone: ZoneId): String {
        val builder = StringBuilder()
        builder.append("· ").append(note.displayTitle)

        if (note.category.isNotBlank()) {
            builder.append(" [").append(note.category).append("]")
        }

        note.reminderAt?.let { reminder ->
            val time = Instant.ofEpochMilli(reminder).atZone(zone)
            builder.append(" (recordatorio: ")
                .append(time.toLocalDate())
                .append(' ')
                .append(String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute))
                .append(')')
        }

        if (note.body.isNotBlank()) {
            builder.append(" — ").append(note.body.take(400))
        }

        return builder.toString()
    }

    private fun keywords(text: String): Set<String> =
        normalize(text)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in STOP_WORDS && it !in TEMPORAL_WORDS }
            .toSet()

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

    private fun weekdayName(now: LocalDateTime): String = when (now.dayOfWeek.value) {
        1 -> "lunes"
        2 -> "martes"
        3 -> "miércoles"
        4 -> "jueves"
        5 -> "viernes"
        6 -> "sábado"
        else -> "domingo"
    }
}
