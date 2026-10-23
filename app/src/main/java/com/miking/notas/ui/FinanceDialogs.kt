package com.miking.notas.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.miking.notas.R
import com.miking.notas.finance.EntryType
import com.miking.notas.finance.FinanceCategory
import com.miking.notas.finance.Goal
import com.miking.notas.finance.Soles
import com.miking.notas.finance.SpendingGroup
import com.miking.notas.finance.SpokenEntryParser
import com.miking.notas.finance.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val financeLocale: Locale = Locale.forLanguageTag("es-PE")

internal fun formatDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Hoy"
    today.minusDays(1) -> "Ayer"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d 'de' MMM", financeLocale))
        .replaceFirstChar { it.uppercase() }
}

internal fun formatLongDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", financeLocale))

/* ---------------------------------------------------------------------------------------- */
/* Movimiento                                                                               */
/* ---------------------------------------------------------------------------------------- */

/**
 * Hoja para crear o editar un gasto o ingreso. Con [startWithVoice] abre
 * el dictado nada más mostrarse.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TransactionSheet(
    initial: Transaction?,
    categories: List<FinanceCategory>,
    isRecurring: Boolean,
    onDismiss: () -> Unit,
    onSave: (Transaction, monthly: Boolean) -> Unit,
    onDelete: (Transaction) -> Unit,
    onStopRecurring: (String) -> Unit,
    onNewCategory: (EntryType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by rememberSaveable { mutableStateOf(initial?.type ?: EntryType.EXPENSE) }
    var amountText by rememberSaveable { mutableStateOf(initial?.amountCents?.let(Soles::toInput).orEmpty()) }
    var categoryId by rememberSaveable { mutableStateOf(initial?.categoryId) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var dateText by rememberSaveable { mutableStateOf((initial?.date ?: LocalDate.now()).toString()) }
    var monthly by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var amountError by rememberSaveable { mutableStateOf(false) }
    var heard by rememberSaveable { mutableStateOf<String?>(null) }

    val date = LocalDate.parse(dateText)
    val visibleCategories = categories.filter { it.type == type }
    if (categoryId != null && visibleCategories.none { it.id == categoryId }) {
        categoryId = null
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val text = recognizedSpeech(result.data) ?: return@rememberLauncherForActivityResult
        val parsed = SpokenEntryParser.parse(text, categories)
        heard = text
        type = parsed.type
        parsed.amountCents?.let { amountText = Soles.toInput(it) }
        parsed.categoryId?.let { categoryId = it }
        note = parsed.note
        dateText = parsed.date.toString()
        amountError = false
    }

    val startVoice: () -> Unit = {
        runCatching {
            voiceLauncher.launch(speechRecognitionIntent("Por ejemplo: gasté 25 soles en almuerzo"))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (initial == null) "Nuevo movimiento" else "Editar movimiento",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = startVoice) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = "Dictar movimiento")
                }
                if (initial != null) {
                    IconButton(onClick = { onDelete(initial) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar movimiento")
                    }
                }
            }

            heard?.let {
                Text(
                    text = "Escuché: «$it». Revisa los datos antes de guardar.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = type == EntryType.EXPENSE,
                    onClick = { type = EntryType.EXPENSE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Gasto") }
                SegmentedButton(
                    selected = type == EntryType.INCOME,
                    onClick = { type = EntryType.INCOME },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Ingreso") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    amountError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Importe") },
                prefix = { Text("S/ ") },
                singleLine = true,
                isError = amountError,
                supportingText = if (amountError) {
                    { Text("Escribe un importe mayor que cero") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Categoría",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleCategories.forEach { category ->
                    FilterChip(
                        selected = category.id == categoryId,
                        onClick = { categoryId = category.id },
                        label = { Text(category.name) },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { onNewCategory(type) },
                    label = { Text("Nueva") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(formatDay(date))
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nota (opcional)") },
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (initial?.recurringId != null && isRecurring) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Viene de un pago fijo mensual.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onStopRecurring(initial.recurringId) }) {
                        Text("Dejar de repetir")
                    }
                }
            } else if (initial == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Repetir cada mes", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Alquiler, internet, sueldo… se registrará solo el día ${date.dayOfMonth}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = monthly, onCheckedChange = { monthly = it })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val cents = Soles.parse(amountText)
                    if (cents == null) {
                        amountError = true
                        return@Button
                    }
                    val fallback = if (type == EntryType.INCOME) "inc-other" else "exp-other"
                    val base = initial ?: Transaction(
                        type = type,
                        amountCents = cents,
                        categoryId = fallback,
                        date = date,
                    )
                    onSave(
                        base.copy(
                            type = type,
                            amountCents = cents,
                            categoryId = categoryId ?: fallback,
                            note = note.trim(),
                            date = date,
                        ),
                        monthly,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar")
            }
        }
    }

    if (showDatePicker) {
        FinanceDatePicker(
            initial = date,
            onDismiss = { showDatePicker = false },
            onPicked = {
                dateText = it.toString()
                showDatePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinanceDatePicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
) {
    // El DatePicker trabaja con medianoche UTC.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    ) {
        DatePicker(state = state)
    }
}

/* ---------------------------------------------------------------------------------------- */
/* Categorías                                                                               */
/* ---------------------------------------------------------------------------------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewFinanceCategoryDialog(
    type: EntryType,
    onDismiss: () -> Unit,
    onCreate: (String, EntryType, SpendingGroup) -> Boolean,
) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(SpendingGroup.NEED) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == EntryType.INCOME) "Nueva categoría de ingreso" else "Nueva categoría de gasto") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nombre") },
                    placeholder = { Text(if (type == EntryType.INCOME) "Por ejemplo, Alquiler de cuarto" else "Por ejemplo, Mascotas") },
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                )
                if (type == EntryType.EXPENSE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "¿Qué tipo de gasto es? (para la regla 50/30/20)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpendingGroup.entries.forEach { option ->
                            FilterChip(
                                selected = group == option,
                                onClick = { group = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    if (onCreate(name, type, group)) onDismiss() else error = "Ya existe una categoría con ese nombre"
                },
            ) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

/** Lista de categorías con opción de crear y borrar las propias. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FinanceCategoriesDialog(
    categories: List<FinanceCategory>,
    onDismiss: () -> Unit,
    onNew: (EntryType) -> Unit,
    onChangeGroup: (FinanceCategory) -> Unit,
    onDelete: (FinanceCategory) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<FinanceCategory?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorías de finanzas") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                EntryType.entries.forEach { type ->
                    item(key = "header-$type") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (type == EntryType.EXPENSE) "Gastos" else "Ingresos",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onNew(type) }) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nueva")
                            }
                        }
                    }
                    items(categories.filter { it.type == type }, key = { it.id }) { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(category.name, style = MaterialTheme.typography.bodyLarge)
                                if (type == EntryType.EXPENSE) {
                                    TextButton(
                                        onClick = { onChangeGroup(category.copy(group = category.group.next())) },
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Text(
                                            text = category.group.label + if (category.builtIn) "" else " · propia",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                } else if (!category.builtIn) {
                                    Text(
                                        text = "Propia",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (!category.builtIn) {
                                IconButton(onClick = { pendingDelete = category }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar ${category.name}")
                                }
                            }
                        }
                    }
                    item(key = "divider-$type") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                }
                item(key = "hint") {
                    Text(
                        text = "Toca el tipo de un gasto (Necesidad, Gusto, Ahorro) para cambiarlo. " +
                            "Las categorías de fábrica no se pueden borrar.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Listo") }
        },
    )

    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar «${category.name}»") },
            text = {
                Text(
                    "Sus movimientos pasarán a " +
                        (if (category.type == EntryType.INCOME) "«Otros ingresos»" else "«Otros gastos»") +
                        " y se quitará su presupuesto.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(category)
                        pendingDelete = null
                    },
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            },
        )
    }
}

private fun SpendingGroup.next(): SpendingGroup =
    SpendingGroup.entries[(ordinal + 1) % SpendingGroup.entries.size]

/* ---------------------------------------------------------------------------------------- */
/* Presupuesto                                                                              */
/* ---------------------------------------------------------------------------------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BudgetDialog(
    categories: List<FinanceCategory>,
    budgets: Map<String, Long>,
    initialCategoryId: String?,
    suggestionFor: (String) -> Long?,
    onDismiss: () -> Unit,
    onSave: (categoryId: String, limitCents: Long?) -> Unit,
) {
    val expenseCategories = categories.filter { it.type == EntryType.EXPENSE }
    var categoryId by remember { mutableStateOf(initialCategoryId ?: expenseCategories.firstOrNull()?.id) }
    var amountText by remember {
        mutableStateOf(initialCategoryId?.let { budgets[it] }?.let(Soles::toInput).orEmpty())
    }
    var error by remember { mutableStateOf(false) }
    val suggestion = categoryId?.let(suggestionFor)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Presupuesto mensual") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (initialCategoryId == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        expenseCategories.forEach { category ->
                            FilterChip(
                                selected = category.id == categoryId,
                                onClick = {
                                    categoryId = category.id
                                    amountText = budgets[category.id]?.let(Soles::toInput).orEmpty()
                                },
                                label = { Text(category.name) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Text(
                        text = expenseCategories.firstOrNull { it.id == initialCategoryId }?.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        error = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Límite al mes") },
                    prefix = { Text("S/ ") },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (suggestion != null) {
                    TextButton(onClick = { amountText = Soles.toInput(suggestion) }) {
                        Text("Usar mi gasto medio: ${Soles.format(suggestion)}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = categoryId != null,
                onClick = {
                    val cents = Soles.parse(amountText)
                    if (cents == null) error = true else onSave(categoryId!!, cents)
                },
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (initialCategoryId != null && budgets.containsKey(initialCategoryId)) {
                    TextButton(onClick = { onSave(initialCategoryId, null) }) {
                        Text("Quitar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

/* ---------------------------------------------------------------------------------------- */
/* Metas                                                                                    */
/* ---------------------------------------------------------------------------------------- */

