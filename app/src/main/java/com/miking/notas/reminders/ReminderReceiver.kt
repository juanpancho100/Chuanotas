package com.miking.notas.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miking.notas.data.NotesRepository
import com.miking.notas.data.nextReminderAt

/** Recibe la alarma y arranca el ciclo de sonido del recordatorio. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP_ALARM -> {
                val notificationId = intent.getIntExtra(
                    ReminderAlarmService.EXTRA_NOTIFICATION_ID,
                    0,
                )
                if (notificationId != 0) {
                    ReminderNotifications.cancelById(context, notificationId)
                }
                ReminderAlarmService.stop(context)
            }

            ReminderScheduler.ACTION_TEST_REMINDER -> {
                ReminderNotifications.showTest(context)
            }

            ReminderScheduler.ACTION_REMINDER -> {
                val noteId = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_ID) ?: return
                val note = runCatching {
                    NotesRepository(context).load().firstOrNull { it.id == noteId }
                }.getOrNull() ?: return

                if (note.completed || note.reminderAt == null) {
                    ReminderScheduler.cancel(context, note.id)
                    return
                }

                if (ReminderNotifications.canPostNotifications(context)) {
                    runCatching { ReminderAlarmService.start(context, note.id) }
                }

                if (note.repeatDays.isNotEmpty()) {
                    val next = note.nextReminderAt(System.currentTimeMillis() + 1_000L)
                    if (next != null) {
                        ReminderScheduler.schedule(context, note.id, next)
                    } else {
                        ReminderScheduler.cancel(context, note.id)
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP_ALARM = "com.miking.notas.action.DISMISS_ALARM"
    }
}
