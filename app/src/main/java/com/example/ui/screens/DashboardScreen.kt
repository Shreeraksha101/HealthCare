package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.UiState
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.ConsultationType
import com.example.data.model.DoseLog
import com.example.data.model.DoseStatus
import com.example.data.model.MedicalReport
import com.example.data.model.Medicine
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.MedicationAdherenceRing
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.CareHubViewModel
import com.example.ui.viewmodel.DoctorsAndAppointmentsViewModel

@Composable
fun DashboardScreen(
    careHubViewModel: CareHubViewModel,
    docsAndApptsViewModel: DoctorsAndAppointmentsViewModel,
    onNavigateAppointments: () -> Unit,
    onNavigateMedicines: () -> Unit,
    onNavigateAiAssistant: () -> Unit,
    onNavigateDoctors: () -> Unit,
    onNavigateHospitals: () -> Unit,
    onNavigateReports: () -> Unit,
    onNavigateProfile: () -> Unit,
    onJoinVideoCall: (String) -> Unit
) {
    val profileState by careHubViewModel.profileState.collectAsStateWithLifecycle()
    val medicinesState by careHubViewModel.medicinesState.collectAsStateWithLifecycle()
    val doseLogsState by careHubViewModel.doseLogsState.collectAsStateWithLifecycle()
    val reportsState by careHubViewModel.reportsState.collectAsStateWithLifecycle()
    val apptsState by docsAndApptsViewModel.patientAppointmentsState.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val medicines = (medicinesState as? UiState.Success)?.data.orEmpty()
    val doseLogs = (doseLogsState as? UiState.Success)?.data.orEmpty()
    val reports = (reportsState as? UiState.Success)?.data.orEmpty()
    val appointments = (apptsState as? UiState.Success)?.data.orEmpty()

    val upcomingAppointments = appointments.filter {
        it.parsedStatus == AppointmentStatus.UPCOMING || it.parsedStatus == AppointmentStatus.IN_CALL
    }

    val todayLogsMap = doseLogs
        .filter { it.dateIso == careHubViewModel.todayIso }
        .associateBy { "${it.medicineId}_${it.scheduledTime}" }

    val scheduledDosesToday = medicines.filter { it.active }.flatMap { med ->
        med.scheduleTimes.map { time ->
            val log = todayLogsMap["${med.id}_$time"]
            Triple(med, time, log?.parsedStatus ?: DoseStatus.PENDING)
        }
    }
    val takenCount = scheduledDosesToday.count { it.third == DoseStatus.TAKEN }
    val totalToday = scheduledDosesToday.size
    val adherenceProgress = if (totalToday == 0) 0f else takenCount.toFloat() / totalToday.toFloat()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Greeting & Hero Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Welcome back,",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = profile?.displayName?.ifBlank { "Patient" } ?: "Patient",
                                        style = MaterialTheme.typography.headlineMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = onNavigateProfile)
                                        .testTag("dashboard_profile_avatar"),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    if (!profile?.photoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = profile.photoUrl,
                                            contentDescription = "Open profile",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = (profile?.displayName?.firstOrNull() ?: 'P').uppercase(),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // Health Activity Overview strip
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                MedicationAdherenceRing(
                                    progress = adherenceProgress,
                                    takenCount = takenCount,
                                    totalCount = totalToday
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Today's Health Overview",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${upcomingAppointments.size} upcoming visits • ${reports.size} stored reports",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(
                                            onClick = onNavigateAiAssistant,
                                            modifier = Modifier.testTag("dashboard_ask_ai_button"),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Ask AI Guide")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Access Services
            item {
                SectionHeader(title = "Quick Care Access")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        icon = Icons.Rounded.MedicalServices,
                        label = "Find Doctors",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onNavigateDoctors,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_find_doctors")
                    )
                    QuickActionTile(
                        icon = Icons.Rounded.LocalHospital,
                        label = "Hospitals Map",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = onNavigateHospitals,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_hospitals_map")
                    )
                    QuickActionTile(
                        icon = Icons.Rounded.Description,
                        label = "Reports",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        onClick = onNavigateReports,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_reports")
                    )
                }
            }

            // Upcoming Appointments
            item {
                SectionHeader(
                    title = "Upcoming Appointments",
                    actionLabel = "View All",
                    onActionClick = onNavigateAppointments
                )
                Spacer(modifier = Modifier.height(8.dp))
                when (apptsState) {
                    is UiState.Loading -> LoadingStateBox()
                    is UiState.Error -> ErrorStateCard(message = (apptsState as UiState.Error).message)
                    is UiState.Success -> {
                        if (upcomingAppointments.isEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Rounded.CalendarMonth,
                                title = "No upcoming visits",
                                subtitle = "Book a clinic visit or a virtual telemedicine video call with a specialist.",
                                actionLabel = "Book Appointment",
                                onActionClick = onNavigateDoctors
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                upcomingAppointments.take(2).forEach { appt ->
                                    DashboardAppointmentCard(
                                        appointment = appt,
                                        onJoinVideoCall = { roomId -> onJoinVideoCall(roomId) },
                                        onOpenAppointments = onNavigateAppointments
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Today's Medicine Schedule
            item {
                SectionHeader(
                    title = "Today's Medicine Schedule",
                    actionLabel = "Manage",
                    onActionClick = onNavigateMedicines
                )
                Spacer(modifier = Modifier.height(8.dp))
                when (medicinesState) {
                    is UiState.Loading -> LoadingStateBox()
                    is UiState.Error -> ErrorStateCard(message = (medicinesState as UiState.Error).message)
                    is UiState.Success -> {
                        if (scheduledDosesToday.isEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Rounded.Medication,
                                title = "No medicines scheduled",
                                subtitle = "Add your prescriptions or daily supplements to get timely reminders.",
                                actionLabel = "Add Medicine",
                                onActionClick = onNavigateMedicines
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                scheduledDosesToday.take(4).forEach { (med, time, status) ->
                                    DashboardDoseRow(
                                        medicine = med,
                                        timeSlot = time,
                                        status = status,
                                        onMarkTaken = {
                                            careHubViewModel.markDose(med, time, DoseStatus.TAKEN)
                                        },
                                        onMarkMissed = {
                                            careHubViewModel.markDose(med, time, DoseStatus.MISSED)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent Medical Reports
            item {
                SectionHeader(
                    title = "Recent Medical Reports",
                    actionLabel = "View Vault",
                    onActionClick = onNavigateReports
                )
                Spacer(modifier = Modifier.height(8.dp))
                when (reportsState) {
                    is UiState.Loading -> LoadingStateBox()
                    is UiState.Error -> ErrorStateCard(message = (reportsState as UiState.Error).message)
                    is UiState.Success -> {
                        if (reports.isEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Rounded.Description,
                                title = "Your private report vault is empty",
                                subtitle = "Store lab results, imaging scans, and prescriptions safely on your account.",
                                actionLabel = "Upload Report",
                                onActionClick = onNavigateReports
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                reports.take(3).forEach { report ->
                                    DashboardReportRow(report = report, onClick = onNavigateReports)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardAppointmentCard(
    appointment: Appointment,
    onJoinVideoCall: (String) -> Unit,
    onOpenAppointments: () -> Unit
) {
    val isVirtual = appointment.parsedType == ConsultationType.VIRTUAL
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAppointments),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appointment.doctorName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${appointment.department} • ${appointment.hospitalName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = if (isVirtual) "Video Call" else "Clinic",
                    containerColor = if (isVirtual) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isVirtual) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
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
                    text = "${appointment.dateIso} at ${appointment.timeSlot}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (isVirtual && !appointment.roomId.isNullOrBlank()) {
                Button(
                    onClick = { onJoinVideoCall(appointment.roomId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_join_video_${appointment.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join Secure Video Call")
                }
            }
        }
    }
}

@Composable
private fun DashboardDoseRow(
    medicine: Medicine,
    timeSlot: String,
    status: DoseStatus,
    onMarkTaken: () -> Unit,
    onMarkMissed: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${medicine.name} (${medicine.dosage})",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "$timeSlot • ${medicine.instructions.ifBlank { "Daily dose" }}",
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
                DoseStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onMarkTaken,
                        modifier = Modifier.testTag("dash_take_${medicine.id}_$timeSlot")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Mark dose taken",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    IconButton(
                        onClick = onMarkMissed,
                        modifier = Modifier.testTag("dash_skip_${medicine.id}_$timeSlot")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Mark dose missed",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardReportRow(
    report: MedicalReport,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = report.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${report.category} • ${report.reportDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
