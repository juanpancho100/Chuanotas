package com.miking.notas.reminders

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miking.notas.data.Note
import com.miking.notas.data.NotesRepository
import com.miking.notas.data.reminderAtOn
import com.miking.notas.ui.formatReminder
import com.miking.notas.ui.repeatDaysShortLabel
import com.miking.notas.ui.theme.NotasTheme
import java.time.LocalDate

/**
 * Pantalla que se muestra cuando suena un recordatorio. Enciende el display,
 * aparece sobre la pantalla de bloqueo y permite abrir la nota o detener el aviso.
 */
class ReminderAlarmActivity : ComponentActivity() {

    private var currentNote by mutableStateOf<Note?>(null)
    private var currentNoteId: String? = null
    private var testMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        enableEdgeToEdge()
        bindIntent(intent)

        setContent {
            NotasTheme {
                AlarmScreen(
                    note = currentNote,
                    testMode = testMode,
                    onOpen = ::openCurrent,
                    onDismiss = ::dismissAlarm,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bindIntent(intent)
    }

    private fun bindIntent(intent: Intent?) {
        testMode = intent?.getBooleanExtra(EXTRA_TEST_MODE, false) == true
        currentNoteId = intent?.getStringExtra(ReminderScheduler.EXTRA_NOTE_ID)
        currentNote = if (testMode || currentNoteId == null) {
            null
        } else {
            runCatching {
                NotesRepository(this).load().firstOrNull { it.id == currentNoteId }
            }.getOrNull()
        }
    }

    private fun openCurrent() {
        val noteId = currentNoteId

        if (testMode || noteId == null) {
            ReminderNotifications.cancelTest(this)
            startActivity(ReminderScheduler.appIntent(this))
        } else {
            ReminderNotifications.cancel(this, noteId)
            startActivity(ReminderScheduler.noteIntent(this, noteId))
        }

        finish()
    }

    private fun dismissAlarm() {
        val noteId = currentNoteId

        if (testMode || noteId == null) {
            ReminderNotifications.cancelTest(this)
        } else {
            ReminderNotifications.cancel(this, noteId)
        }

        finish()
    }

    private fun configureWindow() {
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    companion object {
        const val EXTRA_TEST_MODE = "com.miking.notas.extra.TEST_MODE"
    }
}

@Composable
private fun AlarmScreen(
    note: Note?,
    testMode: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val title = when {
        testMode -> "Prueba de alarma"
        note != null -> note.displayTitle
        else -> "Recordatorio"
    }
    val body = when {
        testMode -> "La pantalla se encendió y el mensaje se está mostrando correctamente."
        note != null -> note.body.trim().ifBlank { "Es hora de revisar esta nota." }
        else -> "La nota de este recordatorio ya no está disponible."
    }
    val reminderAt = note?.let { current ->
        if (current.repeatDays.isEmpty()) {
            current.reminderAt
        } else {
            current.reminderAtOn(LocalDate.now()) ?: current.reminderAt
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (testMode) "PRUEBA DE ALARMA" else "RECORDATORIO",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )

            if (!testMode && reminderAt != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatReminder(context, reminderAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (!testMode && note != null && note.repeatDays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Se repite: ${repeatDaysShortLabel(note.repeatDays)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(if (testMode) "Abrir Notas" else "Abrir nota")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text("Detener alarma")
            }
        }
    }
}
