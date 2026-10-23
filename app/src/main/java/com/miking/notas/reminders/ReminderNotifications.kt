package com.miking.notas.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.miking.notas.R
import com.miking.notas.data.Note

/** Canal y construcción de las notificaciones de recordatorio. */
object ReminderNotifications {

    /**
     * El sonido lo reproduce [ReminderAlarmService] para poder mantener el
     * ciclo de 10 minutos sonando y 5 en silencio. El canal es silencioso para
     * no duplicar el tono; la vibración sí la gestiona Android.
     */
    const val CHANNEL_ID = "recordatorios_alarma_v2"
    private const val PREVIOUS_CHANNEL_ID = "recordatorios_alarma_v1"
    private const val LEGACY_CHANNEL_ID = "recordatorios"

    const val TEST_NOTIFICATION_ID = 7001
    private const val FULL_SCREEN_REQUEST_CODE = 0x46554C4C
    private const val STOP_REQUEST_CODE = 0x53544F50 // "STOP"

    private val ALARM_VIBRATION = longArrayOf(0, 700, 300, 700, 300, 1200, 300, 700, 300, 700)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarma de recordatorios",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alarma continua: suena 10 minutos, descansa 5 y se repite " +
                "hasta que la detengas."
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = ALARM_VIBRATION
            enableLights(true)
            lightColor = Color.RED
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.deleteNotificationChannel(PREVIOUS_CHANNEL_ID)
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager?.getNotificationChannel(CHANNEL_ID)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
            return false
        }

        return true
    }

    /**
     * Android 14+ exige permiso para que una notificación pueda abrir una
     * pantalla completa sobre el bloqueo.
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canUseFullScreenIntent()
    }

    /** Prueba completa: inicia el mismo ciclo de alarma que un recordatorio. */
    fun showTest(context: Context): Boolean {
        if (!canPostNotifications(context)) return false
        return runCatching {
            ReminderAlarmService.startTest(context)
            true
        }.getOrDefault(false)
    }

    fun alarmNotification(
        context: Context,
        note: Note?,
        testMode: Boolean,
    ): Notification {
        val notificationId = if (testMode || note == null) {
            TEST_NOTIFICATION_ID
        } else {
            ReminderScheduler.notificationId(note.id)
        }

        val preview = when {
            testMode -> "La pantalla debería encenderse y la alarma sonar hasta que la detengas."
            note != null -> note.preview.ifBlank { "Es hora de revisar esta nota." }
            else -> "Es hora de revisar esta nota."
        }
        val title = when {
            testMode -> "Prueba de alarma"
            note != null -> note.displayTitle
            else -> "Recordatorio"
        }

        val openIntent = if (testMode || note == null) {
            ReminderScheduler.appOpenIntent(context)
        } else {
            ReminderScheduler.noteOpenIntent(context, note.id)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle(title)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, if (testMode) "Abrir Notas" else "Abrir nota", openIntent)
            .addAction(
                0,
                "Detener alarma",
                stopAlarmIntent(context, notificationId),
            )

        if (canUseFullScreenIntent(context)) {
            builder.setFullScreenIntent(
                fullScreenIntent(context, if (testMode) null else note?.id),
                true,
            )
        }

        return builder.build()
    }

    fun cancel(context: Context, noteId: String) {
        NotificationManagerCompat.from(context)
            .cancel(ReminderScheduler.notificationId(noteId))
    }

    fun cancelTest(context: Context) {
        cancelById(context, TEST_NOTIFICATION_ID)
    }

    fun cancelById(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    private fun stopAlarmIntent(context: Context, notificationId: Int): PendingIntent {
        val appContext = context.applicationContext
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_STOP_ALARM
            putExtra(ReminderAlarmService.EXTRA_NOTIFICATION_ID, notificationId)
        }

        return PendingIntent.getBroadcast(
            appContext,
            notificationId xor STOP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun fullScreenIntent(context: Context, noteId: String?): PendingIntent {
        val appContext = context.applicationContext
        val intent = Intent(appContext, ReminderAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP

            if (noteId != null) {
                putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId)
            } else {
                putExtra(ReminderAlarmActivity.EXTRA_TEST_MODE, true)
            }
        }

        val requestCode = if (noteId != null) {
            ReminderScheduler.notificationId(noteId) xor FULL_SCREEN_REQUEST_CODE
        } else {
            FULL_SCREEN_REQUEST_CODE
        }

        return PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
