package com.miking.notas.reminders

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.miking.notas.data.CategoryAlarmSounds
import com.miking.notas.data.Note
import com.miking.notas.data.NotesRepository
import com.miking.notas.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Mantiene viva la alarma mientras no se detenga.
 *
 * El tono se reproduce en bucle durante 10 minutos, se pausa 5 minutos y se
 * repite hasta que la pantalla de alarma envía [ACTION_STOP] o la persona abre
 * la nota. Al ser un servicio en primer plano, sigue sonando con la pantalla
 * apagada o con la app en segundo plano.
 */
class ReminderAlarmService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var ringJob: Job? = null
    private var player: MediaPlayer? = null
    private var activeSoundUri: Uri? = null
    private var currentNotificationId: Int = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var hasAudioFocus = false
    private var audioManager: AudioManager? = null

    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(ALARM_ATTRIBUTES)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { }
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                if (notificationId != 0) {
                    ReminderNotifications.cancelById(this, notificationId)
                }
                stopSession()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> Unit
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val testMode = intent.getBooleanExtra(EXTRA_TEST_MODE, false)
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        val note: Note? = if (testMode) {
            null
        } else {
            val id = noteId ?: run {
                stopSelf()
                return START_NOT_STICKY
            }
            runCatching {
                NotesRepository(this).load().firstOrNull { it.id == id }
            }.getOrNull() ?: run {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (note?.completed == true) {
            stopSelf()
            return START_NOT_STICKY
        }

        ReminderNotifications.ensureChannel(this)

        val soundUri = if (testMode) {
            CategoryAlarmSounds.defaultUri(this)
        } else {
            val assigned = SettingsStore(this).settings.value.categoryAlarmSounds
            CategoryAlarmSounds.resolveUri(this, note?.category.orEmpty(), assigned)
        }

        val notificationId = when {
            testMode -> ReminderNotifications.TEST_NOTIFICATION_ID
            note != null -> ReminderScheduler.notificationId(note.id)
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val notification = ReminderNotifications.alarmNotification(
            context = this,
            note = note,
            testMode = testMode,
        )

        val previousNotificationId = currentNotificationId
        val started = runCatching {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        }.isSuccess

        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (previousNotificationId != 0 && previousNotificationId != notificationId) {
            ReminderNotifications.cancelById(this, previousNotificationId)
        }

        currentNotificationId = notificationId
        activeNoteId = note?.id
        isRunning = true

        acquireWakeLock()
        startRinging(soundUri)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSession()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRinging(soundUri: Uri) {
        activeSoundUri = soundUri
        ringJob?.cancel()
        releasePlayer()
        abandonAudioFocus()

        ringJob = serviceScope.launch {
            var elapsed = 0L
            while (isActive) {
                if (AlarmCycle.isRinging(elapsed)) {
                    startTone()
                } else {
                    pauseTone()
                }

                val wait = AlarmCycle.remainingInPhase(elapsed)
                delay(wait)
                elapsed += wait
            }
        }
    }

    private fun startTone() {
        val mediaPlayer = ensurePlayer() ?: return
        requestAudioFocus()
        runCatching {
            if (!mediaPlayer.isPlaying) mediaPlayer.start()
        }
    }

    private fun pauseTone() {
        runCatching {
            if (player?.isPlaying == true) player?.pause()
        }
        abandonAudioFocus()
    }

    private fun ensurePlayer(): MediaPlayer? {
        player?.let { return it }

        val requested = activeSoundUri ?: CategoryAlarmSounds.defaultUri(this)
        createPlayer(requested)?.let { created ->
            player = created
            return created
        }

        val fallback = CategoryAlarmSounds.defaultUri(this)
        if (fallback == requested) return null

        return createPlayer(fallback)?.also { player = it }
    }

    private fun createPlayer(uri: Uri): MediaPlayer? {
        val candidate = runCatching { MediaPlayer() }.getOrNull() ?: return null

        return runCatching {
            candidate.apply {
                setAudioAttributes(ALARM_ATTRIBUTES)
                setDataSource(this@ReminderAlarmService, uri)
                isLooping = true
                prepare()
            }
        }.getOrElse {
            runCatching { candidate.release() }
            null
        }
    }

    private fun releasePlayer() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.pause()
                it.release()
            }
        }
        player = null
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val manager = audioManager ?: getSystemService(AudioManager::class.java)?.also {
            audioManager = it
        } ?: return

        hasAudioFocus =
            manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        audioManager?.abandonAudioFocusRequest(focusRequest)
        hasAudioFocus = false
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Notas::Alarma")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun stopSession() {
        val wasRunning = isRunning || currentNotificationId != 0

        ringJob?.cancel()
        ringJob = null
        pauseTone()
        releasePlayer()
        releaseWakeLock()

        activeSoundUri = null
        currentNotificationId = 0
        activeNoteId = null
        isRunning = false

        stopForeground(STOP_FOREGROUND_REMOVE)

        if (wasRunning) {
            sendBroadcast(
                Intent(ACTION_STOPPED).setPackage(packageName),
            )
        }
    }

    companion object {
        const val ACTION_START = "com.miking.notas.action.START_ALARM"
        const val ACTION_STOP = "com.miking.notas.action.STOP_ALARM_SERVICE"
        const val ACTION_STOPPED = "com.miking.notas.action.ALARM_STOPPED"
        const val EXTRA_NOTE_ID = ReminderScheduler.EXTRA_NOTE_ID
        const val EXTRA_TEST_MODE = "com.miking.notas.extra.ALARM_TEST_MODE"
        const val EXTRA_NOTIFICATION_ID = "com.miking.notas.extra.ALARM_NOTIFICATION_ID"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var activeNoteId: String? = null
            private set

        private val ALARM_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        fun start(context: Context, noteId: String) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, ReminderAlarmService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NOTE_ID, noteId)
            }
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun startTest(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, ReminderAlarmService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TEST_MODE, true)
            }
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            appContext.stopService(Intent(appContext, ReminderAlarmService::class.java))
        }

        /** Detiene la alarma solo si pertenece a la nota indicada. */
        fun stopForNote(context: Context, noteId: String) {
            if (activeNoteId == noteId) stop(context)
        }
    }
}
