package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
import com.example.data.model.Appointment
import com.example.data.model.BookedSlot
import com.example.data.model.ConsultationType
import com.example.data.model.Doctor
import com.example.data.repository.TelemedicineAndAppointmentsRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DoctorsAndAppointmentsViewModel(
    private val repository: TelemedicineAndAppointmentsRepository,
    private val currentUserId: String
) : ViewModel() {

    val doctorsState: StateFlow<UiState<List<Doctor>>> = repository.observeDoctors()
        .map<List<Doctor>, UiState<List<Doctor>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapError("doctors", error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val patientAppointmentsState: StateFlow<UiState<List<Appointment>>> =
        repository.observePatientAppointments(currentUserId)
            .map<List<Appointment>, UiState<List<Appointment>>> { UiState.Success(it) }
            .catch { error -> emit(unwrapError("appointments", error)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val doctorAppointmentsState: StateFlow<UiState<List<Appointment>>> =
        repository.observeDoctorAppointments(currentUserId)
            .map<List<Appointment>, UiState<List<Appointment>>> { UiState.Success(it) }
            .catch { error -> emit(unwrapError("doctor schedule", error)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val selectedDoctorIdForSlots = MutableStateFlow<String?>(null)

    val bookedSlotsForSelectedDoctor: StateFlow<List<BookedSlot>> =
        selectedDoctorIdForSlots.flatMapLatest { doctorId ->
            if (doctorId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeBookedSlots(doctorId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing booked slots", firestoreError ?: error)
                    emit(emptyList())
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedDemoDoctorsIfEmpty()
        }
    }

    fun selectDoctorForBookedSlots(doctorId: String) {
        selectedDoctorIdForSlots.value = doctorId
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun bookAppointment(
        patientName: String,
        doctor: Doctor,
        consultationType: ConsultationType,
        dateIso: String,
        timeSlot: String,
        reason: String,
        onBooked: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.bookAppointment(
                patientName = patientName,
                doctor = doctor,
                consultationType = consultationType,
                dateIso = dateIso,
                timeSlot = timeSlot,
                reason = reason
            )
            _isSubmitting.value = false
            result.onSuccess { apptId ->
                val msg = if (consultationType == ConsultationType.VIRTUAL) {
                    "Virtual video consultation booked with ${doctor.name}!"
                } else {
                    "Appointment booked with ${doctor.name}!"
                }
                _feedbackMessage.value = msg
                onBooked(apptId)
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Booking failed. Please try another slot."
            }
        }
    }

    fun cancelAppointment(appointment: Appointment) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.cancelAppointment(appointment)
            _isSubmitting.value = false
            _feedbackMessage.value = result.fold(
                onSuccess = { "Appointment cancelled." },
                onFailure = { it.localizedMessage ?: "Could not cancel appointment." }
            )
        }
    }

    fun rescheduleAppointment(
        appointment: Appointment,
        newDateIso: String,
        newTimeSlot: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.rescheduleAppointment(appointment, newDateIso, newTimeSlot)
            _isSubmitting.value = false
            result.onSuccess {
                _feedbackMessage.value = "Rescheduled to $newDateIso at $newTimeSlot."
                onSuccess()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Reschedule failed."
            }
        }
    }

    fun saveMyDoctorAvailability(
        existingDoctorId: String?,
        name: String,
        specialization: String,
        qualifications: String,
        hospitalName: String,
        clinicAddress: String,
        phone: String,
        experienceYears: Int,
        inPersonFee: Int,
        virtualFee: Int,
        telemedicineEnabled: Boolean,
        bio: String,
        virtualSlots: List<String>,
        clinicSlots: List<String>,
        availableDays: List<String>,
        avatarKey: String,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.upsertMyDoctorProfile(
                existingDoctorId = existingDoctorId,
                name = name,
                specialization = specialization,
                qualifications = qualifications,
                hospitalName = hospitalName,
                clinicAddress = clinicAddress,
                phone = phone,
                experienceYears = experienceYears,
                inPersonFee = inPersonFee,
                virtualFee = virtualFee,
                telemedicineEnabled = telemedicineEnabled,
                bio = bio,
                virtualSlots = virtualSlots,
                clinicSlots = clinicSlots,
                availableDays = availableDays,
                avatarKey = avatarKey
            )
            _isSubmitting.value = false
            result.onSuccess {
                _feedbackMessage.value = "Doctor virtual consultation schedule published!"
                onSaved()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Could not save doctor schedule."
            }
        }
    }

    private fun <T> unwrapError(label: String, error: Throwable): UiState<T> {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Error observing $label", root)
        return UiState.Error(root.message ?: "Failed to load $label.")
    }

    private companion object {
        const val TAG = "DocsAndApptsVM"
    }
}
