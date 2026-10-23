package com.miking.notas.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SortMode { UPDATED, CREATED, TITLE }

private const val DEFAULT_AI_MODEL = "deepseek-flash"

/** Fondos de la vista. Se adaptan al modo claro u oscuro. */
enum class BackgroundStyle(
    val storageValue: String,
    val label: String,
) {
    DEFAULT("default", "Predeterminado"),
    PAPER("paper", "Papel"),
    SAND("sand", "Arena"),
    SAGE("sage", "Salvia"),
    SKY("sky", "Cielo"),
    LAVENDER("lavender", "Lavanda"),
    ROSE("rose", "Rosa"),
    ;

    companion object {
        fun fromStorage(value: String?): BackgroundStyle =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val sortMode: SortMode = SortMode.UPDATED,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.DEFAULT,
    val categories: List<String> = emptyList(),
    val categoryAlarmSounds: Map<String, String> = emptyMap(),
    val aiEnabled: Boolean = false,
    val aiModel: String = DEFAULT_AI_MODEL,
    val aiAutoSpeak: Boolean = true,
)

/**
 * Preferencias pequeñas de la app. SharedPreferences es suficiente aquí:
 * se leen una sola vez al arrancar.
 */
class SettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext
        .getSharedPreferences("notas_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        ensureCategorySounds()
    }

    fun setThemeMode(mode: ThemeMode) = update(_settings.value.copy(themeMode = mode))

    fun setDynamicColor(enabled: Boolean) = update(_settings.value.copy(dynamicColor = enabled))

    fun setSortMode(mode: SortMode) = update(_settings.value.copy(sortMode = mode))

    fun setBackgroundStyle(style: BackgroundStyle) =
        update(_settings.value.copy(backgroundStyle = style))

    fun setAiEnabled(enabled: Boolean) = update(_settings.value.copy(aiEnabled = enabled))

    fun setAiModel(model: String) = update(_settings.value.copy(aiModel = model))

    fun setAiAutoSpeak(enabled: Boolean) = update(_settings.value.copy(aiAutoSpeak = enabled))

    /**
     * Añade una categoría nueva. Devuelve false si el nombre está vacío o ya
     * existe (sin distinguir mayúsculas).
     */
    fun addCategory(name: String): Boolean {
        val clean = CategoryNames.clean(name) ?: return false
        val current = _settings.value.categories
        if (CategoryNames.contains(current, clean)) return false

        val categories = CategoryNames.normalize(current + clean)
        val sounds = CategoryAlarmSounds.ensureDefaults(
            appContext,
            categories,
            _settings.value.categoryAlarmSounds,
        )
        update(
            _settings.value.copy(
                categories = categories,
                categoryAlarmSounds = sounds,
            ),
        )
        return true
    }

    /** Añade varias categorías de golpe, por ejemplo al importar notas. */
    fun addCategories(names: Iterable<String>) {
        val current = _settings.value.categories
        val merged = CategoryNames.normalize(current + names)
        if (merged == current) return

        val sounds = CategoryAlarmSounds.ensureDefaults(
            appContext,
            merged,
            _settings.value.categoryAlarmSounds,
        )
        update(
            _settings.value.copy(
                categories = merged,
                categoryAlarmSounds = sounds,
            ),
        )
    }

    fun removeCategory(name: String) {
        val current = _settings.value.categories
        val next = current.filterNot { it.equals(name, ignoreCase = true) }
        if (next.size == current.size) return

        update(
            _settings.value.copy(
                categories = next,
                categoryAlarmSounds = _settings.value.categoryAlarmSounds
                    .filterKeys { !it.equals(name, ignoreCase = true) },
            ),
        )
    }

    /**
     * Cambia el tono de una categoría. Si [uri] es null o está vacío, se vuelve
     * a asignar un tono automático libre.
     */
    fun setCategorySound(category: String, uri: String?) {
        val canonical = CategoryNames.resolve(_settings.value.categories, category) ?: return
        val current = _settings.value.categoryAlarmSounds
        val clean = uri?.trim().orEmpty()

        val sounds = if (clean.isEmpty()) {
            CategoryAlarmSounds.ensureDefaults(
                appContext,
                _settings.value.categories,
                current.filterKeys { !it.equals(canonical, ignoreCase = true) },
            )
        } else {
            current
                .filterKeys { !it.equals(canonical, ignoreCase = true) }
                .plus(canonical to clean)
        }

        update(_settings.value.copy(categoryAlarmSounds = sounds))
    }

    private fun ensureCategorySounds() {
        val current = _settings.value
        val sounds = CategoryAlarmSounds.ensureDefaults(
            appContext,
            current.categories,
            current.categoryAlarmSounds,
        )
        if (sounds != current.categoryAlarmSounds) {
            update(current.copy(categoryAlarmSounds = sounds))
        }
    }

    private fun update(value: AppSettings) {
        _settings.value = value
        prefs.edit {
            putString(KEY_THEME, value.themeMode.storageValue)
            putBoolean(KEY_DYNAMIC, value.dynamicColor)
            putString(KEY_SORT, value.sortMode.storageValue)
            putString(KEY_BACKGROUND, value.backgroundStyle.storageValue)
            putStringSet(KEY_CATEGORIES, value.categories.toSet())
            putString(KEY_CATEGORY_SOUNDS, JSONObject(value.categoryAlarmSounds).toString())
            putBoolean(KEY_AI_ENABLED, value.aiEnabled)
            putString(KEY_AI_MODEL, value.aiModel)
            putBoolean(KEY_AI_AUTO_SPEAK, value.aiAutoSpeak)
        }
    }

    private fun read(): AppSettings = AppSettings(
        themeMode = themeModeFrom(prefs.getString(KEY_THEME, null)),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
        sortMode = sortModeFrom(prefs.getString(KEY_SORT, null)),
        backgroundStyle = BackgroundStyle.fromStorage(prefs.getString(KEY_BACKGROUND, null)),
        categories = CategoryNames.normalize(
            prefs.getStringSet(KEY_CATEGORIES, emptySet()).orEmpty(),
        ),
        categoryAlarmSounds = readCategorySounds(prefs.getString(KEY_CATEGORY_SOUNDS, null)),
        aiEnabled = prefs.getBoolean(KEY_AI_ENABLED, false),
        aiModel = prefs.getString(KEY_AI_MODEL, DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL,
        aiAutoSpeak = prefs.getBoolean(KEY_AI_AUTO_SPEAK, true),
    )

    /**
     * Guardamos claves estables escritas a mano. Así R8 puede ofuscar los
     * enums sin romper las preferencias.
     */
    private val ThemeMode.storageValue: String
        get() = when (this) {
            ThemeMode.SYSTEM -> "system"
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
        }

    private val SortMode.storageValue: String
        get() = when (this) {
            SortMode.UPDATED -> "updated"
            SortMode.CREATED -> "created"
            SortMode.TITLE -> "title"
        }

    private fun themeModeFrom(value: String?): ThemeMode = when (value) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    private fun sortModeFrom(value: String?): SortMode = when (value) {
        "created" -> SortMode.CREATED
        "title" -> SortMode.TITLE
        else -> SortMode.UPDATED
    }

    private fun readCategorySounds(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()

        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { category ->
                    val uri = json.optString(category).trim()
                    if (uri.isNotEmpty()) put(category, uri)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_SORT = "sort_mode"
        const val KEY_BACKGROUND = "background_style"
        const val KEY_CATEGORIES = "categories"
        const val KEY_CATEGORY_SOUNDS = "category_alarm_sounds"
        const val KEY_AI_ENABLED = "ai_enabled"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_AI_AUTO_SPEAK = "ai_auto_speak"
    }
}
