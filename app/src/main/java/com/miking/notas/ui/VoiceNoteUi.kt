package com.miking.notas.ui

import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale

/**
 * Abre el dictado del sistema. No hace falta el permiso RECORD_AUDIO en esta app:
 * el servicio de reconocimiento de voz del dispositivo gestiona el micrófono.
 */
internal fun speechRecognitionIntent(prompt: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

/** Extrae el primer resultado del dictado. */
internal fun recognizedSpeech(data: Intent?): String? {
    val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?: data?.getCharSequenceArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.map { it.toString() }

    return results?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
