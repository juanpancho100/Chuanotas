package com.miking.notas.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miking.notas.data.NotesRepository

/**
 * Vuelve a programar los recordatorios después de reiniciar el teléfono,
 * actualizar la app, desbloquearlo, cambiar la hora/zona horaria o conceder
 * alarmas exactas.
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_USER_UNLOCKED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED,
            ACTION_QUICKBOOT_POWERON,
            ACTION_HTC_QUICKBOOT_POWERON -> {
                val notes = runCatching { NotesRepository(context).load() }.getOrDefault(emptyList())
                ReminderScheduler.rescheduleAll(context, notes)
            }
        }
    }

    private companion object {
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
