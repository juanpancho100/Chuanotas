package com.miking.notas.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miking.notas.ai.AiAnswer
import com.miking.notas.ai.AiAssistant
import com.miking.notas.ai.AiConfig
import com.miking.notas.ai.AiException
import com.miking.notas.ai.AiNoteDraft
import com.miking.notas.ai.AiPlan
import com.miking.notas.ai.DeepSeekClient
import com.miking.notas.ai.SecretStore
import com.miking.notas.data.AppSettings
import com.miking.notas.data.BackgroundStyle
import com.miking.notas.data.CategoryNames
import com.miking.notas.data.Note
import com.miking.notas.data.NotesRepository
import com.miking.notas.data.ReminderRepeat
import com.miking.notas.data.SettingsStore
import com.miking.notas.data.SortMode
import com.miking.notas.data.ThemeMode
import com.miking.notas.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class EditorState(
    val id: String,
    val title: String,
    val body: String,
    val category: String = "",
    val color: Int,
    val pinned: Boolean,
    val reminderAt: Long?,
    val repeatDays: Set<Int> = emptySet(),
    val createdAt: Long,
    val updatedAt: Long,
    val isNew: Boolean,
) {
    val isEmpty: Boolean
        get() = title.isBlank() && body.isBlank() && reminderAt == null

    fun toNote(updatedAt: Long = System.currentTimeMillis()): Note = Note(
        id = id,
        title = title.trim(),
        body = body,
        category = category.trim(),
        color = color,
        pinned = pinned,
        reminderAt = reminderAt,
        repeatDays = repeatDays,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

sealed interface UiEvent {
    data class NoteDeleted(val note: Note) : UiEvent
    data class Message(val text: String) : UiEvent
}

/** En qué está el asistente de IA ahora mismo. */
enum class AiMode { Plan, Answer }

/**
 * El estado del asistente.
 *
 * El plan y la respuesta viven en campos separados para que cambiar de
 * pestaña no tire lo que ya habías hecho.
 */
data class AiUiState(
    val working: AiMode? = null,
    val plan: AiPlan? = null,
    val answer: AiAnswer? = null,
    val error: String? = null,
    val errorMode: AiMode? = null,
    /**
     * Sube cada vez que llega una respuesta nueva. Sirve para disparar la
     * lectura en voz alta: dos respuestas con el mismo texto son iguales, así
     * que sin este contador la segunda no volvería a leerse.
     */
    val answerSeq: Long = 0L,
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NotesRepository(application)
    private val settingsStore = SettingsStore(application)
    private val assistant = AiAssistant()
    private val secretStore = SecretStore(application)

    /**
     * Cola con capacidad 1: si se guardan varias veces seguidas solo cuenta
     * la instantánea más reciente, y un único consumidor evita escrituras
     * desordenadas sobre notes.json.
     */
    private val saveQueue = Channel<List<Note>>(Channel.CONFLATED)

    private val _notes = MutableStateFlow(repository.load())
    private val _query = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<String?>(null)
    private val _editor = MutableStateFlow<EditorState?>(null)
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    private val _aiState = MutableStateFlow(AiUiState())
    private val _aiApiKey = MutableStateFlow(secretStore.get(AiConfig.API_KEY_SECRET).orEmpty())

    private var persistJob: Job? = null

    val query: StateFlow<String> = _query.asStateFlow()
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()
    val allNotes: StateFlow<List<Note>> = _notes.asStateFlow()
    val settings: StateFlow<AppSettings> = settingsStore.settings
    val events = _events.asSharedFlow()
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()
    val aiApiKey: StateFlow<String> = _aiApiKey.asStateFlow()

    val visibleNotes: StateFlow<List<Note>> =
        combine(_notes, _query, _categoryFilter, settings) { notes, query, category, appSettings ->
            filterAndSort(notes, query, category, appSettings.sortMode)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = filterAndSort(_notes.value, "", null, settings.value.sortMode),
        )

    init {
        // Las categorías de notas antiguas o importadas también aparecen en la lista.
        settingsStore.addCategories(_notes.value.map { it.category })
        ReminderScheduler.rescheduleAll(getApplication(), _notes.value)

        viewModelScope.launch(Dispatchers.IO) {
            for (snapshot in saveQueue) {
                repository.save(snapshot)
            }
        }
    }

    /* ------------------------------- Listado ------------------------------- */

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category?.takeIf { it.isNotBlank() }
    }

    fun newNote() {
        val now = System.currentTimeMillis()
        _editor.value = EditorState(
            id = UUID.randomUUID().toString(),
            title = "",
            body = "",
            category = _categoryFilter.value.orEmpty(),
            color = 0,
            pinned = false,
            reminderAt = null,
            repeatDays = emptySet(),
            createdAt = now,
            updatedAt = now,
            isNew = true,
        )
    }

    /**
     * Crea una nota con los datos capturados por voz y la abre para revisarla.
     * El recordatorio es opcional; si existe, se programa antes de guardar.
     */
    fun createFromVoice(title: String, body: String, reminderAt: Long?) {
        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        if (cleanTitle.isEmpty() && cleanBody.isEmpty()) {
            _events.tryEmit(UiEvent.Message("No se reconoció ninguna nota"))
            return
        }

        if (_editor.value != null) saveAndClose()

        val now = System.currentTimeMillis()
        val normalizedReminder = reminderAt?.takeIf { it > now }
        val note = Note(
            title = cleanTitle.ifBlank { voiceTitle(cleanBody) },
            body = cleanBody,
            category = _categoryFilter.value.orEmpty(),
            reminderAt = normalizedReminder,
            createdAt = now,
            updatedAt = now,
        )

        _notes.update { notes -> notes + note }
        if (normalizedReminder != null) {
            ReminderScheduler.schedule(getApplication(), note.id, normalizedReminder)
        }
        persistNow()
        openNote(note)
    }

    fun openNote(note: Note) {
        _editor.value = EditorState(
            id = note.id,
            title = note.title,
            body = note.body,
            category = note.category,
            color = note.color,
            pinned = note.pinned,
            reminderAt = note.reminderAt,
            repeatDays = note.repeatDays,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt,
            isNew = false,
        )
    }

    fun openNoteById(noteId: String): Boolean {
        if (_editor.value?.id == noteId) return true

        val note = _notes.value.firstOrNull { it.id == noteId } ?: return false
        if (_editor.value != null) {
            saveAndClose()
        }
        openNote(note)
        return true
    }

    /** Vuelve a programar las alarmas; se llama al volver a la app. */
    fun rescheduleReminders() {
        ReminderScheduler.rescheduleAll(getApplication(), _notes.value)
    }

    fun togglePin(note: Note) {
        _notes.update { notes ->
            notes.map {
                if (it.id == note.id) it.copy(pinned = !it.pinned) else it
            }
        }
        persistNow()
    }

    fun delete(note: Note) {
        _notes.update { notes -> notes.filterNot { it.id == note.id } }
        ReminderScheduler.cancel(getApplication(), note.id)
        persistNow()
        _events.tryEmit(UiEvent.NoteDeleted(note))
    }

    fun restore(note: Note) {
        _notes.update { notes ->
            if (notes.any { it.id == note.id }) notes else notes + note
        }

        if (note.reminderAt != null) {
            ReminderScheduler.schedule(getApplication(), note)
        }

        persistNow()
        _events.tryEmit(UiEvent.Message("Nota restaurada"))
    }

    /* -------------------------------- Editor -------------------------------- */

    fun updateTitle(value: String) = updateEditor { it.copy(title = value) }

    fun updateBody(value: String) = updateEditor { it.copy(body = value) }

    fun setCategory(value: String) {
        val clean = CategoryNames.clean(value).orEmpty()
        if (clean.isNotEmpty()) settingsStore.addCategory(clean)
        updateEditor { it.copy(category = clean) }
    }

    fun setColor(index: Int) = updateEditor { it.copy(color = index.coerceIn(0, 5)) }

    fun togglePinEditor() = updateEditor { it.copy(pinned = it.pinned.not()) }

    fun setReminder(timestamp: Long?, repeatDays: Set<Int> = emptySet()) {
        val current = _editor.value ?: return
        val now = System.currentTimeMillis()
        val normalizedDays = if (timestamp == null) emptySet() else ReminderRepeat.sanitize(repeatDays)
        val normalized = timestamp?.takeIf { it > now || normalizedDays.isNotEmpty() }
        val next = current.copy(
            reminderAt = normalized,
            repeatDays = if (normalized == null) emptySet() else normalizedDays,
        )

        _editor.value = next
        syncDraft(next)

        if (normalized != null) {
            ReminderScheduler.schedule(getApplication(), next.toNote())
        } else {
            ReminderScheduler.cancel(getApplication(), next.id)
        }
    }

    /** Añade al final del cuerpo el texto dictado desde el editor. */
    fun appendDictation(text: String) {
        val current = _editor.value ?: return
        val clean = text.trim()
        if (clean.isEmpty()) {
            _events.tryEmit(UiEvent.Message("No se reconoció texto"))
            return
        }

        val body = current.body.trimEnd()
        val newBody = if (body.isEmpty()) clean else "$body\n$clean"
        val next = current.copy(
            title = current.title.ifBlank { voiceTitle(newBody) },
            body = newBody,
        )

        _editor.value = next
        syncDraft(next)
    }

    /** Autoguardado: la nota pasa al listado mientras se escribe. */
    private inline fun updateEditor(transform: (EditorState) -> EditorState) {
        val current = _editor.value ?: return
        val next = transform(current)
        _editor.value = next
        syncDraft(next)
    }

    private fun syncDraft(state: EditorState) {
        if (state.isEmpty) {
            if (_notes.value.any { it.id == state.id }) {
                _notes.update { notes -> notes.filterNot { it.id == state.id } }
                schedulePersist()
            }
            return
        }

        val note = state.toNote()
        _notes.update { notes -> notes.filterNot { it.id == note.id } + note }
        schedulePersist()
    }

    /** Guarda la nota y vuelve al listado. Una nota nueva vacía se descarta. */
    fun saveAndClose() {
        val current = _editor.value ?: return
        persistJob?.cancel()

        val app = getApplication<Application>()

        if (current.isEmpty) {
            _notes.update { notes -> notes.filterNot { it.id == current.id } }
            ReminderScheduler.cancel(app, current.id)
        } else {
            val note = current.toNote()
            _notes.update { notes -> notes.filterNot { it.id == note.id } + note }

            if (note.reminderAt != null) {
                ReminderScheduler.schedule(app, note)
            } else {
                ReminderScheduler.cancel(app, note.id)
            }
        }

        _editor.value = null
        enqueueSave()
    }

    /** Se llama cuando la app pasa a segundo plano para no perder lo último escrito. */
    fun flushDraft() {
        val current = _editor.value
        if (current != null && !current.isEmpty) {
            syncDraft(current)
        }
        persistNow()
    }

    fun deleteCurrent() {
        val current = _editor.value ?: return
        val existing = _notes.value.firstOrNull { it.id == current.id }

        _notes.update { notes -> notes.filterNot { it.id == current.id } }
        _editor.value = null
        ReminderScheduler.cancel(getApplication(), current.id)
        persistNow()

        if (existing != null) {
            _events.tryEmit(UiEvent.NoteDeleted(existing))
        }
    }

    /* ------------------------------ Ajustes ------------------------------- */

    fun setThemeMode(mode: ThemeMode) = settingsStore.setThemeMode(mode)

    fun setDynamicColor(enabled: Boolean) = settingsStore.setDynamicColor(enabled)

    fun setSortMode(mode: SortMode) = settingsStore.setSortMode(mode)

    fun setBackgroundStyle(style: BackgroundStyle) = settingsStore.setBackgroundStyle(style)

    fun addCategory(name: String): Boolean = settingsStore.addCategory(name)

    /** Quita la categoría de los ajustes y de las notas que la usaban. */
    fun deleteCategory(name: String) {
        settingsStore.removeCategory(name)

        if (_categoryFilter.value?.equals(name, ignoreCase = true) == true) {
            _categoryFilter.value = null
        }

        val noteAffected = _notes.value.any { it.category.equals(name, ignoreCase = true) }
        if (noteAffected) {
            _notes.update { notes ->
                notes.map { note ->
                    if (note.category.equals(name, ignoreCase = true)) {
                        note.copy(category = "")
                    } else {
                        note
                    }
                }
            }
            persistNow()
        }

        val current = _editor.value
        if (current != null && current.category.equals(name, ignoreCase = true)) {
            updateEditor { it.copy(category = "") }
        }
    }

    /* --------------------------- Asistente de IA --------------------------- */

    /** Guarda la API key cifrada con la clave del AndroidKeyStore. */
    fun setAiApiKey(value: String) {
        val clean = value.trim()
        secretStore.put(AiConfig.API_KEY_SECRET, clean)
        _aiApiKey.value = clean
    }

    fun setAiEnabled(enabled: Boolean) = settingsStore.setAiEnabled(enabled)

    fun setAiModel(model: String) = settingsStore.setAiModel(AiConfig.normalizeModel(model))

    fun setAiAutoSpeak(enabled: Boolean) = settingsStore.setAiAutoSpeak(enabled)

    fun aiReset() {
        _aiState.update { it.copy(plan = null, error = null, errorMode = null) }
    }

    /** Convierte un dictado libre en un plan de notas con recordatorios. */
    fun aiPlan(spoken: String) {
        val key = _aiApiKey.value
        if (key.isBlank()) {
            _aiState.update {
                it.copy(
                    working = null,
                    error = "Añade tu API key de DeepSeek en Ajustes",
                    errorMode = AiMode.Plan,
                )
            }
            return
        }

        _aiState.update { it.copy(working = AiMode.Plan, error = null, errorMode = null) }

        viewModelScope.launch {
            val result = runCatching {
                assistant.planFromSpeech(key, model(), spoken)
            }
            result.fold(
                onSuccess = { plan ->
                    _aiState.update {
                        it.copy(working = null, plan = plan, error = null, errorMode = null)
                    }
                },
                onFailure = { error ->
                    _aiState.update {
                        it.copy(working = null, error = error.aiMessage(), errorMode = AiMode.Plan)
                    }
                },
            )
        }
    }

    /** Responde una pregunta usando las notas guardadas como contexto. */
    fun aiAsk(question: String) {
        val key = _aiApiKey.value
        if (key.isBlank()) {
            _aiState.update {
                it.copy(
                    working = null,
                    error = "Añade tu API key de DeepSeek en Ajustes",
                    errorMode = AiMode.Answer,
                )
            }
            return
        }
        if (question.isBlank()) return

        _aiState.update { it.copy(working = AiMode.Answer, error = null, errorMode = null) }

        viewModelScope.launch {
            val result = runCatching {
                assistant.answer(key, model(), question, _notes.value)
            }
            result.fold(
                onSuccess = { answer ->
                    _aiState.update {
                        it.copy(
                            working = null,
                            answer = answer,
                            error = null,
                            errorMode = null,
                            answerSeq = it.answerSeq + 1,
                        )
                    }
                },
                onFailure = { error ->
                    _aiState.update {
                        it.copy(working = null, error = error.aiMessage(), errorMode = AiMode.Answer)
                    }
                },
            )
        }
    }

    /** Crea las notas del plan y programa sus recordatorios. */
    fun aiSaveDrafts(drafts: List<AiNoteDraft>) {
        if (drafts.isEmpty()) return

        val now = System.currentTimeMillis()
        val app = getApplication<Application>()

        val created = drafts.map { draft ->
            Note(
                title = draft.title,
                body = draft.body,
                category = draft.category,
                reminderAt = draft.reminderAt,
                repeatDays = draft.repeatDays,
                createdAt = now,
                updatedAt = now,
            )
        }

        _notes.update { notes -> notes + created }
        settingsStore.addCategories(created.map { it.category })

        created.forEach { note ->
            if (note.reminderAt != null) {
                ReminderScheduler.schedule(app, note)
            }
        }

        persistNow()
        _aiState.update { it.copy(plan = null) }
        _events.tryEmit(
            UiEvent.Message(
                if (created.size == 1) "Nota creada" else "${created.size} notas creadas",
            ),
        )
    }

    /** Guarda la respuesta de la IA como una nota normal. */
    fun aiSaveAnswer(answer: AiAnswer) {
        val now = System.currentTimeMillis()
        val note = Note(
            title = answer.question.ifBlank { "Resumen" },
            body = answer.answer,
            createdAt = now,
            updatedAt = now,
        )

        _notes.update { notes -> notes + note }
        persistNow()
        _events.tryEmit(UiEvent.Message("Resumen guardado como nota"))
    }

    /** Comprueba que la key y el modelo responden. */
    fun aiTestConnection() {
        val key = _aiApiKey.value
        if (key.isBlank()) {
            _events.tryEmit(UiEvent.Message("Primero escribe tu API key"))
            return
        }

        viewModelScope.launch {
            val result = runCatching {
                DeepSeekClient(key, model()).complete(
                    system = "Responde solo con la palabra: ok",
                    user = "ok",
                    maxTokens = 16,
                )
            }
            _events.tryEmit(
                UiEvent.Message(
                    result.fold(
                        onSuccess = { "Conexión con DeepSeek correcta" },
                        onFailure = { it.aiMessage() },
                    ),
                ),
            )
        }
    }

    private fun model(): String = AiConfig.normalizeModel(settings.value.aiModel)

    private fun Throwable.aiMessage(): String =
        if (this is AiException) message else "No se pudo completar la petición"

    /* --------------------------- Exportar / importar ------------------------ */

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val notes = _notes.value.sortedByDescending { it.updatedAt }
                if (notes.isEmpty()) error("No hay notas para exportar")

                withContext(Dispatchers.IO) {
                    val json = repository.exportJson(notes)
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                        writer.write(json)
                    } ?: error("No se pudo abrir el archivo")
                }
            }

            _events.emit(
                UiEvent.Message(
                    if (result.isSuccess) {
                        "Notas exportadas correctamente"
                    } else {
                        result.exceptionOrNull()?.message ?: "No se pudo exportar"
                    },
                ),
            )
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("No se pudo abrir el archivo")
                    repository.importJson(text)
                }
            }

            result.onSuccess { imported ->
                val merged = _notes.value.associateBy { it.id }.toMutableMap()
                val changedNotes = mutableListOf<Note>()
                var added = 0

                imported.forEach { note ->
                    val current = merged[note.id]
                    if (current == null || note.updatedAt > current.updatedAt) {
                        merged[note.id] = note
                        changedNotes += note
                        added++
                    }
                }

                _notes.value = merged.values.toList()
                settingsStore.addCategories(imported.map { it.category })
                persistNow()

                changedNotes.forEach { note ->
                    if (note.reminderAt != null) {
                        ReminderScheduler.schedule(getApplication(), note)
                    } else {
                        ReminderScheduler.cancel(getApplication(), note.id)
                    }
                }

                ReminderScheduler.rescheduleAll(getApplication(), _notes.value)

                _events.emit(
                    UiEvent.Message(
                        if (added == 0) "No hay notas nuevas que importar" else "$added notas importadas",
                    ),
                )
            }

            result.onFailure {
                _events.emit(UiEvent.Message("El archivo no es válido o no se pudo leer"))
            }
        }
    }

    /* ------------------------------- Interno -------------------------------- */

    private fun voiceTitle(text: String): String {
        val firstLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isEmpty()) return "Nota de voz"
        if (firstLine.length <= 60) return firstLine

        val cut = firstLine.take(60)
        val atWord = cut.substringBeforeLast(' ')
        return atWord.takeIf { it.length >= 20 } ?: cut
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(350)
            enqueueSave()
        }
    }

    private fun persistNow() {
        persistJob?.cancel()
        enqueueSave()
    }

    private fun enqueueSave() {
        saveQueue.trySend(_notes.value)
    }

    private fun filterAndSort(
        notes: List<Note>,
        query: String,
        category: String?,
        sortMode: SortMode,
    ): List<Note> {
        val term = query.trim()
        val byCategory = if (category.isNullOrBlank()) {
            notes
        } else {
            notes.filter { it.category.equals(category, ignoreCase = true) }
        }

        val filtered = if (term.isEmpty()) {
            byCategory
        } else {
            byCategory.filter { note ->
                note.title.contains(term, ignoreCase = true) ||
                    note.body.contains(term, ignoreCase = true) ||
                    note.category.contains(term, ignoreCase = true)
            }
        }

        val byMode = when (sortMode) {
            SortMode.UPDATED -> compareByDescending<Note> { it.updatedAt }
            SortMode.CREATED -> compareByDescending<Note> { it.createdAt }
            SortMode.TITLE -> Comparator<Note> { a, b ->
                val byTitle = a.displayTitle.compareTo(b.displayTitle, ignoreCase = true)
                if (byTitle != 0) byTitle else b.updatedAt.compareTo(a.updatedAt)
            }
        }

        return filtered.sortedWith(
            compareByDescending<Note> { it.pinned }.then(byMode),
        )
    }
}