@Composable
internal fun GoalDialog(
    initial: Goal?,
    onDismiss: () -> Unit,
    onSave: (Goal) -> Unit,
    onDelete: (Goal) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var amountText by rememberSaveable { mutableStateOf(initial?.targetCents?.let(Soles::toInput).orEmpty()) }
    var dateText by rememberSaveable { mutableStateOf(initial?.targetDate?.toString()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(false) }
    val targetDate = dateText?.let(LocalDate::parse)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva meta" else "Editar meta") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("¿Para qué ahorras?") },
                    placeholder = { Text("Por ejemplo, Moto") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        error = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cuánto cuesta") },
                    prefix = { Text("S/ ") },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.DateRange, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(targetDate?.let { "Para el ${formatLongDate(it)}" } ?: "Fecha objetivo (opcional)")
                }
                if (targetDate != null) {
                    TextButton(onClick = { dateText = null }) { Text("Sin fecha") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val cents = Soles.parse(amountText)
                    if (cents == null) {
                        error = true
                    } else {
                        val base = initial ?: Goal(name = name.trim(), targetCents = cents)
                        onSave(base.copy(name = name.trim(), targetCents = cents, targetDate = targetDate))
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )

    if (showDatePicker) {
        FinanceDatePicker(
            initial = targetDate ?: LocalDate.now().plusMonths(6),
            onDismiss = { showDatePicker = false },
            onPicked = {
                dateText = it.toString()
                showDatePicker = false
            },
        )
    }
}

/** Añadir o retirar dinero de una meta. */
@Composable
internal fun ContributionDialog(
    goal: Goal,
    suggestedCents: Long?,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var amountText by remember { mutableStateOf(suggestedCents?.takeIf { it > 0 }?.let(Soles::toInput).orEmpty()) }
    var withdraw by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(goal.name) },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !withdraw,
                        onClick = { withdraw = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Añadir") }
                    SegmentedButton(
                        selected = withdraw,
                        onClick = { withdraw = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Retirar") }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        error = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Importe") },
                    prefix = { Text("S/ ") },
                    singleLine = true,
                    isError = error,
                    supportingText = {
                        Text("Ahorrado: ${Soles.format(goal.savedCents)} de ${Soles.format(goal.targetCents)}")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cents = Soles.parse(amountText)
                    when {
                        cents == null -> error = true
                        withdraw && cents > goal.savedCents -> error = true
                        else -> onSave(if (withdraw) -cents else cents)
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
