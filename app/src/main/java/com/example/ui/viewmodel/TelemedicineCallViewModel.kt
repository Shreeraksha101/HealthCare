package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
import com.example.data.model.RoomStatus
import com.example.data.model.TelemedicineMessage
import com.example.data.model.TelemedicineRoom
import com.example.data.repository.TelemedicineAndAppointmentsRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TelemedicineCallViewModel(
    private val repository: TelemedicineAndAppointmentsRepository,
    private val roomId: String,
    private val currentUserId: String
) : ViewModel() {

    val roomState: StateFlow<UiState<TelemedicineRoom?>> = repository.observeTelemedicineRoom(roomId)
        .map<TelemedicineRoom?, UiState<TelemedicineRoom?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing telemedicine room", root)
            emit(UiState.Error(root.message ?: "Failed to connect to consultation room."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val messagesState: StateFlow<List<TelemedicineMessage>> =
        repository.observeRoomMessages(roomId, currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing room messages", firestoreError ?: error)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _localMicOn = MutableStateFlow(true)
    val localMicOn: StateFlow<Boolean> = _localMicOn.asStateFlow()

    private val _localCamOn = MutableStateFlow(true)
    val localCamOn: StateFlow<Boolean> = _localCamOn.asStateFlow()

    private val _useFrontCamera = MutableStateFlow(true)
    val useFrontCamera: StateFlow<Boolean> = _useFrontCamera.asStateFlow()

    private val _speakerOn = MutableStateFlow(true)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    private val _callEnded = MutableStateFlow(false)
    val callEnded: StateFlow<Boolean> = _callEnded.asStateFlow()

    private val _errorNotice = MutableStateFlow<String?>(null)
    val errorNotice: StateFlow<String?> = _errorNotice.asStateFlow()

    private var timerStarted = false

    fun startSessionIfNeeded(room: TelemedicineRoom) {
        if (room.parsedStatus == RoomStatus.ENDED) {
            _callEnded.value = true
            return
        }
        if (!timerStarted) {
            timerStarted = true
            val isDoc = room.doctorOwnerUid == currentUserId && room.patientId != currentUserId
            viewModelScope.launch {
                repository.joinOrUpdateTelemedicineRoom(
                    roomId = room.id,
                    isDoctorParticipant = isDoc,
                    micOn = _localMicOn.value,
                    camOn = _localCamOn.value,
                    durationSeconds = room.durationSeconds
                )
            }
            viewModelScope.launch {
                while (!_callEnded.value) {
                    delay(1000L)
                    _elapsedSeconds.value += 1L
                }
            }
        }
    }

    fun toggleMic(room: TelemedicineRoom?) {
        val next = !_localMicOn.value
        _localMicOn.value = next
        if (room != null && room.parsedStatus != RoomStatus.ENDED) {
            val isDoc = room.doctorOwnerUid == currentUserId && room.patientId != currentUserId
            viewModelScope.launch {
                repository.joinOrUpdateTelemedicineRoom(
                    roomId = room.id,
                    isDoctorParticipant = isDoc,
                    micOn = next,
                    camOn = _localCamOn.value,
                    durationSeconds = _elapsedSeconds.value
                )
            }
        }
    }

    fun toggleCam(room: TelemedicineRoom?) {
        val next = !_localCamOn.value
        _localCamOn.value = next
        if (room != null && room.parsedStatus != RoomStatus.ENDED) {
            val isDoc = room.doctorOwnerUid == currentUserId && room.patientId != currentUserId
            viewModelScope.launch {
                repository.joinOrUpdateTelemedicineRoom(
                    roomId = room.id,
                    isDoctorParticipant = isDoc,
                    micOn = _localMicOn.value,
                    camOn = next,
                    durationSeconds = _elapsedSeconds.value
                )
            }
        }
    }

    fun flipCamera() {
        _useFrontCamera.value = !_useFrontCamera.value
    }

    fun toggleSpeaker() {
        _speakerOn.value = !_speakerOn.value
    }

    fun sendMessage(room: TelemedicineRoom, senderName: String, text: String) {
        val role = if (room.doctorOwnerUid == currentUserId && room.patientId != currentUserId) "DOCTOR" else "PATIENT"
        viewModelScope.launch {
            val result = repository.sendRoomMessage(room, senderName, role, text)
            result.onFailure {
                _errorNotice.value = it.localizedMessage ?: "Failed to send message."
            }
        }
    }

    fun endCall(room: TelemedicineRoom, summaryNotes: String, onCompleted: () -> Unit) {
        viewModelScope.launch {
            val result = repository.endTelemedicineCall(room, _elapsedSeconds.value, summaryNotes)
            result.onSuccess {
                _callEnded.value = true
                onCompleted()
            }.onFailure {
                _errorNotice.value = it.localizedMessage ?: "Could not end consultation cleanly."
            }
        }
    }

    fun clearError() {
        _errorNotice.value = null
    }

    private companion object {
        const val TAG = "TelemedicineCallVM"
    }
}
