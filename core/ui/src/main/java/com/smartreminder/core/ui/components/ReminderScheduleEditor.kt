package com.smartreminder.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartreminder.core.model.ReminderSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Shared date + time + lead-time editor. Used by both the capture confirm screen and the
 * reminder edit screen so the two stay identical and are maintained in one place.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderScheduleEditor(
    date: LocalDate,
    time: LocalTime,
    leadMinutes: Int,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onLeadChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCustomLead by remember { mutableStateOf(false) }

    val dateFmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    val timeFmt = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(date.format(dateFmt), maxLines = 1)
            }
            OutlinedButton(onClick = { showTimePicker = true }) {
                Icon(Icons.Filled.Schedule, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(time.format(timeFmt))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Ring me", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReminderSettings.LEAD_TIME_PRESETS.forEach { preset ->
                FilterChip(
                    selected = leadMinutes == preset,
                    onClick = { onLeadChange(preset) },
                    label = { Text(leadLabel(preset)) },
                )
            }
            val isCustom = leadMinutes !in ReminderSettings.LEAD_TIME_PRESETS
            FilterChip(
                selected = isCustom,
                onClick = { showCustomLead = true },
                label = { Text(if (isCustom) leadLabel(leadMinutes) else "Custom") },
                leadingIcon = if (isCustom) {
                    { Icon(Icons.Filled.Schedule, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(timeState.hour, timeState.minute))
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = timeState) },
        )
    }

    if (showCustomLead) {
        var customText by remember { mutableStateOf(leadMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showCustomLead = false },
            title = { Text("Minutes before") },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    customText.toIntOrNull()?.let { onLeadChange(it.coerceIn(0, 1440)) }
                    showCustomLead = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showCustomLead = false }) { Text("Cancel") } },
        )
    }
}

/** "5 min before", "1 h before" — shared lead-time label. */
fun leadLabel(leadMinutes: Int): String = when {
    leadMinutes <= 0 -> "At time"
    leadMinutes < 60 -> "$leadMinutes min before"
    leadMinutes % 60 == 0 -> "${leadMinutes / 60} h before"
    else -> "${leadMinutes / 60} h ${leadMinutes % 60} min before"
}
