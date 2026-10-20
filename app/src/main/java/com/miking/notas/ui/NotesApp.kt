package com.miking.notas.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.withResumed
import com.miking.notas.R
import com.miking.notas.data.Note
import com.miking.notas.data.isRepeating
import com.miking.notas.data.nextReminderAt
import com.miking.notas.ui.theme.noteContainerColor
import kotlinx.coroutines.launch

@Composable
fun NotasApp(
    vm: NotesViewModel = viewModel(),
    pendingNoteId: String? = null,
    onPendingNoteHandled: () -> Unit = {},
) {
    val editor by vm.editor.collectAsStateWithLifecycle()
    val notes by vm.visibleNotes.collectAsStateWithLifecycle()
    val allNotes by vm.allNotes.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val categoryFilter by vm.categoryFilter.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val aiState by vm.aiState.collectAsStateWithLifecycle()
    val aiApiKey by vm.aiApiKey.collectAsStateWithLifecycle()
    val categoryUsage = remember(allNotes) {
        allNotes
            .filter { it.category.isNotBlank() }
            .groupingBy { it.category }
            .eachCount()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAi by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.NOTES) }

    LaunchedEffect(pendingNoteId, vm) {
        val noteId = pendingNoteId ?: return@LaunchedEffect
        val opened = vm.openNoteById(noteId)

        if (opened) {
            showSettings = false
        }
        onPendingNoteHandled()

        if (!opened) {
            scope.launch {
                snackbarHostState.showSnackbar("La nota de este recordatorio ya no existe")
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.rescheduleReminders()
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.NoteDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Nota eliminada",
                        actionLabel = "Deshacer",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.restore(event.note)
                    }
                }

                is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val current = editor
    when {
        showSettings -> SettingsScreen(
            settings = settings,
            categoryUsage = categoryUsage,
            aiApiKey = aiApiKey,
            onThemeMode = vm::setThemeMode,
            onDynamicColor = vm::setDynamicColor,
            onSortMode = vm::setSortMode,
            onBackgroundStyle = vm::setBackgroundStyle,
            onAddCategory = vm::addCategory,
            onDeleteCategory = vm::deleteCategory,
            onAiEnabled = vm::setAiEnabled,
            onAiModel = vm::setAiModel,
            onAiApiKey = vm::setAiApiKey,
            onAiTest = vm::aiTestConnection,
            onExport = vm::exportTo,
            onImport = vm::importFrom,
            onBack = { showSettings = false },
            snackbarHostState = snackbarHostState,
        )

        showAi -> AiAssistantScreen(
            state = aiState,
            aiEnabled = settings.aiEnabled,
            apiKey = aiApiKey,
            autoSpeak = settings.aiAutoSpeak,
            onPlan = vm::aiPlan,
            onAsk = vm::aiAsk,
            onSaveDrafts = vm::aiSaveDrafts,
            onSaveAnswer = vm::aiSaveAnswer,
            onAutoSpeakChange = vm::setAiAutoSpeak,
            onReset = vm::aiReset,
            onOpenSettings = { showSettings = true },
            onBack = { showAi = false },
            snackbarHostState = snackbarHostState,
        )

        current != null -> NoteEditorScreen(
            state = current,
            onBack = vm::saveAndClose,
            onFlushDraft = vm::flushDraft,
            onTitleChange = vm::updateTitle,
            onBodyChange = vm::updateBody,
            onTogglePin = vm::togglePinEditor,
            onColorChange = vm::setColor,
            categories = settings.categories,
            onCategoryChange = vm::setCategory,
            onAddCategory = vm::addCategory,
            onReminderChange = vm::setReminder,
            onAppendDictation = vm::appendDictation,
            onDeleteConfirmed = vm::deleteCurrent,
            snackbarHostState = snackbarHostState,
        )

        selectedTab == MainTab.CALENDAR -> CalendarScreen(
            notes = allNotes,
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            onOpenNote = vm::openNote,
        )

        selectedTab == MainTab.AGENDA -> AgendaScreen(
            notes = allNotes,
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            onOpenNote = vm::openNote,
        )

        else -> NotesListScreen(
            notes = notes,
            query = query,
            categories = settings.categories,
            categoryFilter = categoryFilter,
            onQueryChange = vm::setQuery,
            onCategorySelected = vm::setCategoryFilter,
            onAddCategory = vm::addCategory,
            onNewNote = vm::newNote,
            onVoiceNote = vm::createFromVoice,
            onOpenAi = { showAi = true },
            onOpenNote = vm::openNote,
            onTogglePin = vm::togglePin,
            onDelete = vm::delete,
            onOpenSettings = { showSettings = true },
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            snackbarHostState = snackbarHostState,
        )
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Listado                                                                                    */
/* ------------------------------------------------------------------------------------------ */

private enum class VoiceNoteStep {
    Title,
    Description,
    Reminder,
    Category,
}

@Composable
private fun NotesListScreen(
    notes: List<Note>,
    query: String,
    categories: List<String>,
    categoryFilter: String?,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onAddCategory: (String) -> Boolean,
    onNewNote: () -> Unit,
    onVoiceNote: (title: String, body: String, reminderAt: Long?, category: String) -> Unit,
    onOpenAi: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onTogglePin: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onOpenSettings: () -> Unit,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var pendingDelete by remember { mutableStateOf<Note?>(null) }
    var voiceStep by rememberSaveable { mutableStateOf<VoiceNoteStep?>(null) }
    var voiceTitle by rememberSaveable { mutableStateOf("") }
    var voiceBody by rememberSaveable { mutableStateOf("") }
    var voiceRequest by rememberSaveable { mutableIntStateOf(0) }
    var voiceReminder by rememberSaveable { mutableStateOf<Long?>(null) }
    var voiceCategory by rememberSaveable { mutableStateOf("") }
    var showReminderRetry by rememberSaveable { mutableStateOf(false) }
    var heardReminder by rememberSaveable { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cancelVoiceFlow: () -> Unit = {
        voiceStep = null
        voiceTitle = ""
        voiceBody = ""
        voiceReminder = null
        voiceCategory = ""
        showReminderRetry = false
        heardReminder = ""
    }

    val finishVoiceNote: () -> Unit = {
        val title = voiceTitle
        val body = voiceBody
        val reminderAt = voiceReminder
        val category = voiceCategory
        if (category.isNotBlank()) onAddCategory(category)
        cancelVoiceFlow()
        onVoiceNote(title, body, reminderAt, category)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        finishVoiceNote()
    }

    val completeVoiceNote: () -> Unit = {
        val needsPermission =
            voiceReminder != null &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finishVoiceNote()
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val step = voiceStep
        if (step == null) return@rememberLauncherForActivityResult

        if (result.resultCode != Activity.RESULT_OK) {
            cancelVoiceFlow()
            return@rememberLauncherForActivityResult
        }

        val text = recognizedSpeech(result.data)
        if (text.isNullOrBlank()) {
            cancelVoiceFlow()
            scope.launch {
                snackbarHostState.showSnackbar("No se reconoció lo que dijiste")
            }
            return@rememberLauncherForActivityResult
        }

        when (step) {
            VoiceNoteStep.Title -> {
                voiceTitle = text
                voiceStep = VoiceNoteStep.Description
                voiceRequest++
            }

            VoiceNoteStep.Description -> {
                voiceBody = text
                voiceStep = VoiceNoteStep.Reminder
                voiceRequest++
            }

            VoiceNoteStep.Reminder -> {
                voiceStep = null
                if (isSkipReminderCommand(text)) {
                    voiceReminder = null
                    voiceStep = VoiceNoteStep.Category
                    voiceRequest++
                } else {
                    val reminderAt = parseSpokenReminder(text)
                    if (reminderAt == null) {
                        heardReminder = text
                        showReminderRetry = true
                    } else {
                        voiceReminder = reminderAt
                        voiceStep = VoiceNoteStep.Category
                        voiceRequest++
                    }
                }
            }

            VoiceNoteStep.Category -> {
                voiceStep = null
                voiceCategory = resolveSpokenCategory(text, categories).orEmpty()
                completeVoiceNote()
            }
        }
    }

    LaunchedEffect(voiceStep, voiceRequest, lifecycleOwner) {
        val step = voiceStep ?: return@LaunchedEffect
        val prompt = when (step) {
            VoiceNoteStep.Title -> "Di el título de la nota"
            VoiceNoteStep.Description -> "Di la descripción de la nota"
            VoiceNoteStep.Reminder ->
                "Di la fecha y hora del recordatorio, por ejemplo: mañana a las 9. " +
                    "O di: sin recordatorio"
            VoiceNoteStep.Category ->
                "Di la categoría de la nota, por ejemplo: Trabajo. " +
                    "O di: sin categoría"
        }

        lifecycleOwner.lifecycle.withResumed {
            runCatching {
                voiceLauncher.launch(speechRecognitionIntent(prompt))
            }.onFailure {
                cancelVoiceFlow()
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "No hay un servicio de dictado disponible en este dispositivo",
                    )
                }
            }
        }
    }

    val startVoiceCapture: () -> Unit = {
        voiceTitle = ""
        voiceBody = ""
        voiceReminder = null
        voiceCategory = ""
        showReminderRetry = false
        heardReminder = ""
        voiceStep = VoiceNoteStep.Title
        voiceRequest++
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MainNavigationBar(selected = selectedTab, onSelected = onTabSelected)
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = onOpenAi,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ai),
                        contentDescription = "Asistente de IA",
                    )
                }
                SmallFloatingActionButton(
                    onClick = startVoiceCapture,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = "Crear nota por voz",
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = onNewNote,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Nueva nota") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                )
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 172.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(
                key = "header",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Header(
                    count = notes.size,
                    query = query,
                    onQueryChange = onQueryChange,
                    categories = categories,
                    categoryFilter = categoryFilter,
                    onCategorySelected = onCategorySelected,
                    onAddCategory = onAddCategory,
                    onOpenSettings = onOpenSettings,
                )
            }

            if (notes.isEmpty()) {
                item(
                    key = "empty",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    EmptyState(
                        hasQuery = query.isNotBlank(),
                        hasCategory = categoryFilter != null,
                    )
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        modifier = Modifier.animateItem(),
                        onClick = { onOpenNote(note) },
                        onTogglePin = { onTogglePin(note) },
                        onDelete = { pendingDelete = note },
                    )
                }
            }
        }
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar nota") },
            text = {
                Text(
                    "¿Seguro que quieres eliminar «${note.displayTitle}»? " +
                        "Puedes deshacerlo desde el aviso.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(note)
                        pendingDelete = null
                    },
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showReminderRetry) {
        AlertDialog(
            onDismissRequest = cancelVoiceFlow,
            title = { Text("No pude programar ese recordatorio") },
            text = {
                Text(
                    "Escuché: «$heardReminder».\n\n" +
                        "Di una fecha y hora futuras, por ejemplo: «mañana a las 9», " +
                        "«el viernes a las 18:30» o «en 2 horas».",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReminderRetry = false
                        voiceStep = VoiceNoteStep.Reminder
                        voiceRequest++
                    },
                ) {
                    Text("Reintentar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showReminderRetry = false
                        voiceReminder = null
                        voiceStep = VoiceNoteStep.Category
                        voiceRequest++
                    },
                ) {
                    Text("Sin recordatorio")
                }
            },
        )
    }
}

