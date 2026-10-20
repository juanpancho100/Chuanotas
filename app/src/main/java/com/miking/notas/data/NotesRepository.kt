package com.miking.notas.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistencia sencilla en un JSON dentro del almacenamiento privado de la app.
 * No necesita base de datos ni permisos, y sobrevive a reinicios y actualizaciones.
 */
class NotesRepository(context: Context) {

    private val file = File(context.filesDir, "notes.json")

    fun load(): List<Note> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        val text = file.readText()
        if (text.isBlank()) return@runCatching emptyList()

        val array = JSONArray(text)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(Note.fromJson(item))
            }
        }
    }.getOrElse { emptyList() }

    /**
     * Escritura atómica: primero a un archivo temporal y luego se renombra,
     * para no corromper las notas si la app se cierra a mitad de guardado.
     */
    @Synchronized
    fun save(notes: List<Note>) {
        runCatching {
            val array = JSONArray()
            notes.forEach { array.put(it.toJson()) }

            val temp = File(file.parentFile, "notes.json.tmp")
            temp.writeText(array.toString())
            if (!temp.renameTo(file)) {
                file.writeText(array.toString())
                temp.delete()
            }
        }
    }

    /** Genera una copia de seguridad legible con todas las notas. */
    fun exportJson(notes: List<Note>): String {
        val array = JSONArray()
        notes.forEach { array.put(it.toJson()) }

        return JSONObject()
            .put("app", "Notas")
            .put("format", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("notes", array)
            .toString(2)
    }

    /**
     * Acepta tanto el formato exportado por la app (objeto con "notes")
     * como un array JSON simple. Lanza una excepción si no hay notas válidas.
     */
    fun importJson(text: String): List<Note> {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "El archivo está vacío" }

        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            root.optJSONArray("notes") ?: JSONArray(trimmed)
        }

        val notes = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(Note.fromJson(item))
            }
        }

        require(notes.isNotEmpty()) { "El archivo no contiene notas" }
        return notes
    }
}
