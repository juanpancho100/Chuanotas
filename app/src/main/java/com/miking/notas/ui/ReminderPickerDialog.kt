package com.miking.notas.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.miking.notas.data.ReminderRepeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Ventana de calendario, hora y repetición. Se usa al añadir o cambiar el
 * recordatorio de una nota. Por defecto elige «Una vez».
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReminderPickerDialog(
    initialAt: Long?,
    initialRepeatDays: Set<Int>,
    onDismiss: () -> Unit,
    onSave: (Long, Set<Int>) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val now = System.currentTimeMillis()
    val initialRepeat = remember(initialRepeatDays) {
        ReminderRepeat.sanitize(initialRepeatDays)
    }
    val initialMoment = remember(initialAt, initialRepeatDays) {
        val anchor = initialAt ?: (now + 60 * 60 * 1000L)
        val next = if (initialRepeat.isEmpty()) {
            anchor
        } else {
            ReminderRepeat.nextOccurrence(anchor, initialRepeat, now) ?: anchor
        }
        val nextZoned = Instant.ofEpochMilli(next).atZone(zone)

        when {
            next > now -> nextZoned
            initialRepeat.isEmpty() -> Instant.ofEpochMilli(now + 60 * 60 * 1000L).atZone(zone)
            else -> Instant.ofEpochMilli(now).atZone(zone)
        }
    }

    val initialDateUtc = remember(initialMoment) {
        initialMoment.toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateUtc,
    )

    var hour by rememberSaveable { mutableIntStateOf(initialMoment.hour) }
    var minute by rememberSaveable { mutableIntStateOf(initialMoment.minute) }
    var repeatDays by remember { mutableStateOf(initialRepeat) }
    var error by remember { mutableStateOf<String?>(null) }

    val selectedDate: LocalDate = datePickerState.selectedDateMillis?.let { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    } ?: initialMoment.toLocalDate()

    val selectedAt = selectedDate
        .atTime(hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    LaunchedEffect(selectedAt, repeatDays) {
        error = null
    }

    val save: () -> Unit = {
        val days = ReminderRepeat.sanitize(repeatDays)
        if (days.isEmpty() && selectedAt <= System.currentTimeMillis()) {
            error = "Elige una hora futura para el recordatorio."
        } else {
            error = null
            onSave(selectedAt, days)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                    Text(
                        text = "Recordatorio",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = save) {
                        Text("Guardar")
                    }
                }

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                ) {
                    Text(
                        text = "Elige el día",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    DatePicker(
                        state = datePickerState,
                        showModeToggle = false,
                        title = null,
                        headline = null,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Hora",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, selectedHour, selectedMinute ->
                                    hour = selectedHour
                                    minute = selectedMinute
                                },
                                hour,
                                minute,
                                AndroidDateFormat.is24HourFormat(context),
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Hora: ${formatPickerTime(hour, minute)}")
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Text(
                        text = "Repetir",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Una vez por defecto. Puedes elegir los días que quieras.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = repeatDays.isEmpty(),
                            onClick = {
                                repeatDays = emptySet()
                                error = null
                            },
                            label = { Text("Una vez") },
                        )
                        FilterChip(
                            selected = repeatDays == ReminderRepeat.ALL_DAYS,
                            onClick = {
                                repeatDays = ReminderRepeat.ALL_DAYS
                                error = null
                            },
                            label = { Text("Todos los días") },
                        )
                        FilterChip(
                            selected = repeatDays == ReminderRepeat.WEEKDAYS,
                            onClick = {
                                repeatDays = ReminderRepeat.WEEKDAYS
                                error = null
                            },
                            label = { Text("Lun a vie") },
                        )
                        FilterChip(
                            selected = repeatDays == ReminderRepeat.WEEKEND,
                            onClick = {
                                repeatDays = ReminderRepeat.WEEKEND
                                error = null
                            },
                            label = { Text("Fin de semana") },
                        )
                        FilterChip(
                            selected = repeatDays.isNotEmpty() &&
                                repeatDays != ReminderRepeat.ALL_DAYS &&
                                repeatDays != ReminderRepeat.WEEKDAYS &&
                                repeatDays != ReminderRepeat.WEEKEND,
                            onClick = {
                                if (repeatDays.isEmpty()) {
                                    repeatDays = setOf(selectedDate.dayOfWeek.value)
                                }
                                error = null
                            },
                            label = { Text("Personalizado") },
                        )
                    }

                    if (repeatDays.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Días",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (day in 1..7) {
                                FilterChip(
                                    selected = day in repeatDays,
                                    onClick = {
                                        repeatDays = if (day in repeatDays) {
                                            repeatDays - day
                                        } else {
                                            repeatDays + day
                                        }
                                        error = null
                                    },
                                    label = { Text(dayShortLabel(day)) },
                                    modifier = Modifier.semantics {
                                        contentDescription = dayName(day)
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    val summary = if (repeatDays.isEmpty()) {
                        "Sonará una vez: ${formatPickerDate(selectedDate)} a las " +
                            formatPickerTime(hour, minute) + "."
                    } else {
                        "Sonará ${repeatDaysLabel(repeatDays).lowercase(Locale.forLanguageTag("es-ES"))} a las " +
                            formatPickerTime(hour, minute) + "."
                    }
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun formatPickerTime(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()),
    )

private fun formatPickerDate(date: LocalDate): String =
    date.format(
        DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-ES")),
    )
