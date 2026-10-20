package com.miking.notas.ai

import com.miking.notas.data.Note
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Puente entre la app y la API de DeepSeek.
 *
 * Solo sabe hablar dos idioma: convertir un dictado en un plan de notas y
 * responder preguntas usando las notas guardadas.
 */
internal class AiAssistant {

    /** Convierte un dictado libre en la lista de notas que hay que crear. */
    suspend fun planFromSpeech(apiKey: String, model: String, spoken: String): AiPlan {
        val client = DeepSeekClient(apiKey, model)
        val now = LocalDateTime.now()

        val content = client.complete(
            system = AiPrompts.planSystemPrompt(now, ZoneId.systemDefault()),
            user = spoken.trim(),
            jsonMode = true,
            maxTokens = 2000,
        )

        return parseAiPlan(parseJsonObject(content), spoken.trim(), System.currentTimeMillis())
    }

    /** Responde una pregunta usando como contexto las notas del usuario. */
    suspend fun answer(
        apiKey: String,
        model: String,
        question: String,
        notes: List<Note>,
    ): AiAnswer {
        val client = DeepSeekClient(apiKey, model)
        val now = LocalDateTime.now()
        val context = AiPrompts.buildContext(notes, question, now)

        val user = buildString {
            append("Pregunta: ").append(question.trim())
            append("\n\nNotas del usuario:\n")
            append(context.ifBlank { "(no hay ninguna nota guardada)" })
        }

        val content = client.complete(
            system = AiPrompts.answerSystemPrompt(now, ZoneId.systemDefault()),
            user = user,
            jsonMode = true,
            maxTokens = 1500,
        )

        // Si el modelo no devuelve json válido, el texto plano sirve igual.
        val json = runCatching { parseJsonObject(content) }.getOrNull()
        return if (json != null) {
            parseAiAnswer(json, question.trim(), content)
        } else {
            AiAnswer(question = question.trim(), answer = content.trim(), sources = emptyList())
        }
    }

    private fun parseJsonObject(content: String): JSONObject {
        val text = content.trim()
        val cleaned = if (text.startsWith("```")) {
            text.removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        } else {
            text
        }

        return runCatching { JSONObject(cleaned) }.getOrElse {
            throw AiException("La respuesta de DeepSeek no se pudo interpretar")
        }
    }
}
