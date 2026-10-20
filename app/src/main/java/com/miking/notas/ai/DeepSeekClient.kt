package com.miking.notas.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Error de la API de DeepSeek con un mensaje apto para mostrar en la app. */
internal class AiException(
    override val message: String,
    val retryable: Boolean = false,
) : Exception(message)

/**
 * Cliente mínimo de la API de chat de DeepSeek.
 *
 * Usa HttpURLConnection y org.json para no añadir dependencias ni engordar el
 * APK. La API es compatible con el formato de OpenAI, así que solo hace falta
 * POST a /chat/completions con la cabecera Authorization.
 */
internal class DeepSeekClient(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) {

    /**
     * Envía los mensajes y devuelve el contenido de la respuesta.
     *
     * Con [jsonMode] el modelo responde en json estricto. La documentación de
     * DeepSeek avisa de que en ese modo la respuesta puede llegar vacía, así
     * que se reintenta una vez antes de rendirse.
     */
    suspend fun complete(
        system: String,
        user: String,
        jsonMode: Boolean = false,
        maxTokens: Int = 1500,
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Falta la API key de DeepSeek" }

        val payload = JSONObject().apply {
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", 0.2)
            if (jsonMode) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
        }

        var lastError: Exception? = null

        for (attempt in 0 until 2) {
            try {
                val content = request(payload)
                if (content.isNotBlank()) return@withContext content
                lastError = AiException("DeepSeek devolvió una respuesta vacía", retryable = true)
            } catch (error: AiException) {
                if (!error.retryable) throw error
                lastError = error
            } catch (error: Exception) {
                lastError = error
            }
        }

        throw lastError ?: AiException("No se pudo contactar con DeepSeek", retryable = true)
    }

    private fun request(payload: JSONObject): String {
        val connection = (URL("$BASE_URL/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (status !in 200..299) {
                throw AiException(describeError(status, text), retryable = status >= 500 || status == 429)
            }

            val choices = JSONObject(text).optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                throw AiException("DeepSeek no devolvió ninguna respuesta", retryable = true)
            }

            return choices.getJSONObject(0)
                .optJSONObject("message")
                ?.optString("content")
                .orEmpty()
        } catch (error: AiException) {
            throw error
        } catch (error: Exception) {
            throw AiException("No se pudo conectar con DeepSeek: ${error.message}", retryable = true)
        } finally {
            connection.disconnect()
        }
    }

    private fun describeError(status: Int, text: String): String {
        val detail = runCatching {
            JSONObject(text).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")

        return when (status) {
            401, 403 -> "La API key de DeepSeek no es válida"
            402 -> "La cuenta de DeepSeek no tiene saldo"
            429 -> "Demasiadas peticiones a DeepSeek. Espera un momento."
            in 500..599 -> "DeepSeek está teniendo problemas. Inténtalo de nuevo."
            else -> detail.ifBlank { "Error de DeepSeek ($status)" }
        }
    }

    internal companion object {
        const val DEFAULT_MODEL = "deepseek-flash"
        private const val BASE_URL = "https://api.deepseek.com"
    }
}
