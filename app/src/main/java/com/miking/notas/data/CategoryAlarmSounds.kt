package com.miking.notas.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.net.toUri

data class AvailableAlarmSound(
    val uri: String,
    val title: String,
)

/**
 * Tonos de alarma del sistema y asignación por categoría.
 *
 * No se crean canales de notificación por categoría: el canal sigue siendo
 * silencioso y [com.miking.notas.reminders.ReminderAlarmService] reproduce el
 * tono de la categoría con MediaPlayer.
 */
object CategoryAlarmSounds {

    fun available(context: Context): List<AvailableAlarmSound> {
        val appContext = context.applicationContext
        val manager = RingtoneManager(appContext).apply {
            setType(RingtoneManager.TYPE_ALARM)
        }
        val cursor = runCatching { manager.cursor }.getOrNull() ?: return emptyList()

        return cursor.use { rows ->
            val titleColumn = rows.getColumnIndex(MediaStore.Audio.Media.TITLE)
            buildList {
                while (rows.moveToNext()) {
                    val uri = runCatching { manager.getRingtoneUri(rows.position) }
                        .getOrNull()
                        ?: continue
                    val title = if (titleColumn >= 0) {
                        runCatching { rows.getString(titleColumn) }.getOrNull()
                    } else {
                        null
                    }

                    add(
                        AvailableAlarmSound(
                            uri = uri.toString(),
                            title = title?.trim().orEmpty(),
                        ),
                    )
                }
            }
        }.distinctBy { it.uri }
    }

    /** Completa las categorías sin sonido con tonos distintos cuando sea posible. */
    fun ensureDefaults(
        context: Context,
        categories: List<String>,
        existing: Map<String, String>,
    ): Map<String, String> {
        val normalized = CategoryNames.normalize(categories)
        if (normalized.isEmpty()) return emptyMap()

        val complete = normalized.all { category ->
            existing.entries.any {
                it.key.equals(category, ignoreCase = true) && it.value.isNotBlank()
            }
        } && existing.keys.all { key ->
            normalized.any { it.equals(key, ignoreCase = true) }
        }

        if (complete) {
            return normalized.associateWith { category ->
                existing.entries.first {
                    it.key.equals(category, ignoreCase = true)
                }.value
            }
        }

        val sounds = available(context).map { it.uri }
        return planCategorySounds(
            categories = normalized,
            existing = existing,
            available = sounds,
            fallback = defaultUri(context).toString(),
        )
    }

    /** URI que debe reproducir la alarma de una categoría. */
    fun resolveUri(
        context: Context,
        category: String,
        assigned: Map<String, String>,
    ): Uri {
        val stored = assigned.entries
            .firstOrNull { it.key.equals(category, ignoreCase = true) }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return stored
            ?.let { it.toUri() }
            ?: defaultUri(context)
    }

    /** Nombre legible del tono guardado, para mostrarlo en Ajustes. */
    fun displayName(context: Context, uriString: String?): String {
        if (uriString.isNullOrBlank()) return "Predeterminado del sistema"

        val uri = runCatching { uriString.toUri() }.getOrNull()
            ?: return "Sonido personalizado"
        val title = runCatching {
            RingtoneManager.getRingtone(context.applicationContext, uri)?.getTitle(context)
        }.getOrNull()

        return title?.trim()?.takeIf { it.isNotEmpty() } ?: "Sonido personalizado"
    }

    fun defaultUri(context: Context): Uri {
        val appContext = context.applicationContext
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Settings.System.DEFAULT_NOTIFICATION_URI
    }
}
