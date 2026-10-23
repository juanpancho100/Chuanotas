package com.miking.notas.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.miking.notas.R
import com.miking.notas.data.ReminderRepeat
import com.miking.notas.reminders.ReminderNotifications
import com.miking.notas.reminders.ReminderScheduler
import com.miking.notas.ui.theme.noteContainerColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteEditorScreen(
    state: EditorState,
    onBack: () -> Unit,
    onFlushDraft: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onTogglePin: () -> Unit,
    onToggleCompleted: () -> Unit,
    onColorChange: (Int) -> Unit,
    categories: List<String>,
    onCategoryChange: (String) -> Unit,
    onAddCategory: (String) -> Boolean,
    onReminderChange: (Long?, Set<Int>) -> Unit,
    onAppendDictation: (String) -> Unit,
    onDeleteConfirmed: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var confirmDelete by rememberSaveable(state.id) { mutableStateOf(false) }
    var showReminderPicker by rememberSaveable(state.id) { mutableStateOf(false) }
    var lastEditorId by rememberSaveable { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var exactAlarmsAllowed by remember {
        mutableStateOf(ReminderScheduler.canScheduleExact(context))
    }
    var notificationsAllowed by remember {
        mutableStateOf(ReminderNotifications.canPostNotifications(context))
    }

    BackHandler(onBack = onBack)

    // Si la app va a segundo plano (o se abre la hoja de compartir) no se pierde nada.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        onFlushDraft()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exactAlarmsAllowed = ReminderScheduler.canScheduleExact(context)
        notificationsAllowed = ReminderNotifications.canPostNotifications(context)
    }

    val openReminderPicker: () -> Unit = { showReminderPicker = true }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = ReminderNotifications.canPostNotifications(context)
        if (granted) {
            openReminderPicker()
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Activa las notificaciones para recibir recordatorios.",
                    actionLabel = "Abrir ajustes",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    openNotificationSettings(context)
                }
            }
        }
    }

    val askForReminder: () -> Unit = {
        val permissionMissing =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED

        when {
            permissionMissing -> notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS,
            )

            !ReminderNotifications.canPostNotifications(context) -> scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Las notificaciones están desactivadas para Notas.",
                    actionLabel = "Abrir ajustes",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    openNotificationSettings(context)
                }
            }

            else -> openReminderPicker()
        }
    }

    val dictationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = recognizedSpeech(result.data)
            if (text == null) {
                scope.launch {
                    snackbarHostState.showSnackbar("No se reconoció texto")
                }
            } else {
                onAppendDictation(text)
            }
        }
    }

    val startDictation: () -> Unit = {
        runCatching {
            dictationLauncher.launch(speechRecognitionIntent("Dicta el texto de la nota"))
        }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "No hay un servicio de dictado disponible en este dispositivo",
                )
            }
        }
    }

    LaunchedEffect(state.id) {
        if (lastEditorId != state.id) {
            lastEditorId = state.id
            scrollState.scrollTo(0)
        }
        if (state.isNew) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Scaffold(
        containerColor = noteContainerColor(state.color),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver y guardar")
                    }
                },
                actions = {
                    IconButton(onClick = startDictation) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mic),
                            contentDescription = "Dictar texto",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { shareNote(context, state) },
                        enabled = !state.isEmpty,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Compartir nota",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = if (state.pinned) "Dejar de fijar" else "Fijar nota",
                            tint = if (state.pinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Eliminar nota",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Guardar y volver",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 24.dp),
        ) {
            TextField(
                value = state.title,
                onValueChange = onTitleChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = "Título",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (state.completed) TextDecoration.LineThrough else null,
                ),
                colors = editorFieldColors(),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (index in 0..5) {
                    val selected = state.color == index
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(noteContainerColor(index))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                            .clickable { onColorChange(index) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            CompletionSection(
                completed = state.completed,
                onToggle = onToggleCompleted,
            )

            Spacer(modifier = Modifier.height(14.dp))

            CategorySection(
                category = state.category,
                categories = categories,
                onCategoryChange = onCategoryChange,
                onAddCategory = onAddCategory,
            )

            Spacer(modifier = Modifier.height(14.dp))

            ReminderSection(
                reminderAt = state.reminderAt,
                repeatDays = state.repeatDays,
                completed = state.completed,
                exactAlarmsAllowed = exactAlarmsAllowed,
                notificationsAllowed = notificationsAllowed,
                onChange = askForReminder,
                onClear = { onReminderChange(null, emptySet()) },
                onOpenExactAlarmSettings = { openExactAlarmSettings(context) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = state.body,
                onValueChange = onBodyChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp),
                placeholder = {
                    Text(
                        text = "Escribe algo…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 24.sp,
                    textDecoration = if (state.completed) TextDecoration.LineThrough else null,
                ),
                colors = editorFieldColors(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${wordCount(state.body)} palabras · ${state.body.length} caracteres",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Guardado automático",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showReminderPicker) {
        ReminderPickerDialog(
            initialAt = state.reminderAt,
            initialRepeatDays = state.repeatDays,
            onDismiss = { showReminderPicker = false },
            onSave = { at, days ->
                val normalizedDays = ReminderRepeat.sanitize(days)
                val isFuture = at > System.currentTimeMillis()

                if (normalizedDays.isEmpty() && !isFuture) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Elige una hora futura para el recordatorio")
                    }
                } else {
                    showReminderPicker = false
                    onReminderChange(at, normalizedDays)

                    if (!ReminderScheduler.canScheduleExact(context)) {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "El recordatorio puede retrasarse unos minutos.",
                                actionLabel = "Alarmas exactas",
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                openExactAlarmSettings(context)
                            }
                        }
                    }
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar nota") },
            text = { Text("Puedes deshacerlo desde el aviso que aparecerá al volver al listado.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteConfirmed()
                    },
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun CompletionSection(
    completed: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .toggleable(
                value = completed,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (completed) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = completed,
                onCheckedChange = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (completed) "Nota completada" else "Marcar como completada",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (completed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = if (completed) {
                        "Se muestra tachada y su recordatorio está detenido."
                    } else {
                        "Se tachará y se detendrá su recordatorio."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategorySection(
    category: String,
    categories: List<String>,
    onCategoryChange: (String) -> Unit,
    onAddCategory: (String) -> Boolean,
) {
    var showNewCategory by rememberSaveable { mutableStateOf(false) }

    Column {
        Text(
            text = "Categoría",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        CategoryChips(
            categories = categories,
            selected = category,
            onSelected = { onCategoryChange(it.orEmpty()) },
            onNewCategory = { showNewCategory = true },
        )
    }

    if (showNewCategory) {
        NewCategoryDialog(
            existing = categories,
            onDismiss = { showNewCategory = false },
            onCreate = { name ->
                val created = onAddCategory(name)
                if (created) onCategoryChange(name)
                created
            },
        )
    }
}

@Composable
private fun ReminderSection(
    reminderAt: Long?,
    repeatDays: Set<Int>,
    completed: Boolean,
    exactAlarmsAllowed: Boolean,
    notificationsAllowed: Boolean,
    onChange: () -> Unit,
    onClear: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
) {
    val context = LocalContext.current

    if (reminderAt == null) {
        OutlinedButton(
            onClick = onChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Añadir recordatorio")
        }
        return
    }

    val nextAt = if (completed) {
        null
    } else {
        ReminderRepeat.nextOccurrence(
            anchor = reminderAt,
            days = repeatDays,
            now = System.currentTimeMillis(),
        )
    }
    val isPast = nextAt == null
    val shownAt = nextAt ?: reminderAt

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isPast) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            completed -> "Recordatorio detenido"
                            isPast -> "Recordatorio vencido"
                            repeatDays.isNotEmpty() -> "Recordatorio repetido"
                            else -> "Recordatorio"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatReminder(context, shownAt),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (completed) {
                            "Se activará al desmarcar la nota"
                        } else if (repeatDays.isEmpty()) {
                            "Una vez"
                        } else {
                            "Se repite: ${repeatDaysLabel(repeatDays)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Quitar recordatorio")
                }
            }

            if (!notificationsAllowed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Notificaciones desactivadas: no verás el aviso.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { openNotificationSettings(context) }) {
                        Text("Activar")
                    }
                }
            }

            if (!exactAlarmsAllowed && !isPast && !completed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Android puede retrasarlo unos minutos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenExactAlarmSettings) {
                        Text("Activar")
                    }
                }
            }

            TextButton(onClick = onChange) {
                Text(
                    if (isPast && !completed) {
                        "Programar otra vez"
                    } else {
                        "Cambiar fecha, hora y repetición"
                    },
                )
            }
        }
    }
}

private fun shareNote(context: Context, state: EditorState) {
    val title = state.title.trim()
    val body = state.body.trim()
    if (title.isEmpty() && body.isEmpty()) return

    val text = if (title.isEmpty()) body else "$title\n\n$body"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir nota"))
}

@Composable
private fun editorFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
)
