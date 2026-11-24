package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.BookedSlot
import com.example.data.model.ConsultationType
import com.example.data.model.Doctor
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.DoctorsAndAppointmentsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AppointmentsScreen(
    patientName: String,
    viewModel: DoctorsAndAppointmentsViewModel,
    onJoinVideoCall: (String) -> Unit
) {
    val patientApptsState by viewModel.patientAppointmentsState.collectAsStateWithLifecycle()
    val doctorApptsState by viewModel.doctorAppointmentsState.collectAsStateWithLifecycle()
    val doctorsState by viewModel.doctorsState.collectAsStateWithLifecycle()
    val bookedSlots by viewModel.bookedSlotsForSelectedDoctor.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showBookDialog by remember { mutableStateOf(false) }
    var rescheduleTarget by remember { mutableStateOf<Appointment?>(null) }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val doctors = (doctorsState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(showBookDialog, doctors) {
        if (showBookDialog && doctors.isNotEmpty()) {
            viewModel.selectDoctorForBookedSlots(doctors.first().id)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (doctors.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showBookDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("book_appointment_fab")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Book appointment")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 860.dp)
                    .fillMaxSize()
            ) {
                Text(
                    text = "Appointments & Telemedicine",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                val tabs = listOf("Upcoming", "Past", "Doctor Queue")
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(title) }
                        )
                    }
                }

                val activeState = if (selectedTab == 2) doctorApptsState else patientApptsState

                when (activeState) {
                    is UiState.Loading -> LoadingStateBox()
                    is UiState.Error -> Box(modifier = Modifier.padding(16.dp)) {
                        ErrorStateCard(message = activeState.message)
                    }
                    is UiState.Success -> {
                        val list = when (selectedTab) {
                            0 -> activeState.data.filter {
                                it.parsedStatus == AppointmentStatus.UPCOMING || it.parsedStatus == AppointmentStatus.IN_CALL
                            }
                            1 -> activeState.data.filter {
                                it.parsedStatus == AppointmentStatus.COMPLETED || it.parsedStatus == AppointmentStatus.CANCELLED
                            }
                            else -> activeState.data.filter {
                                it.parsedStatus != AppointmentStatus.CANCELLED
                            }
                        }

                        if (list.isEmpty()) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                EmptyStateCard(
                                    icon = Icons.Rounded.CalendarMonth,
                                    title = when (selectedTab) {
                                        0 -> "No upcoming appointments"
                                        1 -> "No past appointments"
                                        else -> "No incoming patient bookings"
                                    },
                                    subtitle = if (selectedTab == 2) {
                                        "Virtual consultations booked with your Doctor Portal profile will appear here."
                                    } else {
                                        "Tap + to book an in-person visit or virtual video consultation."
                                    },
                                    actionLabel = if (selectedTab == 0 && doctors.isNotEmpty()) "Book Now" else null,
                                    onActionClick = if (selectedTab == 0 && doctors.isNotEmpty()) {
                                        { showBookDialog = true }
                                    } else null
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(list, key = { it.id }) { appt ->
                                    AppointmentItemCard(
                                        appointment = appt,
                                        isDoctorQueue = selectedTab == 2,
                                        onJoinVideoCall = onJoinVideoCall,
                                        onReschedule = {
                                            viewModel.selectDoctorForBookedSlots(appt.doctorId)
                                            rescheduleTarget = appt
                                        },
                                        onCancel = { viewModel.cancelAppointment(appt) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBookDialog && doctors.isNotEmpty()) {
        BookAppointmentDialog(
            doctors = doctors,
            initialDoctor = doctors.first(),
            initialType = ConsultationType.VIRTUAL,
            bookedSlots = bookedSlots,
            isSubmitting = isSubmitting,
            onDoctorChanged = { viewModel.selectDoctorForBookedSlots(it.id) },
            onDismiss = { showBookDialog = false },
            onConfirmBooking = { doc, type, dateIso, timeSlot, reason ->
                viewModel.bookAppointment(
                    patientName = patientName,
                    doctor = doc,
                    consultationType = type,
                    dateIso = dateIso,
                    timeSlot = timeSlot,
                    reason = reason,
                    onBooked = { showBookDialog = false }
                )
            }
        )
    }

    val target = rescheduleTarget
    if (target != null) {
        val doc = doctors.firstOrNull { it.id == target.doctorId }
        if (doc != null) {
            RescheduleAppointmentDialog(
                appointment = target,
                doctor = doc,
                bookedSlots = bookedSlots,
                isSubmitting = isSubmitting,
                onDismiss = { rescheduleTarget = null },
                onConfirm = { newDate, newSlot ->
                    viewModel.rescheduleAppointment(target, newDate, newSlot) {
                        rescheduleTarget = null
                    }
                }
            )
        }
    }
}

@Composable
private fun AppointmentItemCard(
    appointment: Appointment,
    isDoctorQueue: Boolean,
    onJoinVideoCall: (String) -> Unit,
    onReschedule: () -> Unit,
    onCancel: () -> Unit
) {
    val isVirtual = appointment.parsedType == ConsultationType.VIRTUAL
    val isActive = appointment.parsedStatus == AppointmentStatus.UPCOMING ||
        appointment.parsedStatus == AppointmentStatus.IN_CALL

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDoctorQueue) "Patient: ${appointment.patientName}" else appointment.doctorName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${appointment.department} • ${appointment.hospitalName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val (pillBg, pillFg) = when (appointment.parsedStatus) {
                    AppointmentStatus.COMPLETED -> SuccessContainer to SuccessEmerald
                    AppointmentStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
                    else -> if (isVirtual) {
                        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
                    }
                }
                StatusPill(
                    text = if (isActive) appointment.parsedType.label else appointment.parsedStatus.label,
                    containerColor = pillBg,
                    contentColor = pillFg,
                    icon = if (isVirtual) Icons.Rounded.Videocam else Icons.Rounded.LocalHospital
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${appointment.dateIso} • ${appointment.timeSlot}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (appointment.reason.isNotBlank()) {
                Text(
                    text = "Reason: ${appointment.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!appointment.notes.isNullOrBlank()) {
                Text(
                    text = "Visit Notes: ${appointment.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isActive) {
                if (isVirtual && !appointment.roomId.isNullOrBlank()) {
                    Button(
                        onClick = { onJoinVideoCall(appointment.roomId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_video_call_${appointment.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isDoctorQueue) "Start Telemedicine Call" else "Join Secure Video Call")
                    }
                }
                if (!isDoctorQueue) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReschedule,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Rounded.EventRepeat,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reschedule")
                        }
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookAppointmentDialog(
    doctors: List<Doctor>,
    initialDoctor: Doctor,
    initialType: ConsultationType,
    bookedSlots: List<BookedSlot>,
    isSubmitting: Boolean,
    onDoctorChanged: (Doctor) -> Unit,
    onDismiss: () -> Unit,
    onConfirmBooking: (Doctor, ConsultationType, String, String, String) -> Unit
) {
    var selectedDoctor by remember { mutableStateOf(initialDoctor) }
    var selectedType by remember {
        mutableStateOf(
            if (initialType == ConsultationType.VIRTUAL && initialDoctor.telemedicineEnabled && initialDoctor.virtualSlots.isNotEmpty()) {
                ConsultationType.VIRTUAL
            } else {
                ConsultationType.IN_PERSON
            }
        )
    }

    val upcomingDates = remember { generateUpcomingDates(7) }
    var selectedDate by remember { mutableStateOf(upcomingDates.first().first) }

    val candidateSlots = if (selectedType == ConsultationType.VIRTUAL) {
        selectedDoctor.virtualSlots
    } else {
        selectedDoctor.clinicSlots.ifEmpty { listOf("10:00 AM", "02:00 PM") }
    }
    val takenForDate = bookedSlots
        .filter { it.doctorId == selectedDoctor.id && it.dateIso == selectedDate }
        .map { it.timeSlot }
        .toSet()

    val availableSlots = candidateSlots.filter { it !in takenForDate }
    var selectedSlot by remember(selectedDoctor.id, selectedType, selectedDate, availableSlots) {
        mutableStateOf(availableSlots.firstOrNull().orEmpty())
    }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Book Appointment") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (doctors.size > 1) {
                    Text("Select Doctor", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        doctors.forEach { doc ->
                            FilterChip(
                                selected = selectedDoctor.id == doc.id,
                                onClick = {
                                    selectedDoctor = doc
                                    onDoctorChanged(doc)
                                    if (selectedType == ConsultationType.VIRTUAL && (!doc.telemedicineEnabled || doc.virtualSlots.isEmpty())) {
                                        selectedType = ConsultationType.IN_PERSON
                                    }
                                },
                                label = { Text("${doc.name} (${doc.specialization})") }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "${selectedDoctor.name} • ${selectedDoctor.specialization}",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Text("Consultation Mode", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val virtualAllowed = selectedDoctor.telemedicineEnabled && selectedDoctor.virtualSlots.isNotEmpty()
                    SegmentedButton(
                        selected = selectedType == ConsultationType.VIRTUAL,
                        onClick = { if (virtualAllowed) selectedType = ConsultationType.VIRTUAL },
                        enabled = virtualAllowed,
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) {
                        Text("Virtual ($${selectedDoctor.virtualFee})")
                    }
                    SegmentedButton(
                        selected = selectedType == ConsultationType.IN_PERSON,
                        onClick = { selectedType = ConsultationType.IN_PERSON },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) {
                        Text("Clinic ($${selectedDoctor.inPersonFee})")
                    }
                }

                Text("Select Date", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    upcomingDates.forEach { (iso, label) ->
                        FilterChip(
                            selected = selectedDate == iso,
                            onClick = { selectedDate = iso },
                            label = { Text(label) }
                        )
                    }
                }

                Text("Available Time Slots", style = MaterialTheme.typography.labelLarge)
                if (candidateSlots.isEmpty()) {
                    Text(
                        "No slots configured for this consultation type.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        candidateSlots.forEach { slot ->
                            val isTaken = slot in takenForDate
                            FilterChip(
                                selected = selectedSlot == slot && !isTaken,
                                enabled = !isTaken,
                                onClick = { selectedSlot = slot },
                                label = { Text(if (isTaken) "$slot (Booked)" else slot) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for visit / symptoms") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmBooking(selectedDoctor, selectedType, selectedDate, selectedSlot, reason)
                },
                enabled = !isSubmitting && selectedSlot.isNotBlank(),
                modifier = Modifier.testTag("confirm_booking_button")
            ) {
                Text(if (isSubmitting) "Booking..." else "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RescheduleAppointmentDialog(
    appointment: Appointment,
    doctor: Doctor,
    bookedSlots: List<BookedSlot>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val dates = remember { generateUpcomingDates(7) }
    var newDate by remember { mutableStateOf(appointment.dateIso.ifBlank { dates.first().first }) }
    val slots = if (appointment.parsedType == ConsultationType.VIRTUAL) {
        doctor.virtualSlots.ifEmpty { listOf("09:00 AM", "10:30 AM", "03:00 PM") }
    } else {
        doctor.clinicSlots.ifEmpty { listOf("10:00 AM", "02:00 PM") }
    }
    val taken = bookedSlots
        .filter { it.doctorId == doctor.id && it.dateIso == newDate }
        .map { it.timeSlot }
        .toSet()
    var newSlot by remember(newDate) {
        mutableStateOf(slots.firstOrNull { it !in taken }.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule Visit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pick New Date", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dates.forEach { (iso, label) ->
                        FilterChip(
                            selected = newDate == iso,
                            onClick = { newDate = iso },
                            label = { Text(label) }
                        )
                    }
                }
                Text("Pick New Slot", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    slots.forEach { slot ->
                        val isTaken = slot in taken
                        FilterChip(
                            selected = newSlot == slot && !isTaken,
                            enabled = !isTaken,
                            onClick = { newSlot = slot },
                            label = { Text(slot) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newDate, newSlot) },
                enabled = !isSubmitting && newSlot.isNotBlank()
            ) {
                Text(if (isSubmitting) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun generateUpcomingDates(days: Int): List<Pair<String, String>> {
    val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val labelFmt = SimpleDateFormat("EEE, MMM d", Locale.US)
    val cal = Calendar.getInstance()
    return (0 until days).map { offset ->
        val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
        val iso = isoFmt.format(c.time)
        val label = if (offset == 0) "Today" else labelFmt.format(c.time)
        iso to label
    }
}
