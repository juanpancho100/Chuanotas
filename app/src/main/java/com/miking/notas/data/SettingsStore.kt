package com.miking.notas.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SortMode { UPDATED, CREATED, TITLE }

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
)

/**
 * Preferencias pequeñas de la app. SharedPreferences es suficiente aquí:
 * se leen una sola vez al arrancar.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("notas_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = update(_settings.value.copy(themeMode = mode))

    fun setDynamicColor(enabled: Boolean) = update(_settings.value.copy(dynamicColor = enabled))

    fun setSortMode(mode: SortMode) = update(_settings.value.copy(sortMode = mode))

    fun setBackgroundStyle(style: BackgroundStyle) =
        update(_settings.value.copy(backgroundStyle = style))

    /**
     * Añade una categoría nueva. Devuelve false si el nombre está vacío o ya
     * existe (sin distinguir mayúsculas).
     */
    fun addCategory(name: String): Boolean {
        val clean = CategoryNames.clean(name) ?: return false
        val current = _settings.value.categories
        if (CategoryNames.contains(current, clean)) return false

        update(_settings.value.copy(categories = CategoryNames.normalize(current + clean)))
        return true
    }

    /** Añade varias categorías de golpe, por ejemplo al importar notas. */
    fun addCategories(names: Iterable<String>) {
        val merged = CategoryNames.normalize(_settings.value.categories + names)
        if (merged != _settings.value.categories) {
            update(_settings.value.copy(categories = merged))
        }
    }

    fun removeCategory(name: String) {
        val current = _settings.value.categories
        val next = current.filterNot { it.equals(name, ignoreCase = true) }
        if (next.size != current.size) {
            update(_settings.value.copy(categories = next))
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

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_SORT = "sort_mode"
        const val KEY_BACKGROUND = "background_style"
        const val KEY_CATEGORIES = "categories"
    }
}
