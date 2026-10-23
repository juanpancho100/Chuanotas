package com.miking.notas.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miking.notas.data.Note
import com.miking.notas.data.NoteOccurrence
import com.miking.notas.data.isRepeating
import com.miking.notas.data.occurrencesOn
import com.miking.notas.data.reminderAtOn
import com.miking.notas.ui.theme.noteContainerColor
import java.text.DateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    NOTES("Notas", Icons.Filled.Home),
    CALENDAR("Calendario", Icons.Filled.DateRange),
    AGENDA("Hoy", Icons.AutoMirrored.Filled.List),
    FINANCE("Finanzas", WalletIcon),
}

/** Cartera (Material «account_balance_wallet»); el set básico de iconos no la incluye. */
private val WalletIcon: ImageVector = ImageVector.Builder(
    name = "Wallet",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = addPathNodes(
        "M21,18v1c0,1.1 -0.9,2 -2,2L5,21c-1.11,0 -2,-0.9 -2,-2L3,5c0,-1.1 0.89,-2 2,-2h14c1.1,0 2,0.9 2,2v1h-9" +
            "c-1.11,0 -2,0.9 -2,2v8c0,1.1 0.89,2 2,2h9zM12,16h10L22,8L12,8v8zM16,13.5c-0.83,0 -1.5,-0.67 -1.5,-1.5" +
            "s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
    ),
    fill = SolidColor(Color.Black),
).build()

@Composable
internal fun MainNavigationBar(
    selected: MainTab,
    onSelected: (MainTab) -> Unit,
) {
    NavigationBar {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}

@Composable
internal fun CalendarScreen(
    notes: List<Note>,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onOpenNote: (Note) -> Unit,
) {
    val today = LocalDate.now()
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDateKey by rememberSaveable { mutableStateOf(today.toString()) }

    val month = runCatching { YearMonth.parse(monthKey) }.getOrDefault(YearMonth.now())
    val selectedDate = runCatching { LocalDate.parse(selectedDateKey) }.getOrDefault(today)
    val dayOccurrences = remember(notes, selectedDate) {
        notes.mapNotNull { it.occurrencesOn(selectedDate) }.sortedBy { it.at }
    }

    fun changeMonth(newMonth: YearMonth) {
        monthKey = newMonth.toString()
        selectedDateKey = newMonth.atDay(1).toString()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            MainNavigationBar(selected = selectedTab, onSelected = onTabSelected)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Calendario",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Recordatorios por día",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        val current = YearMonth.now()
                        monthKey = current.toString()
                        selectedDateKey = LocalDate.now().toString()
                    },
                ) {
                    Text("Hoy")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { changeMonth(month.minusMonths(1)) }) {
                            Text("‹", fontSize = 28.sp)
                        }
                        Text(
                            text = capitalize(month.format(monthFormatter)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { changeMonth(month.plusMonths(1)) }) {
                            Text("›", fontSize = 28.sp)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("L", "M", "X", "J", "V", "S", "D").forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val firstOffset = month.atDay(1).dayOfWeek.value - 1
                    val daysInMonth = month.lengthOfMonth()
                    val weeks = (firstOffset + daysInMonth + 6) / 7

                    for (week in 0 until weeks) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (column in 0..6) {
                                val dayNumber = week * 7 + column - firstOffset + 1
                                if (dayNumber in 1..daysInMonth) {
                                    val date = month.atDay(dayNumber)
                                    CalendarDayCell(
                                        modifier = Modifier.weight(1f),
                                        date = date,
                                        selected = date == selectedDate,
                                        today = date == today,
                                        hasReminder = notes.any { it.reminderAtOn(date) != null },
                                        onClick = { selectedDateKey = date.toString() },
                                    )
                                } else {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = capitalize(selectedDate.format(dayFormatter)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (dayOccurrences.isEmpty()) {
                    "Sin recordatorios"
                } else if (dayOccurrences.size == 1) {
                    "1 recordatorio"
                } else {
                    "${dayOccurrences.size} recordatorios"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (dayOccurrences.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        text = "No hay recordatorios para este día.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            } else {
                dayOccurrences.forEach { occurrence ->
                    ReminderOccurrenceRow(
                        occurrence = occurrence,
                        onClick = { onOpenNote(occurrence.note) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
internal fun AgendaScreen(
    notes: List<Note>,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onOpenNote: (Note) -> Unit,
) {
    val today = LocalDate.now()
    var selectedDateKey by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDate = runCatching { LocalDate.parse(selectedDateKey) }.getOrDefault(today)
    val occurrences = remember(notes, selectedDate) {
        notes.mapNotNull { it.occurrencesOn(selectedDate) }.sortedBy { it.at }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            MainNavigationBar(selected = selectedTab, onSelected = onTabSelected)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Horarios del día",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Lo que suena hoy y en los próximos días",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { selectedDateKey = LocalDate.now().toString() }) {
                    Text("Hoy")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selectedDateKey = selectedDate.minusDays(1).toString() }) {
                        Text("‹", fontSize = 28.sp)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = dayTitle(selectedDate, today),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = capitalize(selectedDate.format(fullDayFormatter)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    IconButton(onClick = { selectedDateKey = selectedDate.plusDays(1).toString() }) {
                        Text("›", fontSize = 28.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (occurrences.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "No hay recordatorios para este día.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Puedes cambiar de día con las flechas o crear una nota con alarma.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                occurrences.forEach { occurrence ->
                    ReminderOccurrenceRow(
                        occurrence = occurrence,
                        onClick = { onOpenNote(occurrence.note) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier,
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    hasReminder: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = container,
            border = if (today && !selected) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = content,
                )
                if (hasReminder) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .padding(0.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderOccurrenceRow(
    occurrence: NoteOccurrence,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val note = occurrence.note

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = noteContainerColor(note.color),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(76.dp),
            ) {
                Text(
                    text = DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(Date(occurrence.at)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.isRepeating) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (note.category.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = note.category,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (note.preview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = note.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (note.isRepeating) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Se repite: ${repeatDaysShortLabel(note.repeatDays)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private val monthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("es-ES"))

private val dayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-ES"))

private val fullDayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-ES"))

private fun capitalize(value: String): String {
    if (value.isEmpty()) return value
    return value.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale.forLanguageTag("es-ES")) else char.toString()
    }
}

private fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoy"
    today.plusDays(1) -> "Mañana"
    today.minusDays(1) -> "Ayer"
    else -> capitalize(date.format(dayFormatter))
}