@Composable
private fun Header(
    count: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    categories: List<String>,
    categoryFilter: String?,
    onCategorySelected: (String?) -> Unit,
    onAddCategory: (String) -> Boolean,
    onOpenSettings: () -> Unit,
) {
    var showNewCategory by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notas",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        categoryFilter != null && count == 0 -> "0 notas · $categoryFilter"
                        categoryFilter != null && count == 1 -> "1 nota · $categoryFilter"
                        categoryFilter != null -> "$count notas · $categoryFilter"
                        count == 0 -> "Tu espacio para ideas"
                        count == 1 -> "1 nota"
                        else -> "$count notas"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Ajustes",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar notas") },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )

        Spacer(modifier = Modifier.height(12.dp))

        CategoryChips(
            categories = categories,
            selected = categoryFilter,
            onSelected = onCategorySelected,
            onNewCategory = { showNewCategory = true },
            includeAll = true,
        )
    }

    if (showNewCategory) {
        NewCategoryDialog(
            existing = categories,
            onDismiss = { showNewCategory = false },
            onCreate = { name ->
                val created = onAddCategory(name)
                if (created) onCategorySelected(name)
                created
            },
        )
    }
}

@Composable
private fun EmptyState(
    hasQuery: Boolean,
    hasCategory: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = when {
                hasQuery -> "Sin resultados"
                hasCategory -> "Sin notas en esta categoría"
                else -> "Aún no hay notas"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                hasQuery -> "Prueba con otra búsqueda."
                hasCategory -> "Elige otra categoría o crea una nota nueva."
                else -> "Toca «Nueva nota» o el micrófono. Por voz te pedirá título, " +
                    "descripción, recordatorio y categoría."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoteCard(
    note: Note,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val context = LocalContext.current
    val upcomingReminder = note.nextReminderAt()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = noteContainerColor(note.color)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 16.dp, bottom = 14.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            ) {
                if (note.category.isNotBlank()) {
                    Text(
                        text = note.category,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = note.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (note.preview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = note.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = relativeTime(note.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (note.pinned) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Fijada",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (upcomingReminder != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (note.isRepeating) {
                                "Se repite: ${repeatDaysShortLabel(note.repeatDays)} · " +
                                    formatReminderShort(context, upcomingReminder)
                            } else {
                                "Recordatorio: ${formatReminderShort(context, upcomingReminder)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Box {
                var menuOpen by remember { mutableStateOf(false) }

                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Opciones de la nota",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (note.pinned) "Dejar de fijar" else "Fijar nota") },
                        leadingIcon = {
                            Icon(Icons.Filled.Star, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onTogglePin()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Utilidades                                                                                 */
/* ------------------------------------------------------------------------------------------ */

internal fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    if (now - timestamp < DateUtils.MINUTE_IN_MILLIS) return "Ahora"

    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

internal fun wordCount(text: String): Int =
    text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
