package com.miking.notas.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.miking.notas.R
import com.miking.notas.ai.AiAnswer
import com.miking.notas.ai.AiNoteDraft
import com.miking.notas.ai.AiPlan
import kotlinx.coroutines.launch

private enum class AiTab { Create, Ask }

/**
 * El asistente de IA: dicta tareas y crea las notas, o pregunta por tus notas
 * y te lee la respuesta en voz alta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiAssistantScreen(
    state: AiUiState,
    aiEnabled: Boolean,
    apiKey: String,
    autoSpeak: Boolean,
    onPlan: (String) -> Unit,
    onAsk: (String) -> Unit,
    onSaveDrafts: (List<AiNoteDraft>) -> Unit,
    onSaveAnswer: (AiAnswer) -> Unit,
    onAutoSpeakChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val ready = aiEnabled && apiKey.isNotBlank()
    var tab by remember { mutableStateOf(AiTab.Create) }
    val speech = rememberSpeechOutput()

    // Cada respuesta nueva se lee sola. El contador del estado hace que
    // salte aunque la respuesta sea igual que la anterior.
    LaunchedEffect(state.answerSeq) {
        val answer = state.answer ?: return@LaunchedEffect
        if (!autoSpeak) return@LaunchedEffect

        val error = speech.speak(answer.answer)
        if (error != null) {
            snackbarHostState.showSnackbar(error)
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Asistente IA", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterChip(
                    selected = tab == AiTab.Create,
                    onClick = { tab = AiTab.Create },
                    label = { Text("Crear tareas") },
                )
                FilterChip(
                    selected = tab == AiTab.Ask,
                    onClick = { tab = AiTab.Ask },
                    label = { Text("Preguntar") },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (tab) {
                AiTab.Create -> AiCreateTab(
                    state = state,
                    ready = ready,
                    onPlan = onPlan,
                    onSaveDrafts = onSaveDrafts,
                    onReset = onReset,
                    onOpenSettings = onOpenSettings,
                    snackbarHostState = snackbarHostState,
                )

                AiTab.Ask -> AiAskTab(
                    state = state,
                    ready = ready,
                    speech = speech,
                    autoSpeak = autoSpeak,
                    onAsk = onAsk,
                    onSaveAnswer = onSaveAnswer,
                    onAutoSpeakChange = onAutoSpeakChange,
                    onOpenSettings = onOpenSettings,
                    snackbarHostState = snackbarHostState,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Crear tareas                                                                               */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun AiCreateTab(
    state: AiUiState,
    ready: Boolean,
    onPlan: (String) -> Unit,
    onSaveDrafts: (List<AiNoteDraft>) -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var transcript by remember { mutableStateOf("") }

    val dictationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val text = recognizedSpeech(result.data)
        if (text.isNullOrBlank()) {
            scope.launch {
                snackbarHostState.showSnackbar("No se reconoció lo que dijiste")
            }
            return@rememberLauncherForActivityResult
        }

        transcript = text
        onPlan(text)
    }

    val startDictation: () -> Unit = {
        scope.launch {
            lifecycleOwner.lifecycle.withResumed {
                try {
                    dictationLauncher.launch(
                        speechRecognitionIntent(
                            "Dicta tus tareas con su día y hora. Por ejemplo: mañana a las 10 " +
                                "tengo cita con el doctor y luego comprar pan a las 7 de la noche",
                        ),
                    )
                } catch (error: Exception) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "No hay un servicio de dictado disponible en este dispositivo",
                        )
                    }
                }
            }
        }
    }

    if (!ready) {
        AiNoticeCard(
            title = "La IA está desactivada",
            text = "Activa la inteligencia artificial y añade tu API key de DeepSeek en " +
                "Ajustes para poder usar el asistente.",
            actionLabel = "Abrir Ajustes",
            onAction = onOpenSettings,
        )
        return
    }

    AiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ai),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dicta tus tareas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Habla como quieras: la IA entiende el día, la hora y " +
                        "cada cosa que tengas que hacer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = startDictation,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Dictar con la IA")
        }

        if (transcript.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Has dicho:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (state.working == AiMode.Plan) {
        AiWorkingCard("Analizando con DeepSeek…")
        Spacer(modifier = Modifier.height(16.dp))
    }

    state.plan?.let { plan ->
        AiPlanPreview(
            plan = plan,
            onSave = onSaveDrafts,
            onDictateAgain = {
                transcript = ""
                onReset()
            },
        )
    }

    if (state.error != null && state.errorMode == AiMode.Plan) {
        Spacer(modifier = Modifier.height(16.dp))
        AiNoticeCard(
            title = "No se pudo completar",
            text = state.error,
            actionLabel = "Reintentar",
            onAction = startDictation,
        )
    }
}

