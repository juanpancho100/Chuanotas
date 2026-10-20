package com.miking.notas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miking.notas.reminders.ReminderNotifications
import com.miking.notas.reminders.ReminderScheduler
import com.miking.notas.ui.NotasApp
import com.miking.notas.ui.NotesViewModel
import com.miking.notas.ui.theme.NotasTheme

class MainActivity : ComponentActivity() {

    private var pendingNoteId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderNotifications.ensureChannel(this)
        pendingNoteId = intent?.getStringExtra(ReminderScheduler.EXTRA_NOTE_ID)

        setContent {
            val vm: NotesViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()

            NotasTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                backgroundStyle = settings.backgroundStyle,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NotasApp(
                        vm = vm,
                        pendingNoteId = pendingNoteId,
                        onPendingNoteHandled = { pendingNoteId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNoteId = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_ID)
    }
}
