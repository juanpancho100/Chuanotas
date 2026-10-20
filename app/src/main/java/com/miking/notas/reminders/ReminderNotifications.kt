package com.miking.notas.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.miking.notas.R
import com.miking.notas.data.Note

/** Canal y construcción de las notificaciones de recordatorio. */
object ReminderNotifications {

    /** Canal nuevo: Android no permite cambiar el sonido de un canal ya creado. */
    const val CHANNEL_ID = "recordatorios_alarma_v1"
    private const val LEGACY_CHANNEL_ID = "recordatorios"
    private const val TEST_NOTIFICATION_ID = 7001
    private const val FULL_SCREEN_REQUEST_CODE = 0x46554C4C

    private val ALARM_VIBRATION = longArrayOf(0, 700, 300, 700, 300, 1200, 300, 700, 300, 700)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val soundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarma de recordatorios",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Recordatorios con tono de alarma y vibración"
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = ALARM_VIBRATION
            enableLights(true)
            lightColor = Color.RED
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
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

    /** Notificación de prueba para comprobar el tono de alarma y la vibración. */
    @SuppressLint("MissingPermission")
    fun showTest(context: Context): Boolean {
        if (!canPostNotifications(context)) return false

        ensureChannel(context)

        val text = "La pantalla debería encenderse y mostrar este mensaje."
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle("Prueba de alarma")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(ReminderScheduler.appOpenIntent(context))

        if (canUseFullScreenIntent(context)) {
            builder.setFullScreenIntent(fullScreenIntent(context, noteId = null), true)
        }

        val notification = builder.build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, note: Note) {
        if (!canPostNotifications(context)) return

        ensureChannel(context)

        val preview = note.preview.ifBlank { "Es hora de revisar esta nota." }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle(note.displayTitle)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(ReminderScheduler.noteOpenIntent(context, note.id))

        if (canUseFullScreenIntent(context)) {
            builder.setFullScreenIntent(fullScreenIntent(context, note.id), true)
        }

        val notification = builder.build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(ReminderScheduler.notificationId(note.id), notification)
        }
    }

    fun cancel(context: Context, noteId: String) {
        NotificationManagerCompat.from(context)
            .cancel(ReminderScheduler.notificationId(noteId))
    }

    fun cancelTest(context: Context) {
        NotificationManagerCompat.from(context).cancel(TEST_NOTIFICATION_ID)
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
