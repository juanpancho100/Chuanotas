package com.miking.notas.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Lee un texto en voz alta con el motor de síntesis del sistema.
 *
 * No hace falta ningún permiso. Si el motor no está listo todavía, el texto se
 * guarda y se lee en cuanto arranque.
 */
internal class SpeechOutput(context: Context) : TextToSpeech.OnInitListener {

    private var ready = false
    private var pending: String? = null
    private var failure: String? = null

    private val engine: TextToSpeech

    init {
        engine = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            failure = "Este dispositivo no tiene motor de voz"
            return
        }

        ready = true

        val result = engine.setLanguage(Locale.getDefault())
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            failure = "No hay voz en español instalada"
            return
        }

        pending?.let { text ->
            pending = null
            speak(text)
        }
    }

    /**
     * Lee el texto en voz alta.
     *
     * Devuelve un mensaje de error si no se pudo leer, o null si la lectura
     * está en marcha.
     */
    fun speak(text: String): String? {
        if (text.isBlank()) return "No hay nada que leer"

        failure?.let { return it }

        if (!ready) {
            pending = text
            return null
        }

        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        return if (result == TextToSpeech.ERROR) "No se pudo leer el texto" else null
    }

    fun stop() {
        pending = null
        engine.stop()
    }

    fun shutdown() {
        pending = null
        engine.stop()
        engine.shutdown()
    }

    private companion object {
        const val UTTERANCE_ID = "notas-ai"
    }
}

/** Crea un [SpeechOutput] ligado al ciclo de vida de la pantalla. */
@Composable
internal fun rememberSpeechOutput(): SpeechOutput {
    val context = LocalContext.current
    val output = remember { SpeechOutput(context) }

    DisposableEffect(output) {
        onDispose { output.shutdown() }
    }

    return output
}