@Composable
private fun AiPlanPreview(
    plan: AiPlan,
    onSave: (List<AiNoteDraft>) -> Unit,
    onDictateAgain: () -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(plan) {
        selected = plan.drafts.indices.toSet()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (plan.summary.isNotBlank()) {
            AiCard {
                Text(
                    text = plan.summary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (plan.drafts.isEmpty()) {
            AiNoticeCard(
                title = "No encontré ninguna tarea",
                text = "Prueba a dictar algo más explícito, por ejemplo: «mañana a las 10 " +
                    "tengo cita con el doctor».",
                actionLabel = "Volver a dictar",
                onAction = onDictateAgain,
            )
            return@Column
        }

        plan.drafts.forEachIndexed { index, draft ->
            AiDraftCard(
                draft = draft,
                checked = index in selected,
                onToggle = {
                    selected = if (index in selected) selected - index else selected + index
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onDictateAgain,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Volver a dictar")
            }
            Button(
                onClick = {
                    onSave(plan.drafts.filterIndexed { index, _ -> index in selected })
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    when (selected.size) {
                        0 -> "Nada"
                        1 -> "Guardar 1"
                        else -> "Guardar ${selected.size}"
                    },
                )
            }
        }
    }
}

@Composable
private fun AiDraftCard(
    draft: AiNoteDraft,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draft.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                if (draft.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = draft.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (draft.category.isNotBlank() || draft.reminderAt != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (draft.category.isNotBlank()) {
                            AiTag(draft.category)
                        }
                        draft.reminderAt?.let { reminder ->
                            AiTag(formatReminder(context, reminder))
                        }
                    }
                }

                if (draft.repeatDays.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Se repite: ${repeatDaysLabel(draft.repeatDays)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Preguntar                                                                                  */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun AiAskTab(
    state: AiUiState,
    ready: Boolean,
    speech: SpeechOutput,
    autoSpeak: Boolean,
    onAsk: (String) -> Unit,
    onSaveAnswer: (AiAnswer) -> Unit,
    onAutoSpeakChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var question by remember { mutableStateOf("") }

    val questionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val text = recognizedSpeech(result.data)
        if (text.isNullOrBlank()) {
            scope.launch {
                snackbarHostState.showSnackbar("No se reconoció la pregunta")
            }
            return@rememberLauncherForActivityResult
        }

        question = text
        onAsk(text)
    }

    val ask: (String) -> Unit = { text ->
        question = text
        onAsk(text)
    }

    if (!ready) {
        AiNoticeCard(
            title = "La IA está desactivada",
            text = "Activa la inteligencia artificial y añade tu API key de DeepSeek en " +
                "Ajustes para poder usar el asistente.",
            actionLabel = "Abrir Ajustes",
            onAction = onOpenSettings,
        )
        return
    }

    AiCard {
        Text(
            text = "Pregunta sobre tus notas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "La IA responde solo con lo que tienes guardado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("¿Qué tengo que hacer hoy?") },
            trailingIcon = {
                IconButton(
                    onClick = {
                        scope.launch {
                            lifecycleOwner.lifecycle.withResumed {
                                try {
                                    questionLauncher.launch(
                                        speechRecognitionIntent("Dices tu pregunta"),
                                    )
                                } catch (error: Exception) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "No hay un servicio de dictado disponible en este dispositivo",
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = "Dictar la pregunta",
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { ask(question) },
            enabled = question.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Preguntar")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Qué tengo que hacer hoy",
                "Qué tengo que hacer mañana",
                "Resumen de la semana",
            ).forEach { suggestion ->
                TextButton(onClick = { ask(suggestion) }) {
                    Text(suggestion, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Leer la respuesta en voz alta",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Suena sola en cuanto llega la respuesta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = autoSpeak, onCheckedChange = onAutoSpeakChange)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (state.working == AiMode.Answer) {
        AiWorkingCard("Consultando tus notas…")
        Spacer(modifier = Modifier.height(16.dp))
    }

    state.answer?.let { answer ->
        AiAnswerCard(
            answer = answer,
            onListen = {
                val error = speech.speak(answer.answer)
                if (error != null) {
                    scope.launch { snackbarHostState.showSnackbar(error) }
                }
            },
            onStop = { speech.stop() },
            onSave = { onSaveAnswer(answer) },
        )
    }

    if (state.error != null && state.errorMode == AiMode.Answer) {
        Spacer(modifier = Modifier.height(16.dp))
        AiNoticeCard(
            title = "No se pudo responder",
            text = state.error,
            actionLabel = "Reintentar",
            onAction = { ask(question) },
        )
    }
}

@Composable
private fun AiAnswerCard(
    answer: AiAnswer,
    onListen: () -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
) {
    AiCard {
        Text(
            text = answer.answer,
            style = MaterialTheme.typography.bodyLarge,
        )

        if (answer.sources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "Notas usadas: ${answer.sources.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onListen,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_speaker),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Escuchar")
            }
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Guardar como nota")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        TextButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Text("Dejar de leer", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Piezas comunes                                                                             */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun AiCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun AiTag(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun AiWorkingCard(text: String) {
    AiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AiNoticeCard(
    title: String,
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}
