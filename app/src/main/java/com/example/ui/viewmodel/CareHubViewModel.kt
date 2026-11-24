package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
import com.example.data.model.DoseLog
import com.example.data.model.DoseStatus
import com.example.data.model.MedicalReport
import com.example.data.model.Medicine
import com.example.data.model.UserProfile
import com.example.data.repository.ReportStorageHelper
import com.example.data.repository.UserAndCareRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CareHubViewModel(
    private val repository: UserAndCareRepository,
    private val reportStorageHelper: ReportStorageHelper,
    private val currentUserId: String
) : ViewModel() {

    val todayIso: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError("profile", error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val medicinesState: StateFlow<UiState<List<Medicine>>> = repository.observeMedicines(currentUserId)
        .map<List<Medicine>, UiState<List<Medicine>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError("medicines", error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val doseLogsState: StateFlow<UiState<List<DoseLog>>> = repository.observeDoseLogs(currentUserId)
        .map<List<DoseLog>, UiState<List<DoseLog>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError("dose logs", error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val reportsState: StateFlow<UiState<List<MedicalReport>>> = repository.observeReports(currentUserId)
        .map<List<MedicalReport>, UiState<List<MedicalReport>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError("reports", error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun ensureProfile(user: FirebaseUser?) {
        if (user == null) return
        viewModelScope.launch {
            repository.ensureUserProfile(user)
        }
    }

    fun updateProfile(
        displayName: String,
        bloodGroup: String,
        allergies: String,
        emergencyContactName: String,
        emergencyContactPhone: String,
        dateOfBirth: String,
        providerMode: Boolean,
        notificationsEnabled: Boolean
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateUserProfile(
                displayName = displayName,
                bloodGroup = bloodGroup,
                allergies = allergies,
                emergencyContactName = emergencyContactName,
                emergencyContactPhone = emergencyContactPhone,
                dateOfBirth = dateOfBirth,
                providerMode = providerMode,
                notificationsEnabled = notificationsEnabled
            )
            _isSaving.value = false
            _feedbackMessage.value = result.fold(
                onSuccess = { "Profile & preferences saved." },
                onFailure = { it.localizedMessage ?: "Failed to update profile." }
            )
        }
    }

    fun addMedicine(
        name: String,
        dosage: String,
        instructions: String,
        scheduleTimes: List<String>,
        remindersEnabled: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addMedicine(name, dosage, instructions, scheduleTimes, remindersEnabled)
            _isSaving.value = false
            result.onSuccess {
                _feedbackMessage.value = "Medicine \"$name\" added to your schedule."
                onComplete()
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Could not add medicine."
            }
        }
    }

    fun toggleMedicineReminder(medicineId: String, enabled: Boolean) {
        viewModelScope.launch {
            val result = repository.toggleMedicineReminder(medicineId, enabled)
            result.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to update reminder."
            }
        }
    }

    fun updateMedicineScheduleTimes(
        medicineId: String,
        scheduleTimes: List<String>,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateMedicineScheduleTimes(medicineId, scheduleTimes)
            _isSaving.value = false
            result.onSuccess {
                _feedbackMessage.value = "Reminder schedule updated."
                onComplete()
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to update schedule times."
            }
        }
    }

    fun deleteMedicine(medicine: Medicine) {
        viewModelScope.launch {
            val result = repository.deleteMedicine(medicine.id)
            _feedbackMessage.value = result.fold(
                onSuccess = { "Removed ${medicine.name}." },
                onFailure = { it.localizedMessage ?: "Failed to remove medicine." }
            )
        }
    }

    fun markDose(medicine: Medicine, scheduledTime: String, status: DoseStatus) {
        viewModelScope.launch {
            val result = repository.setDoseStatus(medicine, todayIso, scheduledTime, status)
            result.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to update dose status."
            }
        }
    }

    fun uploadReport(
        title: String,
        category: String,
        reportDate: String,
        sourceUri: Uri,
        notes: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val stored = reportStorageHelper.copyUriToPrivateStorage(currentUserId, sourceUri)
                val result = repository.addMedicalReport(title, category, reportDate, stored, notes)
                result.onSuccess {
                    _feedbackMessage.value = "Report \"$title\" saved privately."
                    onComplete()
                }.onFailure { err ->
                    reportStorageHelper.deleteLocalFile(stored.localRelativePath)
                    _feedbackMessage.value = err.localizedMessage ?: "Failed to save report."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Report upload failed", e)
                _feedbackMessage.value = e.localizedMessage ?: "Unable to import selected file."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteReport(report: MedicalReport) {
        viewModelScope.launch {
            val result = repository.deleteReport(report.id)
            result.onSuccess {
                reportStorageHelper.deleteLocalFile(report.localRelativePath)
                _feedbackMessage.value = "Deleted report \"${report.title}\"."
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to delete report."
            }
        }
    }

    private fun <T> unwrapFirestoreError(label: String, error: Throwable): UiState<T> {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Error observing $label", root)
        return UiState.Error(root.message ?: "Failed to load $label.")
    }

    private companion object {
        const val TAG = "CareHubViewModel"
    }
}
