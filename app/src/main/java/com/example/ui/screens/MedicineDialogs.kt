package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Medicine
import com.example.notifications.MedicineReminderScheduler

private val DEFAULT_PRESET_TIMES = listOf(
    "07:00 AM", "08:00 AM", "12:30 PM", "02:00 PM", "06:00 PM", "09:00 PM"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddMedicineDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, List<String>, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("After meals") }
    var remindersEnabled by remember { mutableStateOf(true) }
    var showTimePicker by remember { mutableStateOf(false) }

    val availableTimes = remember {
        mutableStateListOf<String>().apply { addAll(DEFAULT_PRESET_TIMES) }
    }
    val selectedTimes = remember { mutableStateListOf("08:00 AM") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Medicine") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medicine Name (e.g. Metformin)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("medicine_name_input")
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosage (e.g. 500 mg, 1 tablet)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("medicine_dosage_input")
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Instructions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Reminder Times (${selectedTimes.size}/6)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FilledTonalButton(
                        onClick = { showTimePicker = true },
                        enabled = selectedTimes.size < 6,
                        modifier = Modifier.testTag("add_custom_time_button")
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Custom Time")
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableTimes.forEach { slot ->
                        val active = slot in selectedTimes
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (active && selectedTimes.size > 1) {
                                    selectedTimes.remove(slot)
                                } else if (!active && selectedTimes.size < 6) {
                                    selectedTimes.add(slot)
                                }
                            },
                            label = { Text(slot) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Daily Alarm Notifications", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = remindersEnabled, onCheckedChange = { remindersEnabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, dosage, instructions, selectedTimes.toList(), remindersEnabled)
                },
                enabled = !isSaving && name.trim().isNotEmpty() && dosage.trim().isNotEmpty() && selectedTimes.isNotEmpty(),
                modifier = Modifier.testTag("save_medicine_button")
            ) {
                Text(if (isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showTimePicker) {
        ReminderTimePickerDialog(
            onDismiss = { showTimePicker = false },
            onTimePicked = { formattedTime ->
                if (formattedTime !in availableTimes) {
                    availableTimes.add(0, formattedTime)
                }
                if (formattedTime !in selectedTimes && selectedTimes.size < 6) {
                    selectedTimes.add(formattedTime)
                }
                showTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditMedicineScheduleDialog(
    medicine: Medicine,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val availableTimes = remember(medicine.id) {
        mutableStateListOf<String>().apply {
            addAll((medicine.scheduleTimes + DEFAULT_PRESET_TIMES).distinct())
        }
    }
    val selectedTimes = remember(medicine.id) {
        mutableStateListOf<String>().apply {
            addAll(medicine.scheduleTimes.ifEmpty { listOf("08:00 AM") })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder Times • ${medicine.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Choose preset slots or pick custom daily times for your AlarmManager notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = { showTimePicker = true },
                    enabled = selectedTimes.size < 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_custom_time_button")
                ) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Custom Time (${selectedTimes.size}/6)")
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableTimes.forEach { slot ->
                        val active = slot in selectedTimes
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (active && selectedTimes.size > 1) {
                                    selectedTimes.remove(slot)
                                } else if (!active && selectedTimes.size < 6) {
                                    selectedTimes.add(slot)
                                }
                            },
                            label = { Text(slot) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedTimes.toList()) },
                enabled = !isSaving && selectedTimes.isNotEmpty(),
                modifier = Modifier.testTag("save_edited_times_button")
            ) {
                Text(if (isSaving) "Saving..." else "Update Alarms")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showTimePicker) {
        ReminderTimePickerDialog(
            onDismiss = { showTimePicker = false },
            onTimePicked = { formattedTime ->
                if (formattedTime !in availableTimes) {
                    availableTimes.add(0, formattedTime)
                }
                if (formattedTime !in selectedTimes && selectedTimes.size < 6) {
                    selectedTimes.add(formattedTime)
                }
                showTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimePickerDialog(
    onDismiss: () -> Unit,
    onTimePicked: (String) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = 8,
        initialMinute = 30,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick Reminder Time") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val formatted = MedicineReminderScheduler.formatTime12Hour(
                        hourOfDay = timePickerState.hour,
                        minute = timePickerState.minute
                    )
                    onTimePicked(formatted)
                },
                modifier = Modifier.testTag("confirm_time_picker_button")
            ) {
                Text("Add Time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
