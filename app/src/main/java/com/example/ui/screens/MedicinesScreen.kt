package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.model.DoseStatus
import com.example.data.model.Medicine
import com.example.notifications.MedicineReminderScheduler
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.MedicationAdherenceRing
import com.example.ui.components.StatusPill
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.CareHubViewModel
import kotlinx.coroutines.launch

@Composable
fun MedicinesScreen(
    viewModel: CareHubViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val medicinesState by viewModel.medicinesState.collectAsStateWithLifecycle()
    val doseLogsState by viewModel.doseLogsState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingScheduleMedicine by remember { mutableStateOf<Medicine?>(null) }

    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
    }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val medicines = (medicinesState as? UiState.Success)?.data.orEmpty()
    val doseLogs = (doseLogsState as? UiState.Success)?.data.orEmpty()
    val globalNotificationsEnabled = (profileState as? UiState.Success)?.data?.notificationsEnabled ?: true

    val todayLogsMap = doseLogs
        .filter { it.dateIso == viewModel.todayIso }
        .associateBy { "${it.medicineId}_${it.scheduledTime}" }

    val dailySlots = medicines.filter { it.active }.flatMap { med ->
        med.scheduleTimes.map { slot ->
            Triple(med, slot, todayLogsMap["${med.id}_$slot"]?.parsedStatus ?: DoseStatus.PENDING)
        }
    }
    val takenCount = dailySlots.count { it.third == DoseStatus.TAKEN }
    val missedCount = dailySlots.count { it.third == DoseStatus.MISSED }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_medicine_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add medicine")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 860.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Medicine Reminders",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        FilledTonalButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    MedicineReminderScheduler.scheduleTestReminder(context.applicationContext, 5)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Test alarm scheduled — notification arrives in 5 seconds.")
                                    }
                                }
                            },
                            modifier = Modifier.testTag("test_alarm_button")
                        ) {
                            Icon(Icons.Rounded.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test 5s Alert")
                        }
                    }
                }

                if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Allow notifications to receive daily medicine reminders.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                ) {
                                    Text("Enable")
                                }
                            }
                        }
                    }
                }

                // Daily Adherence Summary
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            MedicationAdherenceRing(
                                progress = if (dailySlots.isEmpty()) 0f else takenCount.toFloat() / dailySlots.size.toFloat(),
                                takenCount = takenCount,
                                totalCount = dailySlots.size
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Today's Medication Log", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Taken: $takenCount • Missed: $missedCount • Pending: ${dailySlots.size - takenCount - missedCount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (globalNotificationsEnabled) "Daily AlarmManager active • ${viewModel.todayIso}"
                                    else "Global notifications paused in Profile • ${viewModel.todayIso}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                when (val state = medicinesState) {
                    is UiState.Loading -> item { LoadingStateBox() }
                    is UiState.Error -> item { ErrorStateCard(message = state.message) }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Rounded.Medication,
                                    title = "No medications added yet",
                                    subtitle = "Add your medicines with dosage instructions and custom reminder times for daily tracking.",
                                    actionLabel = "Add Medicine",
                                    onActionClick = { showAddDialog = true }
                                )
                            }
                        } else {
                            item {
                                Text(
                                    "Daily Schedule",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            items(dailySlots, key = { "${it.first.id}_${it.second}" }) { (med, slot, status) ->
                                DailyDoseCard(
                                    medicine = med,
                                    timeSlot = slot,
                                    status = status,
                                    onMarkStatus = { newStatus ->
                                        viewModel.markDose(med, slot, newStatus)
                                    }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "My Prescriptions (${state.data.size})",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            items(state.data, key = { it.id }) { med ->
                                MedicineConfigCard(
                                    medicine = med,
                                    globalNotificationsEnabled = globalNotificationsEnabled,
                                    onEditTimes = { editingScheduleMedicine = med },
                                    onToggleReminder = { enabled ->
                                        viewModel.toggleMedicineReminder(med.id, enabled)
                                    },
                                    onDelete = {
                                        MedicineReminderScheduler.cancelMedicineReminders(context.applicationContext, med)
                                        viewModel.deleteMedicine(med)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMedicineDialog(
            isSaving = isSaving,
            onDismiss = { showAddDialog = false },
            onSave = { name, dosage, instructions, times, reminders ->
                viewModel.addMedicine(name, dosage, instructions, times, reminders) {
                    showAddDialog = false
                }
            }
        )
    }

    val editTarget = editingScheduleMedicine
    if (editTarget != null) {
        EditMedicineScheduleDialog(
            medicine = editTarget,
            isSaving = isSaving,
            onDismiss = { editingScheduleMedicine = null },
            onSave = { updatedTimes ->
                viewModel.updateMedicineScheduleTimes(editTarget.id, updatedTimes) {
                    editingScheduleMedicine = null
                }
            }
        )
    }
}

@Composable
private fun DailyDoseCard(
    medicine: Medicine,
    timeSlot: String,
    status: DoseStatus,
    onMarkStatus: (DoseStatus) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${medicine.name} • ${medicine.dosage}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "$timeSlot — ${medicine.instructions.ifBlank { "Take as directed" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (status) {
                    DoseStatus.TAKEN -> StatusPill(
                        text = "Taken",
                        containerColor = SuccessContainer,
                        contentColor = SuccessEmerald,
                        icon = Icons.Rounded.CheckCircle
                    )
                    DoseStatus.MISSED -> StatusPill(
                        text = "Missed",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                    DoseStatus.PENDING -> StatusPill(
                        text = "Pending",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onMarkStatus(DoseStatus.TAKEN) },
                    modifier = Modifier.weight(1f),
                    enabled = status != DoseStatus.TAKEN
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mark Taken")
                }
                OutlinedButton(
                    onClick = { onMarkStatus(DoseStatus.MISSED) },
                    modifier = Modifier.weight(1f),
                    enabled = status != DoseStatus.MISSED
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Missed")
                }
            }
        }
    }
}

@Composable
private fun MedicineConfigCard(
    medicine: Medicine,
    globalNotificationsEnabled: Boolean,
    onEditTimes: () -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val nextAlertText = remember(medicine.scheduleTimes, medicine.remindersEnabled, globalNotificationsEnabled) {
        if (!globalNotificationsEnabled) "Global reminders paused in Profile"
        else if (!medicine.remindersEnabled) "Reminders paused"
        else "Next alert: ${MedicineReminderScheduler.formatNextTriggerSummary(medicine.scheduleTimes)}"
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${medicine.name} (${medicine.dosage})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Times: ${medicine.scheduleTimes.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = nextAlertText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (medicine.remindersEnabled && globalNotificationsEnabled)
                        MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (medicine.instructions.isNotBlank()) {
                    Text(
                        text = medicine.instructions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onEditTimes,
                modifier = Modifier.testTag("edit_times_${medicine.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.EditCalendar,
                    contentDescription = "Edit reminder times for ${medicine.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Switch(
                checked = medicine.remindersEnabled,
                onCheckedChange = onToggleReminder
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete ${medicine.name}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
