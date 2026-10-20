package com.miking.notas.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miking.notas.data.NotesRepository
import com.miking.notas.data.nextReminderAt

/** Recibe la alarma y publica la notificación del recordatorio. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_TEST_REMINDER -> {
                ReminderNotifications.showTest(context)
            }

            ReminderScheduler.ACTION_REMINDER -> {
                val noteId = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_ID) ?: return
                val note = runCatching {
                    NotesRepository(context).load().firstOrNull { it.id == noteId }
                }.getOrNull() ?: return

                if (note.reminderAt != null) {
                    ReminderNotifications.show(context, note)

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
    }
}
