package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.model.ConsultationType
import com.example.data.model.Doctor
import com.example.ui.components.DoctorAvatar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.DoctorsAndAppointmentsViewModel

@Composable
fun DoctorsScreen(
    viewModel: DoctorsAndAppointmentsViewModel,
    onOpenDoctorDetail: (String) -> Unit,
    onOpenProviderAvailability: () -> Unit
) {
    val doctorsState by viewModel.doctorsState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedSpecialty by remember { mutableStateOf("All") }

    val specialties = listOf(
        "All",
        "Telemedicine",
        "Cardiology",
        "General Medicine",
        "Dermatology",
        "Pediatrics",
        "Neurology",
        "Orthopedics"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Find Doctors",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    FilledTonalButton(
                        onClick = onOpenProviderAvailability,
                        modifier = Modifier.testTag("doctor_portal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Doctor Portal")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name, specialty, or hospital...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search doctors") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doctors_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    specialties.forEach { spec ->
                        FilterChip(
                            selected = selectedSpecialty == spec,
                            onClick = { selectedSpecialty = spec },
                            label = { Text(spec) }
                        )
                    }
                }
            }

            when (val state = doctorsState) {
                is UiState.Loading -> LoadingStateBox()
                is UiState.Error -> Box(modifier = Modifier.padding(16.dp)) {
                    ErrorStateCard(message = state.message)
                }
                is UiState.Success -> {
                    val filtered = state.data.filter { doc ->
                        val matchesQuery = searchQuery.isBlank() ||
                            doc.name.contains(searchQuery, ignoreCase = true) ||
                            doc.specialization.contains(searchQuery, ignoreCase = true) ||
                            doc.hospitalName.contains(searchQuery, ignoreCase = true)
                        val matchesSpec = when (selectedSpecialty) {
                            "All" -> true
                            "Telemedicine" -> doc.telemedicineEnabled && doc.virtualSlots.isNotEmpty()
                            else -> doc.specialization.equals(selectedSpecialty, ignoreCase = true)
                        }
                        matchesQuery && matchesSpec
                    }
                    if (filtered.isEmpty()) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            EmptyStateCard(
                                icon = Icons.Rounded.MedicalServices,
                                title = "No matching doctors found",
                                subtitle = "Try clearing your search or switching specialization filters."
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filtered, key = { it.id }) { doctor ->
                                DoctorDirectoryCard(
                                    doctor = doctor,
                                    onClick = { onOpenDoctorDetail(doctor.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoctorDirectoryCard(
    doctor: Doctor,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("doctor_card_${doctor.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DoctorAvatar(
                    avatarKey = doctor.avatarKey,
                    contentDescription = "Photo of ${doctor.name}",
                    size = 64.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doctor.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = doctor.specialization,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = doctor.qualifications,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Rating",
                        tint = WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "${doctor.rating}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${doctor.hospitalName} • ${doctor.experienceYears} yrs exp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (doctor.telemedicineEnabled && doctor.virtualSlots.isNotEmpty()) {
                    StatusPill(
                        text = "Virtual $${doctor.virtualFee}",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.secondary,
                        icon = Icons.Rounded.Videocam
                    )
                } else {
                    StatusPill(
                        text = "Clinic $${doctor.inPersonFee}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                        icon = Icons.Rounded.LocalHospital
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DoctorDetailScreen(
    doctorId: String,
    patientName: String,
    viewModel: DoctorsAndAppointmentsViewModel,
    onBack: () -> Unit,
    onAppointmentBooked: () -> Unit
) {
    val doctorsState by viewModel.doctorsState.collectAsStateWithLifecycle()
    val bookedSlots by viewModel.bookedSlotsForSelectedDoctor.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(doctorId) {
        viewModel.selectDoctorForBookedSlots(doctorId)
    }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val doctor = (doctorsState as? UiState.Success)?.data?.firstOrNull { it.id == doctorId }
    var bookingType by remember { mutableStateOf<ConsultationType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doctor Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (doctor == null) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                LoadingStateBox()
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            DoctorAvatar(
                                avatarKey = doctor.avatarKey,
                                contentDescription = doctor.name,
                                size = 80.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doctor.name, style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    doctor.specialization,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    doctor.qualifications,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(doctor.bio, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(
                                text = "${doctor.experienceYears} yrs experience",
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StatusPill(
                                text = "${doctor.rating} ★ Rating",
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Clinic & Availability Card
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Clinic & Schedule", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${doctor.hospitalName}\n${doctor.clinicAddress}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(doctor.phone, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (doctor.availableDays.isNotEmpty()) {
                            Text(
                                "Available: ${doctor.availableDays.joinToString(" • ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Virtual Telemedicine Availability Card
                if (doctor.telemedicineEnabled && doctor.virtualSlots.isNotEmpty()) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Videocam,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    "Virtual Video Consultation Slots ($${doctor.virtualFee})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                doctor.virtualSlots.forEach { slot ->
                                    AssistChip(
                                        onClick = { bookingType = ConsultationType.VIRTUAL },
                                        label = { Text(slot) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (doctor.telemedicineEnabled && doctor.virtualSlots.isNotEmpty()) {
                        Button(
                            onClick = { bookingType = ConsultationType.VIRTUAL },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("book_virtual_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                Icons.Rounded.Videocam,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Book Virtual")
                        }
                    }
                    OutlinedButton(
                        onClick = { bookingType = ConsultationType.IN_PERSON },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("book_clinic_button")
                    ) {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Book Clinic ($${doctor.inPersonFee})")
                    }
                }
            }
        }
    }

    val currentBookingType = bookingType
    if (currentBookingType != null && doctor != null) {
        BookAppointmentDialog(
            doctors = listOf(doctor),
            initialDoctor = doctor,
            initialType = currentBookingType,
            bookedSlots = bookedSlots,
            isSubmitting = isSubmitting,
            onDoctorChanged = { viewModel.selectDoctorForBookedSlots(it.id) },
            onDismiss = { bookingType = null },
            onConfirmBooking = { selectedDoc, type, dateIso, timeSlot, reason ->
                viewModel.bookAppointment(
                    patientName = patientName,
                    doctor = selectedDoc,
                    consultationType = type,
                    dateIso = dateIso,
                    timeSlot = timeSlot,
                    reason = reason,
                    onBooked = {
                        bookingType = null
                        onAppointmentBooked()
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProviderAvailabilityScreen(
    currentUserId: String,
    defaultDoctorName: String,
    viewModel: DoctorsAndAppointmentsViewModel,
    onBack: () -> Unit
) {
    val doctorsState by viewModel.doctorsState.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val existingMyProvider = (doctorsState as? UiState.Success)?.data
        ?.firstOrNull { it.id == "provider_$currentUserId" }

    var name by remember(existingMyProvider) {
        mutableStateOf(existingMyProvider?.name ?: "Dr. $defaultDoctorName")
    }
    var specialization by remember(existingMyProvider) {
        mutableStateOf(existingMyProvider?.specialization ?: "General Medicine")
    }
    var qualifications by remember(existingMyProvider) {
        mutableStateOf(existingMyProvider?.qualifications ?: "MD, Telehealth Certified")
    }
    var hospitalName by remember(existingMyProvider) {
        mutableStateOf(existingMyProvider?.hospitalName ?: "HealthCare Companion Virtual Clinic")
    }
    var clinicAddress by remember(existingMyProvider) {
        mutableStateOf(existingMyProvider?.clinicAddress ?: "100 Telehealth Plaza, Suite 2B")
    }
    var phone by remember(existingMyProvider) {
        mutableStateOf(existingMyProvider?.phone ?: "+1-555-0199")
    }
    var virtualFeeStr by remember(existingMyProvider) {
        mutableStateOf((existingMyProvider?.virtualFee ?: 60).toString())
    }
    var clinicFeeStr by remember(existingMyProvider) {
        mutableStateOf((existingMyProvider?.inPersonFee ?: 95).toString())
    }
    var telemedicineEnabled by remember(existingMyProvider) {
        mutableStateOf(existingMyProvider?.telemedicineEnabled ?: true)
    }
    var bio by remember(existingMyProvider) {
        mutableStateOf(existingMyProvider?.bio ?: "Offering secure online video consultations and care follow-ups.")
    }

    val allCandidateSlots = listOf(
        "08:00 AM", "09:00 AM", "10:30 AM", "12:00 PM",
        "02:00 PM", "03:30 PM", "05:00 PM", "07:00 PM"
    )
    val selectedVirtualSlots = remember(existingMyProvider) {
        mutableStateListOf<String>().apply {
            addAll(existingMyProvider?.virtualSlots ?: listOf("09:00 AM", "10:30 AM", "03:30 PM", "05:00 PM"))
        }
    }
    val allDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val selectedDays = remember(existingMyProvider) {
        mutableStateListOf<String>().apply {
            addAll(existingMyProvider?.availableDays ?: listOf("Mon", "Tue", "Wed", "Thu", "Fri"))
        }
    }
    var customSlotText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doctor Virtual Availability") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Accept Virtual Consultations",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Allow patients to book telemedicine video slots on your profile.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = telemedicineEnabled,
                            onCheckedChange = { telemedicineEnabled = it },
                            modifier = Modifier.testTag("provider_telemedicine_switch")
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Doctor Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = specialization,
                    onValueChange = { specialization = it },
                    label = { Text("Specialization") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = qualifications,
                    onValueChange = { qualifications = it },
                    label = { Text("Qualifications") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hospitalName,
                    onValueChange = { hospitalName = it },
                    label = { Text("Hospital / Clinic Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = virtualFeeStr,
                        onValueChange = { virtualFeeStr = it.filter(Char::isDigit) },
                        label = { Text("Virtual Fee ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = clinicFeeStr,
                        onValueChange = { clinicFeeStr = it.filter(Char::isDigit) },
                        label = { Text("Clinic Fee ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Available Days", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allDays.forEach { day ->
                        val active = day in selectedDays
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (active) selectedDays.remove(day) else selectedDays.add(day)
                            },
                            label = { Text(day) }
                        )
                    }
                }

                Text("Virtual Video Call Slots", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (allCandidateSlots + selectedVirtualSlots).distinct().forEach { slot ->
                        val active = slot in selectedVirtualSlots
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (active) selectedVirtualSlots.remove(slot) else selectedVirtualSlots.add(slot)
                            },
                            label = { Text(slot) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customSlotText,
                        onValueChange = { customSlotText = it },
                        label = { Text("Add custom slot (e.g. 08:30 PM)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = {
                            val trimmed = customSlotText.trim()
                            if (trimmed.length >= 3 && trimmed !in selectedVirtualSlots) {
                                selectedVirtualSlots.add(trimmed)
                                customSlotText = ""
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add slot")
                    }
                }

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Clinical Bio") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        viewModel.saveMyDoctorAvailability(
                            existingDoctorId = existingMyProvider?.id,
                            name = name,
                            specialization = specialization,
                            qualifications = qualifications,
                            hospitalName = hospitalName,
                            clinicAddress = clinicAddress,
                            phone = phone,
                            experienceYears = existingMyProvider?.experienceYears ?: 8,
                            inPersonFee = clinicFeeStr.toIntOrNull() ?: 95,
                            virtualFee = virtualFeeStr.toIntOrNull() ?: 60,
                            telemedicineEnabled = telemedicineEnabled,
                            bio = bio,
                            virtualSlots = selectedVirtualSlots.toList(),
                            clinicSlots = existingMyProvider?.clinicSlots?.ifEmpty {
                                listOf("10:00 AM", "02:00 PM")
                            } ?: listOf("10:00 AM", "02:00 PM"),
                            availableDays = selectedDays.toList(),
                            avatarKey = existingMyProvider?.avatarKey ?: "female",
                            onSaved = {}
                        )
                    },
                    enabled = !isSubmitting &&
                        name.trim().length >= 2 &&
                        specialization.trim().length >= 2 &&
                        qualifications.trim().length >= 2 &&
                        hospitalName.trim().length >= 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_provider_availability_button")
                ) {
                    Text(if (isSubmitting) "Publishing..." else "Publish Virtual Availability")
                }
            }
        }
    }
}
