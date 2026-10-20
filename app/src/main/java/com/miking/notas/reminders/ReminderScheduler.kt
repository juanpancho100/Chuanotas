package com.miking.notas.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.miking.notas.MainActivity
import com.miking.notas.data.Note
import com.miking.notas.data.nextReminderAt

/**
 * Programa y cancela alarmas del sistema para los recordatorios de las notas.
 *
 * Se usa `setAlarmClock` cuando Android permite alarmas exactas: es el tipo de
 * alarma más fiable en segundo plano y en modo Doze. Si no hay permiso, se usa
 * una alarma inexacta como respaldo.
 */
object ReminderScheduler {

    const val ACTION_REMINDER = "com.miking.notas.action.REMINDER"
    const val ACTION_TEST_REMINDER = "com.miking.notas.action.TEST_REMINDER"
    const val ACTION_OPEN_NOTE = "com.miking.notas.action.OPEN_NOTE"
    const val EXTRA_NOTE_ID = "com.miking.notas.extra.NOTE_ID"

    private const val ALARM_SCHEME = "notas"
    private const val ALARM_HOST_REMINDER = "recordatorio"
    private const val ALARM_HOST_TEST = "prueba"
    private const val TEST_REQUEST_CODE = 0x54455354 // "TEST"

    /** Programa la próxima vez que debe sonar una nota, tenga o no repetición. */
    fun schedule(context: Context, note: Note) {
        val at = note.nextReminderAt() ?: run {
            cancel(context, note.id)
            return
        }
        schedule(context, note.id, at)
    }

    fun schedule(context: Context, noteId: String, at: Long) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()

        if (at <= now) {
            cancel(appContext, noteId)
            return
        }

        val operation = pendingIntent(
            context = appContext,
            noteId = noteId,
            flags = PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return

        setAlarm(
            context = appContext,
            at = at,
            operation = operation,
            showIntent = noteOpenIntent(appContext, noteId),
        )
    }

    /**
     * Programa una alarma de prueba. Sirve para comprobar si el teléfono
     * entrega alarmas con la app cerrada o bloqueada.
     */
    fun scheduleTest(context: Context, delayMillis: Long = 10_000L): Boolean {
        val appContext = context.applicationContext
        val at = System.currentTimeMillis() + delayMillis.coerceAtLeast(1_000L)
        val operation = PendingIntent.getBroadcast(
            appContext,
            TEST_REQUEST_CODE,
            Intent(appContext, ReminderReceiver::class.java).apply {
                action = ACTION_TEST_REMINDER
                data = Uri.parse("$ALARM_SCHEME://$ALARM_HOST_TEST")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return setAlarm(
            context = appContext,
            at = at,
            operation = operation,
            showIntent = appOpenIntent(appContext),
        )
    }

    fun cancel(context: Context, noteId: String) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(
            context = appContext,
            noteId = noteId,
            flags = PendingIntent.FLAG_NO_CREATE,
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun rescheduleAll(context: Context, notes: List<Note>) {
        notes.forEach { note ->
            if (note.reminderAt != null) {
                schedule(context, note)
            }
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true

        val alarmManager = context.applicationContext
            .getSystemService(AlarmManager::class.java) ?: return false

        return alarmManager.canScheduleExactAlarms()
    }

    fun notificationId(noteId: String): Int = noteId.hashCode() and 0x7FFFFFFF

    /** PendingIntent que abre la nota desde la alarma o la notificación. */
    fun noteOpenIntent(context: Context, noteId: String): PendingIntent {
        val appContext = context.applicationContext
        return PendingIntent.getActivity(
            appContext,
            noteId.hashCode(),
            noteIntent(appContext, noteId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Intent que abre una nota concreta en la pantalla principal. */
    fun noteIntent(context: Context, noteId: String): Intent {
        val appContext = context.applicationContext
        return Intent(appContext, MainActivity::class.java).apply {
            action = ACTION_OPEN_NOTE
            data = Uri.parse("notas://nota/${Uri.encode(noteId)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NOTE_ID, noteId)
        }
    }

    private fun setAlarm(
        context: Context,
        at: Long,
        operation: PendingIntent,
        showIntent: PendingIntent,
    ): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        val exact = canScheduleExact(context)

        if (exact) {
            val alarmClockSet = runCatching {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(at, showIntent),
                    operation,
                )
            }.isSuccess

            if (alarmClockSet) return true
        }

        return try {
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    at,
                    operation,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    at,
                    operation,
                )
            }
            true
        } catch (_: SecurityException) {
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    at,
                    operation,
                )
            }.isSuccess
        }
    }

    /** Intent que abre la app sin una nota concreta. */
    fun appIntent(context: Context): Intent {
        val appContext = context.applicationContext
        return Intent(appContext, MainActivity::class.java).apply {
            action = "com.miking.notas.action.OPEN_TEST"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    /** PendingIntent que abre la app sin una nota concreta. */
    fun appOpenIntent(context: Context): PendingIntent {
        val appContext = context.applicationContext
        return PendingIntent.getActivity(
            appContext,
            TEST_REQUEST_CODE + 1,
            appIntent(appContext),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun pendingIntent(
        context: Context,
        noteId: String,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = Uri.parse("$ALARM_SCHEME://$ALARM_HOST_REMINDER/${Uri.encode(noteId)}")
            putExtra(EXTRA_NOTE_ID, noteId)
        }

        return PendingIntent.getBroadcast(
            context,
            noteId.hashCode(),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
