package com.miking.notas.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.miking.notas.BuildConfig
import com.miking.notas.ai.AiConfig
import com.miking.notas.data.AppSettings
import com.miking.notas.data.BackgroundStyle
import com.miking.notas.data.SortMode
import com.miking.notas.data.ThemeMode
import com.miking.notas.reminders.ReminderNotifications
import com.miking.notas.reminders.ReminderScheduler
import com.miking.notas.ui.theme.backgroundPreviewColor
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    categoryUsage: Map<String, Int>,
    aiApiKey: String,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onSortMode: (SortMode) -> Unit,
    onBackgroundStyle: (BackgroundStyle) -> Unit,
    onAddCategory: (String) -> Boolean,
    onDeleteCategory: (String) -> Unit,
    onAiEnabled: (Boolean) -> Unit,
    onAiModel: (String) -> Unit,
    onAiApiKey: (String) -> Unit,
    onAiTest: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var pendingCategoryDelete by remember { mutableStateOf<String?>(null) }
    var showNewCategory by rememberSaveable { mutableStateOf(false) }
    var keyText by remember(aiApiKey) { mutableStateOf(aiApiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permissionsVersion by remember { mutableIntStateOf(0) }

    val notificationsAllowed = remember(permissionsVersion) {
        ReminderNotifications.canPostNotifications(context)
    }
    val exactAlarmsAllowed = remember(permissionsVersion) {
        ReminderScheduler.canScheduleExact(context)
    }
    val fullScreenAllowed = remember(permissionsVersion) {
        ReminderNotifications.canUseFullScreenIntent(context)
    }
    val batteryUnrestricted = remember(permissionsVersion) {
        isIgnoringBatteryOptimizations(context)
    }
    val isHyperOs = remember { isHyperOsDevice() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionsVersion++
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) onExport(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) pendingImport = uri
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.SemiBold) },
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
            SectionTitle("Apariencia")

            SettingsCard {
                ChoiceRow(
                    title = "Sistema",
                    subtitle = "Sigue el tema del teléfono",
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeMode(ThemeMode.SYSTEM) },
                )
                ChoiceRow(
                    title = "Claro",
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { onThemeMode(ThemeMode.LIGHT) },
                )
                ChoiceRow(
                    title = "Oscuro",
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { onThemeMode(ThemeMode.DARK) },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            onClick = { onDynamicColor(!settings.dynamicColor) },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Color dinámico",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                "Usa los colores del fondo de pantalla"
                            } else {
                                "Requiere Android 12 o superior"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.dynamicColor,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        onCheckedChange = onDynamicColor,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Text(
                    text = "Fondo de la vista",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Elige el tono de fondo de la app. Se adapta al modo claro u oscuro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BackgroundStyle.entries.forEach { style ->
                        BackgroundSwatch(
                            style = style,
                            selected = settings.backgroundStyle == style,
                            dynamicColor = settings.dynamicColor,
                            onClick = { onBackgroundStyle(style) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("Listado")

            SettingsCard {
                ChoiceRow(
                    title = "Última edición",
                    subtitle = "Las notas editadas más recientemente primero",
                    selected = settings.sortMode == SortMode.UPDATED,
                    onClick = { onSortMode(SortMode.UPDATED) },
                )
                ChoiceRow(
                    title = "Fecha de creación",
                    selected = settings.sortMode == SortMode.CREATED,
                    onClick = { onSortMode(SortMode.CREATED) },
                )
                ChoiceRow(
                    title = "Título A–Z",
                    selected = settings.sortMode == SortMode.TITLE,
                    onClick = { onSortMode(SortMode.TITLE) },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("Categorías")

            Text(
                text = "Organiza tus notas por temas. Puedes asignar una categoría desde " +
                    "la nota y filtrar el listado con los chips de la cabecera.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )

            SettingsCard {
                if (settings.categories.isEmpty()) {
                    Text(
                        text = "Aún no hay categorías. Crea la primera para agrupar tus notas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    settings.categories.forEachIndexed { index, category ->
                        if (index > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                        CategorySettingsRow(
                            category = category,
                            count = categoryUsage.entries
                                .firstOrNull { it.key.equals(category, ignoreCase = true) }
                                ?.value ?: 0,
                            onDelete = { pendingCategoryDelete = category },
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                }

                OutlinedButton(
                    onClick = { showNewCategory = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Añadir categoría")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("Recordatorios")

            Text(
                text = "Cada nota puede tener una alarma opcional. Suena una vez o se " +
                    "repite los días que elijas, con el tono de alarma del sistema. " +
                    "También vibra y enciende la pantalla para mostrar la nota. Android 13 " +
                    "o superior pedirá permiso para mostrar la notificación.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )

            SettingsCard {
                StatusRow(
                    title = "Notificaciones",
                    subtitle = if (notificationsAllowed) {
                        "Activadas: verás los avisos de tus recordatorios."
                    } else {
                        "Desactivadas: no se mostrarán los avisos."
                    },
                    actionLabel = "Ajustes",
                    onAction = { openNotificationSettings(context) },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    StatusRow(
                        title = "Alarmas exactas",
                        subtitle = if (exactAlarmsAllowed) {
                            "Activadas: el aviso llega a la hora indicada."
                        } else {
                            "Desactivadas: Android puede retrasar el aviso unos minutos."
                        },
                        actionLabel = if (exactAlarmsAllowed) null else "Permitir",
                        onAction = if (exactAlarmsAllowed) {
                            null
                        } else {
                            { openExactAlarmSettings(context) }
                        },
                    )
                } else {
                    StatusRow(
                        title = "Alarmas",
                        subtitle = "Este Android programa los recordatorios a la hora indicada.",
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                StatusRow(
                    title = "Pantalla completa",
                    subtitle = if (fullScreenAllowed) {
                        "La alarma encenderá la pantalla y mostrará la nota sobre el bloqueo."
                    } else {
                        "Android 14+ necesita permiso para encender la pantalla al sonar."
                    },
                    actionLabel = if (fullScreenAllowed) null else "Permitir",
                    onAction = if (fullScreenAllowed) {
                        null
                    } else {
                        { openFullScreenIntentSettings(context) }
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                StatusRow(
                    title = "Segundo plano",
                    subtitle = if (batteryUnrestricted) {
                        "Sin restricciones de batería: las alarmas pueden sonar con la app cerrada."
                    } else {
                        "El ahorro de batería puede retrasar o bloquear las alarmas."
                    },
                    actionLabel = if (batteryUnrestricted) "Ajustes" else "Permitir",
                    onAction = {
                        if (batteryUnrestricted) {
                            openAppDetailsSettings(context)
                        } else {
                            requestIgnoreBatteryOptimizations(context)
                        }
                    },
                )

                if (isHyperOs) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    StatusRow(
                        title = "Inicio automático (HyperOS)",
                        subtitle = "Actívalo para que las alarmas se recuperen tras reiniciar o " +
                            "actualizar la app.",
                        actionLabel = "Abrir",
                        onAction = { openHyperOsAutostartSettings(context) },
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    StatusRow(
                        title = "Permisos de HyperOS",
                        subtitle = "Activa «Mostrar ventanas emergentes» y «Mostrar en pantalla " +
                            "de bloqueo» si la alarma no aparece.",
                        actionLabel = "Abrir",
                        onAction = { openHyperOsPermissionsSettings(context) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    if (notificationsAllowed) {
                        ReminderNotifications.showTest(context)
                    } else {
                        openNotificationSettings(context)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    if (notificationsAllowed) {
                        "Probar sonido y pantalla"
                    } else {
                        "Activar notificaciones"
                    },
                )
            }

            if (notificationsAllowed) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val scheduled = ReminderScheduler.scheduleTest(context)
                        val exact = ReminderScheduler.canScheduleExact(context)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = when {
                                    !scheduled -> "No se pudo programar la alarma de prueba."

                                    exact -> "Alarma de prueba en 10 segundos. Bloquea el " +
                                        "teléfono o sal de la app para comprobarla."

                                    else -> "Alarma de prueba programada. Sin alarmas exactas " +
                                        "puede tardar unos minutos."
                                },
                                duration = SnackbarDuration.Long,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Probar alarma en 10 segundos")
                }

                Text(
                    text = "Si la prueba no suena o no enciende la pantalla, revisa " +
                        "«Pantalla completa», «Alarmas exactas», «Segundo plano» y el inicio " +
                        "automático de HyperOS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("Inteligencia artificial")

            Text(
                text = "Conecta DeepSeek para dictar tus tareas y preguntarle a la app por " +
                    "tus notas. Solo se envía información cuando usas el asistente, " +
                    "nunca por tiempo. Tu API key se guarda cifrada en el dispositivo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )

            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAiEnabled(!settings.aiEnabled) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Activar el asistente IA",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Aparece un botón con la chispa en el listado, junto al " +
                                "micrófono.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.aiEnabled,
                        onCheckedChange = onAiEnabled,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Text(
                    text = "API key de DeepSeek",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Se cifra con la clave del AndroidKeyStore y no sale del teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = keyText,
                    onValueChange = {
                        keyText = it
                        onAiApiKey(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { keyVisible = !keyVisible }) {
                            Text(if (keyVisible) "Ocultar" else "Ver")
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Modelo",
                    style = MaterialTheme.typography.bodyLarge,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiConfig.MODELS.forEach { model ->
                        FilterChip(
                            selected = settings.aiModel == model,
                            onClick = { onAiModel(model) },
                            label = { Text(AiConfig.modelLabel(model)) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onAiTest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Probar conexión")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("Copia de seguridad")

            Text(
                text = "Exporta todas tus notas a un archivo JSON o recupéralas en otro " +
                    "dispositivo. Las notas se guardan solo en este teléfono.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = {
                    exportLauncher.launch("notas-${LocalDate.now()}.json")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Exportar notas")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Importar notas")
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("Acerca de")

            SettingsCard {
                Text(
                    text = "Notas ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Kotlin + Jetpack Compose · Material 3\n" +
                        "Compatible con HyperOS y Android 16",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Importar notas") },
            text = {
                Text(
                    "Se añadirán las notas que no existan o cuya versión sea más reciente. " +
                        "Las notas actuales no se borrarán.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImport = null
                        onImport(uri)
                    },
                ) {
                    Text("Importar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showNewCategory) {
        NewCategoryDialog(
            existing = settings.categories,
            onDismiss = { showNewCategory = false },
            onCreate = { name ->
                val created = onAddCategory(name)
                if (created) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Categoría creada")
                    }
                }
                created
            },
        )
    }

    pendingCategoryDelete?.let { category ->
        val count = categoryUsage.entries
            .firstOrNull { it.key.equals(category, ignoreCase = true) }
            ?.value ?: 0
        val detail = when (count) {
            0 -> "No hay notas en esta categoría."
            1 -> "Se quitará de 1 nota. La nota no se eliminará."
            else -> "Se quitará de $count notas. Las notas no se eliminarán."
        }

        AlertDialog(
            onDismissRequest = { pendingCategoryDelete = null },
            title = { Text("Eliminar categoría") },
            text = {
                Text("«$category»\n\n$detail")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCategoryDelete = null
                        onDeleteCategory(category)
                        scope.launch {
                            snackbarHostState.showSnackbar("Categoría eliminada")
                        }
                    },
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategoryDelete = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun CategorySettingsRow(
    category: String,
    count: Int,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = category, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = when (count) {
                    1 -> "1 nota"
                    else -> "$count notas"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Eliminar categoría $category",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackgroundSwatch(
    style: BackgroundStyle,
    selected: Boolean,
    dynamicColor: Boolean,
    onClick: () -> Unit,
) {
    val preview = backgroundPreviewColor(style, dynamicColor)

    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = preview,
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Fondo ${style.label} seleccionado",
                        tint = if (preview.luminance() > 0.5f) {
                            Color(0xFF1B1B21)
                        } else {
                            Color.White
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = style.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else null,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
