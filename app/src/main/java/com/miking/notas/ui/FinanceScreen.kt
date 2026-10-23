package com.miking.notas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miking.notas.finance.BudgetLevel
import com.miking.notas.finance.BudgetStatus
import com.miking.notas.finance.EntryType
import com.miking.notas.finance.FinanceData
import com.miking.notas.finance.FinanceStats
import com.miking.notas.finance.Goal
import com.miking.notas.finance.GoalPlan
import com.miking.notas.finance.OverallBudget
import com.miking.notas.finance.ProjectionMethod
import com.miking.notas.finance.Soles
import com.miking.notas.finance.SpendingGroup
import com.miking.notas.finance.Transaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class FinanceTab(val label: String) {
    SUMMARY("Resumen"),
    MOVEMENTS("Movimientos"),
    BUDGETS("Presupuestos"),
    GOALS("Metas"),
}

@Composable
internal fun FinanceScreen(
    vm: FinanceViewModel,
    aiEnabled: Boolean,
    aiApiKey: String,
    aiModel: String,
    onOpenSettings: () -> Unit,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val advice by vm.advice.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(FinanceTab.SUMMARY) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var showNewTransaction by rememberSaveable { mutableStateOf(false) }
    var newCategoryType by rememberSaveable { mutableStateOf<EntryType?>(null) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var budgetDialog by remember { mutableStateOf<BudgetDialogRequest?>(null) }
    var goalDialog by remember { mutableStateOf<Goal?>(null) }
    var showNewGoal by rememberSaveable { mutableStateOf(false) }
    var showOverallDialog by rememberSaveable { mutableStateOf(false) }
    var contributionGoal by remember { mutableStateOf<GoalPlan?>(null) }
    val today = LocalDate.now()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.applyRecurring()
    }

    LaunchedEffect(vm) {
        vm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val deleteWithUndo: (Transaction) -> Unit = { transaction ->
        vm.deleteTransaction(transaction)
        editing = null
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Movimiento eliminado",
                actionLabel = "Deshacer",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) vm.restoreTransaction(transaction)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FinanceHeader(
                month = month,
                tab = tab,
                onTab = { tab = it },
                onPrevious = vm::previousMonth,
                onNext = vm::nextMonth,
                onToday = vm::currentMonth,
                onCategories = { showCategories = true },
            )
        },
        bottomBar = {
            MainNavigationBar(selected = selectedTab, onSelected = onTabSelected)
        },
        floatingActionButton = {
            when (tab) {
                FinanceTab.SUMMARY, FinanceTab.MOVEMENTS -> ExtendedFloatingActionButton(
                    onClick = { showNewTransaction = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Movimiento") },
                )

                FinanceTab.BUDGETS -> ExtendedFloatingActionButton(
                    onClick = { budgetDialog = BudgetDialogRequest(null) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Presupuesto") },
                )

                FinanceTab.GOALS -> ExtendedFloatingActionButton(
                    onClick = { showNewGoal = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Meta") },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                FinanceTab.SUMMARY -> summaryTab(
                    data = data,
                    month = month,
                    today = today,
                    advice = advice,
                    aiEnabled = aiEnabled,
                    onAdvice = { vm.requestAdvice(aiApiKey, aiModel) },
                    onOpenSettings = onOpenSettings,
                    onOpenTransaction = { editing = it },
                )

                FinanceTab.MOVEMENTS -> movementsTab(
                    data = data,
                    month = month,
                    onOpen = { editing = it },
                )

                FinanceTab.BUDGETS -> budgetsTab(
                    data = data,
                    month = month,
                    today = today,
                    onEdit = { budgetDialog = BudgetDialogRequest(it) },
                    onEditOverall = { showOverallDialog = true },
                )

                FinanceTab.GOALS -> goalsTab(
                    data = data,
                    today = today,
                    onEdit = { goalDialog = it },
                    onContribute = { contributionGoal = it },
                )
            }
        }
    }

    if (showNewTransaction || editing != null) {
        val current = editing
        TransactionSheet(
            initial = current,
            categories = data.categories,
            isRecurring = data.recurrings.any { it.id == current?.recurringId },
            onDismiss = {
                showNewTransaction = false
                editing = null
            },
            onSave = { transaction, monthly ->
                vm.saveTransaction(transaction, monthly)
                showNewTransaction = false
                editing = null
            },
            onDelete = deleteWithUndo,
            onStopRecurring = {
                vm.stopRecurring(it)
                editing = null
            },
            onNewCategory = { newCategoryType = it },
        )
    }

    newCategoryType?.let { type ->
        NewFinanceCategoryDialog(
            type = type,
            onDismiss = { newCategoryType = null },
            onCreate = vm::addCategory,
        )
    }

    if (showCategories) {
        FinanceCategoriesDialog(
            categories = data.categories,
            onDismiss = { showCategories = false },
            onNew = { newCategoryType = it },
            onChangeGroup = vm::updateCategory,
            onDelete = vm::deleteCategory,
        )
    }

    budgetDialog?.let { request ->
        BudgetDialog(
            categories = data.categories,
            budgets = data.budgets,
            initialCategoryId = request.categoryId,
            suggestionFor = { id -> averageCategorySpend(data, id, month) },
            onDismiss = { budgetDialog = null },
            onSave = { id, cents ->
                vm.setBudget(id, cents)
                budgetDialog = null
            },
        )
    }

    if (showOverallDialog) {
        OverallBudgetDialog(
            currentCents = data.monthlyLimitCents,
            suggestedCents = FinanceStats.movingAverageExpense(data.transactions, month)
                ?.let { (it + 99) / 100 * 100 },
            onDismiss = { showOverallDialog = false },
            onSave = {
                vm.setMonthlyLimit(it)
                showOverallDialog = false
            },
        )
    }

    if (showNewGoal || goalDialog != null) {
        GoalDialog(
            initial = goalDialog,
            onDismiss = {
                showNewGoal = false
                goalDialog = null
            },
            onSave = {
                vm.saveGoal(it)
                showNewGoal = false
                goalDialog = null
            },
            onDelete = {
                vm.deleteGoal(it)
                goalDialog = null
            },
        )
    }

    contributionGoal?.let { plan ->
        ContributionDialog(
            goal = plan.goal,
            suggestedCents = plan.requiredMonthlyCents,
            onDismiss = { contributionGoal = null },
            onSave = {
                vm.contribute(plan.goal, it)
                contributionGoal = null
            },
        )
    }
}

private data class BudgetDialogRequest(val categoryId: String?)

/** Gasto medio mensual de una categoría en los 3 meses anteriores, para sugerir un presupuesto. */
private fun averageCategorySpend(data: FinanceData, categoryId: String, month: YearMonth): Long? {
    val totals = (1..3).map { offset ->
        val target = month.minusMonths(offset.toLong())
        data.transactions
            .filter { it.type == EntryType.EXPENSE && it.categoryId == categoryId && it.month == target }
            .sumOf { it.amountCents }
    }.filter { it > 0 }
    if (totals.isEmpty()) return null
    // Redondeado a soles enteros: un presupuesto con céntimos no aporta nada.
    return (totals.sum() / totals.size + 99) / 100 * 100
}

/* ---------------------------------------------------------------------------------------- */
/* Cabecera                                                                                 */
/* ---------------------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceHeader(
    month: YearMonth,
    tab: FinanceTab,
    onTab: (FinanceTab) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onCategories: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Finanzas",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Opciones de finanzas",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Categorías") },
                        onClick = {
                            menuOpen = false
                            onCategories()
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
            }
            Text(
                text = monthTitle(month),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
            }
        }
        if (month != YearMonth.now()) {
            TextButton(
                onClick = onToday,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Volver al mes actual")
            }
        }

        PrimaryTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            FinanceTab.entries.forEach { option ->
                Tab(
                    selected = tab == option,
                    onClick = { onTab(option) },
                    text = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

internal fun monthTitle(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", financeLocale))
        .replaceFirstChar { it.uppercase() }

/* ---------------------------------------------------------------------------------------- */
/* Resumen                                                                                  */
/* ---------------------------------------------------------------------------------------- */

private fun LazyListScope.summaryTab(
    data: FinanceData,
    month: YearMonth,
    today: LocalDate,
    advice: FinanceAdviceState,
    aiEnabled: Boolean,
    onAdvice: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTransaction: (Transaction) -> Unit,
) {
    val summary = FinanceStats.summary(data.transactions, month)
    val hasData = summary.incomeCents > 0 || summary.expenseCents > 0

    item(key = "balance") {
        val colors = financeColors()
        FinanceCard {
            Text("Balance del mes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = Soles.format(summary.balanceCents),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                StatColumn(
                    label = "Ingresos",
                    value = Soles.format(summary.incomeCents),
                    swatch = colors.income,
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    label = "Gastos",
                    value = Soles.format(summary.expenseCents),
                    swatch = colors.expense,
                    modifier = Modifier.weight(1f),
                )
            }
            summary.savingsRate?.let { rate ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Tasa de ahorro: ${percent(rate)}" + when {
                        rate >= 0.2 -> " · ¡bien! (meta: 20 %)"
                        rate >= 0 -> " · la meta sana es 20 %"
                        else -> " · gastas más de lo que entra"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rate >= 0.2) colors.goodText else if (rate < 0) colors.badText else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (!hasData) {
        item(key = "empty") {
            EmptyFinance(
                title = "Sin movimientos en ${monthTitle(month).lowercase()}",
                body = "Toca «Movimiento» para registrar un gasto o ingreso. También puedes dictarlo: " +
                    "«gasté 25 soles en almuerzo».",
            )
        }
    }

    val projection = FinanceStats.projectExpenses(data, month, today)
    val average = FinanceStats.movingAverageExpense(data.transactions, month)
    if (YearMonth.from(today) == month && summary.expenseCents > 0) {
        item(key = "projection") {
            val colors = financeColors()
            var showHelp by rememberSaveable { mutableStateOf(false) }
            val projected = projection.totalCents

            FinanceCard {
                TitleWithHelp(
                    title = "Proyección a fin de mes",
                    helpDescription = "¿Cómo se calcula la proyección?",
                    onHelp = { showHelp = true },
                )
                Text(
                    text = "Si sigues así, gastarás unos ${Soles.format(projected)} este mes.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Es una estimación, no dinero gastado: llevas ${Soles.format(projection.spentCents)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = when (projection.method) {
                        ProjectionMethod.PATTERN ->
                            "Según cómo gastaste en tus últimos ${projection.patternMonths} meses."
                        ProjectionMethod.LINEAR -> "Según tu ritmo de gasto de este mes."
                    } + if (projection.fixedPaidCents + projection.fixedPendingCents > 0) {
                        " Los pagos fijos se cuentan una sola vez."
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (average != null && average > 0) {
                    val diff = (projected - average).toDouble() / average
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tu media de los últimos meses es ${Soles.format(average)} " +
                            "(${signedPercent(diff)}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (diff > 0.05) colors.badText else colors.goodText,
                    )
                }
                data.monthlyLimitCents?.let { limit ->
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusLine(
                        level = if (projected > limit) BudgetLevel.WARNING else BudgetLevel.OK,
                        text = if (projected > limit) {
                            "Superarías tu presupuesto general (${Soles.format(limit)}) por ${Soles.format(projected - limit)}."
                        } else {
                            "Dentro de tu presupuesto general de ${Soles.format(limit)}."
                        },
                    )
                }
                if (summary.incomeCents > 0 && projected > summary.incomeCents) {
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusLine(
                        level = BudgetLevel.EXCEEDED,
                        text = "Superarías tus ingresos del mes por ${Soles.format(projected - summary.incomeCents)}.",
                    )
                }
            }

            if (showHelp) {
                ProjectionHelpDialog(projection = projection, onDismiss = { showHelp = false })
            }
        }
    }

    item(key = "history") {
        FinanceCard {
            SectionTitle("Últimos 6 meses")
            MonthlyBarsChart(history = FinanceStats.history(data.transactions, month, 6))
        }
    }

    val totals = FinanceStats.expensesByCategory(data, month)
    if (totals.isNotEmpty()) {
        item(key = "categories") {
            val colors = financeColors()
            val maxCents = totals.first().cents
            FinanceCard {
                SectionTitle("¿En qué se va tu dinero?")
                totals.forEach { total ->
                    AmountBarRow(
                        label = total.category.name,
                        cents = total.cents,
                        fraction = total.cents.toFloat() / maxCents,
                        color = colors.bar,
                        trailing = percent(total.share),
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                val pareto = FinanceStats.pareto(totals)
                if (totals.size >= 3 && pareto.size < totals.size) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "El ${percent(pareto.sumOf { it.share })} de tu gasto está en " +
                            pareto.joinToString { it.category.name } +
                            ". Recortar ahí es lo que más rinde.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    val changes = FinanceStats.monthOverMonth(data, month).filter { it.change != null && it.previousCents > 0 }
    if (changes.isNotEmpty()) {
        item(key = "changes") {
            val colors = financeColors()
            FinanceCard {
                SectionTitle("Frente al mes anterior")
                val ups = changes.filter { (it.change ?: 0.0) > 0.1 }.take(3)
                val downs = changes.filter { (it.change ?: 0.0) < -0.1 }.sortedBy { it.change }.take(3)
                if (ups.isEmpty() && downs.isEmpty()) {
                    Text("Gastas parecido al mes pasado en todas las categorías.", style = MaterialTheme.typography.bodyMedium)
                }
                ups.forEach {
                    ChangeRow(
                        name = it.category.name,
                        detail = "${Soles.format(it.previousCents)} → ${Soles.format(it.currentCents)}",
                        change = signedPercent(it.change!!),
                        color = colors.badText,
                    )
                }
                downs.forEach {
                    ChangeRow(
                        name = it.category.name,
                        detail = "${Soles.format(it.previousCents)} → ${Soles.format(it.currentCents)}",
                        change = signedPercent(it.change!!),
                        color = colors.goodText,
                    )
                }
            }
        }
    }

    FinanceStats.rule503020(data, month)?.let { split ->
        item(key = "rule") {
            val colors = financeColors()
            FinanceCard {
                SectionTitle("Regla 50/30/20")
                Text(
                    text = "Recomendado: 50 % necesidades, 30 % gustos y 20 % ahorro de tus ingresos. " +
                        "La raya marca la meta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                split.forEach { group ->
                    val over = when (group.group) {
                        SpendingGroup.SAVING -> group.shareOfIncome < group.target
                        else -> group.shareOfIncome > group.target
                    }
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Row {
                            Text(
                                text = when (group.group) {
                                    SpendingGroup.NEED -> "Necesidades"
                                    SpendingGroup.WANT -> "Gustos"
                                    SpendingGroup.SAVING -> "Ahorro"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${percent(group.shareOfIncome)} · ${Soles.format(group.cents)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (over) colors.badText else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        MeterBar(
                            fraction = group.shareOfIncome.toFloat(),
                            color = colors.bar,
                            markerAt = group.target.toFloat(),
                        )
                    }
                }
            }
        }
    }

    val anomalies = FinanceStats.anomalies(data, month)
    if (anomalies.isNotEmpty()) {
        item(key = "anomalies") {
            FinanceCard {
                SectionTitle("Gastos fuera de lo normal")
                anomalies.take(5).forEach { anomaly ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTransaction(anomaly.transaction) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = anomaly.transaction.note.ifBlank { anomaly.category.name },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${anomaly.category.name} · lo usual es ${Soles.format(anomaly.usualCents)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = Soles.format(anomaly.transaction.amountCents),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (hasData) {
        item(key = "advice") {
            FinanceCard {
                SectionTitle("Consejos con IA")
                when {
                    !aiEnabled || advice.error?.contains("API key") == true -> {
                        Text(
                            text = "Activa el asistente de DeepSeek en Ajustes para recibir consejos. " +
                                "Solo se envían totales por categoría, nunca tus movimientos ni notas.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onOpenSettings) { Text("Abrir ajustes") }
                    }

                    advice.working -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Analizando tus finanzas…")
                    }

                    else -> {
                        advice.text?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        advice.error?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (advice.text == null) {
                            Text(
                                text = "Analiza este mes y te dice dónde recortar y cuánto. " +
                                    "Solo se envían totales, nunca tus movimientos ni notas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        OutlinedButton(onClick = onAdvice) {
                            Text(if (advice.text == null) "Pedir consejos" else "Volver a analizar")
                        }
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------- */
/* Movimientos                                                                              */
/* ---------------------------------------------------------------------------------------- */

private fun LazyListScope.movementsTab(
    data: FinanceData,
    month: YearMonth,
    onOpen: (Transaction) -> Unit,
) {
    val inMonth = data.transactions
        .filter { it.month == month }
        .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt })

    if (inMonth.isEmpty()) {
        item(key = "empty") {
            EmptyFinance(
                title = "Sin movimientos",
                body = "Registra tus gastos e ingresos de ${monthTitle(month).lowercase()} con el botón «Movimiento».",
            )
        }
        return
    }

    inMonth.groupBy { it.date }.forEach { (date, list) ->
        item(key = "day-$date") {
            val net = list.sumOf { if (it.type == EntryType.INCOME) it.amountCents else -it.amountCents }
            Row(modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
                Text(
                    text = formatDay(date),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Soles.format(net),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(list, key = { it.id }) { transaction ->
            TransactionRow(data, transaction, onClick = { onOpen(transaction) })
        }
    }
}

@Composable
private fun TransactionRow(
    data: FinanceData,
    transaction: Transaction,
    onClick: () -> Unit,
) {
    val colors = financeColors()
    val category = data.category(transaction.categoryId)
    val income = transaction.type == EntryType.INCOME

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendSwatch(if (income) colors.income else colors.expense)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.note.ifBlank { category?.name ?: "Sin categoría" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(category?.name ?: "Sin categoría")
                        if (transaction.recurringId != null) append(" · fijo mensual")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = (if (income) "+" else "-") + Soles.format(transaction.amountCents),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (income) colors.goodText else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/* ---------------------------------------------------------------------------------------- */
/* Presupuestos                                                                             */
/* ---------------------------------------------------------------------------------------- */

private fun LazyListScope.budgetsTab(
    data: FinanceData,
    month: YearMonth,
    today: LocalDate,
    onEdit: (String) -> Unit,
    onEditOverall: () -> Unit,
) {
    val statuses = FinanceStats.budgetStatuses(data, month, today)
    val overall = FinanceStats.overallBudget(data, month, today)

    item(key = "overall") {
        if (overall == null) {
            FinanceCard {
                SectionTitle("Presupuesto general")
                Text(
                    text = "Ponle un tope a todo lo que gastas en el mes, sin importar la categoría. " +
                        "Te diré cuánto puedes gastar al día para no pasarte.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onEditOverall) { Text("Definir presupuesto general") }
            }
        } else {
            OverallBudgetCard(
                overall = overall,
                isCurrentMonth = YearMonth.from(today) == month,
                onEdit = onEditOverall,
            )
        }
    }

    if (statuses.isEmpty()) {
        item(key = "empty") {
            EmptyFinance(
                title = "Sin presupuestos por categoría",
                body = "Además del general, puedes ponerle un límite mensual a categorías concretas " +
                    "(comida, ocio, compras…). Te avisaré al llegar al 80 % y al pasarte.",
            )
        }
        return
    }

    item(key = "categories-title") {
        Text(
            text = "Por categoría",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }

    items(statuses, key = { it.category.id }) { status ->
        BudgetCard(status, isCurrentMonth = YearMonth.from(today) == month, onClick = { onEdit(status.category.id) })
    }
}

@Composable
private fun OverallBudgetCard(
    overall: OverallBudget,
    isCurrentMonth: Boolean,
    onEdit: () -> Unit,
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }

    FinanceCard(onClick = onEdit) {
        TitleWithHelp(
            title = "Presupuesto general",
            helpDescription = "¿Cómo funciona el presupuesto general?",
            onHelp = { showHelp = true },
        )
        Text(
            text = "${Soles.format(overall.spentCents)} de ${Soles.format(overall.limitCents)}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MeterBar(fraction = overall.ratio, color = statusColor(overall.level))
        Spacer(modifier = Modifier.height(8.dp))
        StatusLine(
            level = overall.level,
            text = when (overall.level) {
                BudgetLevel.EXCEEDED -> "Excedido por ${Soles.format(-overall.remainingCents)}"
                BudgetLevel.WARNING ->
                    "Cuidado: llevas el ${(overall.ratio * 100).roundToInt()} %, quedan ${Soles.format(overall.remainingCents)}"
                BudgetLevel.OK -> "Bien: quedan ${Soles.format(overall.remainingCents)}"
            },
        )
        if (isCurrentMonth) {
            overall.dailyAllowanceCents?.let { daily ->
                Text(
                    text = if (daily > 0) {
                        "Puedes gastar unos ${Soles.format(daily)} al día durante ${overall.daysLeft} " +
                            (if (overall.daysLeft == 1) "día" else "días") + " más."
                    } else {
                        "No te queda margen para gastos variables este mes."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (overall.projectedCents > overall.limitCents && overall.level != BudgetLevel.EXCEEDED) {
                Text(
                    text = "A este ritmo llegarás a ${Soles.format(overall.projectedCents)} a fin de mes.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (overall.categoryBudgetsCents > overall.limitCents) {
            Spacer(modifier = Modifier.height(6.dp))
            StatusLine(
                level = BudgetLevel.WARNING,
                text = "Tus presupuestos por categoría suman ${Soles.format(overall.categoryBudgetsCents)}, " +
                    "más que el general.",
            )
        }
    }

    if (showHelp) {
        OverallBudgetHelpDialog(overall = overall, onDismiss = { showHelp = false })
    }
}

@Composable
private fun BudgetCard(
    status: BudgetStatus,
    isCurrentMonth: Boolean,
    onClick: () -> Unit,
) {
    FinanceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = status.category.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${Soles.format(status.spentCents)} / ${Soles.formatShort(status.limitCents)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        MeterBar(fraction = status.ratio, color = statusColor(status.level))
        Spacer(modifier = Modifier.height(8.dp))
        StatusLine(
            level = status.level,
            text = when (status.level) {
                BudgetLevel.EXCEEDED -> "Excedido por ${Soles.format(-status.remainingCents)}"
                BudgetLevel.WARNING -> "Cuidado: llevas el ${(status.ratio * 100).roundToInt()} %, quedan ${Soles.format(status.remainingCents)}"
                BudgetLevel.OK -> "Bien: quedan ${Soles.format(status.remainingCents)}"
            },
        )
        if (isCurrentMonth && status.level != BudgetLevel.EXCEEDED && status.willExceed) {
            Text(
                text = "A este ritmo llegarás a ${Soles.format(status.projectedCents)} a fin de mes.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/* ---------------------------------------------------------------------------------------- */
/* Metas                                                                                    */
/* ---------------------------------------------------------------------------------------- */

private fun LazyListScope.goalsTab(
    data: FinanceData,
    today: LocalDate,
    onEdit: (Goal) -> Unit,
    onContribute: (GoalPlan) -> Unit,
) {
    if (data.goals.isEmpty()) {
        item(key = "empty") {
            EmptyFinance(
                title = "Sin metas todavía",
                body = "Crea una meta (una moto, un viaje, un fondo de emergencia) y te diré cuánto " +
                    "ahorrar al mes, cuándo la alcanzarás a tu ritmo y qué gastos recortar.",
            )
        }
        return
    }

    items(data.goals.sortedBy { it.reached }, key = { it.id }) { goal ->
        GoalCard(
            plan = FinanceStats.goalPlan(goal, data, today),
            onEdit = { onEdit(goal) },
            onContribute = onContribute,
        )
    }
}

@Composable
private fun GoalCard(
    plan: GoalPlan,
    onEdit: () -> Unit,
    onContribute: (GoalPlan) -> Unit,
) {
    val goal = plan.goal
    val colors = financeColors()

    FinanceCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = goal.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = percent(goal.progress.toDouble()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${Soles.format(goal.savedCents)} de ${Soles.format(goal.targetCents)}" +
                (goal.targetDate?.let { " · para el ${formatLongDate(it)}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        MeterBar(fraction = goal.progress, color = if (goal.reached) colors.good else colors.bar)
        Spacer(modifier = Modifier.height(10.dp))

        if (goal.reached) {
            StatusLine(level = BudgetLevel.OK, text = "¡Meta conseguida!")
        } else {
            plan.requiredMonthlyCents?.let { required ->
                Text(
                    text = "Necesitas ahorrar ${Soles.format(required)} al mes " +
                        "durante ${plan.monthsLeft} ${if (plan.monthsLeft == 1) "mes" else "meses"}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = if (plan.averageSavingsCents > 0) {
                    "Ahorras de media ${Soles.format(plan.averageSavingsCents)} al mes."
                } else {
                    "Ahora mismo no te sobra dinero a fin de mes."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            plan.estimatedDate?.let {
                Text(
                    text = "A tu ritmo la tendrías hacia ${monthTitle(YearMonth.from(it)).lowercase()}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            StatusLine(
                level = if (plan.onTrack) BudgetLevel.OK else BudgetLevel.WARNING,
                text = if (plan.onTrack) "Vas bien encaminado" else "Te falta ritmo para llegar a la fecha",
            )

            if (plan.cuts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Para llegar, podrías recortar al mes:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                plan.cuts.forEach { cut ->
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        Text("• ${cut.category.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(Soles.format(cut.monthlyCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                val covered = plan.cuts.sumOf { it.monthlyCents }
                val gap = (plan.requiredMonthlyCents ?: 0L) - plan.averageSavingsCents.coerceAtLeast(0)
                if (covered < gap) {
                    Text(
                        text = "Aun así faltarían ${Soles.format(gap - covered)} al mes: " +
                            "considera mover la fecha o aumentar ingresos.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { onContribute(plan) }) {
            Text("Añadir o retirar dinero")
        }
    }
}

/* ---------------------------------------------------------------------------------------- */
/* Piezas comunes                                                                           */
/* ---------------------------------------------------------------------------------------- */

@Composable
private fun FinanceCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun TitleWithHelp(
    title: String,
    helpDescription: String,
    onHelp: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onHelp) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = helpDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    swatch: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendSwatch(swatch)
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LegendSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

@Composable
private fun ChangeRow(name: String, detail: String, change: String, color: Color) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(change, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Estado con icono y texto: el color nunca va solo. */
@Composable
private fun StatusLine(level: BudgetLevel, text: String) {
    val icon: ImageVector = when (level) {
        BudgetLevel.OK -> Icons.Filled.CheckCircle
        BudgetLevel.WARNING -> Icons.Filled.Info
        BudgetLevel.EXCEEDED -> Icons.Filled.Warning
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = statusColor(level), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun statusColor(level: BudgetLevel): Color {
    val colors = financeColors()
    return when (level) {
        BudgetLevel.OK -> colors.good
        BudgetLevel.WARNING -> colors.warning
        BudgetLevel.EXCEEDED -> colors.critical
    }
}

@Composable
private fun EmptyFinance(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun percent(value: Double) = "${(value * 100).roundToInt()} %"

private fun signedPercent(value: Double): String {
    val rounded = (value * 100).roundToInt()
    return if (rounded > 0) "+$rounded %" else "$rounded %"
}
