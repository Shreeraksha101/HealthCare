package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AiHealthRepository
import com.example.data.repository.ChatHistoryTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val hasAttachment: Boolean = false,
    val isEmergency: Boolean = false
)

sealed interface AiResponseState {
    data object Idle : AiResponseState
    data object Loading : AiResponseState
    data class Streaming(val partialText: String) : AiResponseState
    data class Error(val message: String) : AiResponseState
}

class AiAssistantViewModel(
    private val aiRepository: AiHealthRepository = AiHealthRepository()
) : ViewModel() {

    private val _messages = MutableStateFlow(
        listOf(
            AiChatMessage(
                id = "welcome",
                isUser = false,
                text = "Hello! I'm your HealthCare Companion AI Guide. I can explain medical terms, summarize wellness tips, or help you prepare questions for your doctor. Remember, I provide general health information only and never replace a licensed physician."
            )
        )
    )
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _responseState = MutableStateFlow<AiResponseState>(AiResponseState.Idle)
    val responseState: StateFlow<AiResponseState> = _responseState.asStateFlow()

    private val _showEmergencyCard = MutableStateFlow(false)
    val showEmergencyCard: StateFlow<Boolean> = _showEmergencyCard.asStateFlow()

    fun sendQuestion(rawPrompt: String, attachedImage: Bitmap? = null) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() && attachedImage == null) return
        val effectivePrompt = prompt.ifEmpty { "Please explain the medical terms shown in this image in simple language." }

        val emergencyDetected = aiRepository.isEmergencySymptomQuery(effectivePrompt)
        if (emergencyDetected) {
            _showEmergencyCard.value = true
        }

        val priorHistory = _messages.value
            .filter { it.id != "welcome" }
            .map { ChatHistoryTurn(isUser = it.isUser, text = it.text) }

        val userMsg = AiChatMessage(
            id = "u_${System.currentTimeMillis()}",
            isUser = true,
            text = effectivePrompt,
            hasAttachment = attachedImage != null,
            isEmergency = emergencyDetected
        )
        _messages.update { it + userMsg }

        viewModelScope.launch {
            _responseState.value = AiResponseState.Loading
            var finalOutput = ""
            try {
                aiRepository.streamHealthResponse(
                    history = priorHistory,
                    prompt = effectivePrompt,
                    attachedImage = attachedImage
                ).collect { accumulated ->
                    finalOutput = accumulated
                    _responseState.value = AiResponseState.Streaming(accumulated)
                }
                val aiMsg = AiChatMessage(
                    id = "m_${System.currentTimeMillis()}",
                    isUser = false,
                    text = finalOutput,
                    isEmergency = emergencyDetected
                )
                _messages.update { it + aiMsg }
                _responseState.value = AiResponseState.Idle
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI assistant request timed out", e)
                _responseState.value = AiResponseState.Error(
                    "Response timed out after 60 seconds. Please check your connection and try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI assistant request failed", e)
                _responseState.value = AiResponseState.Error(
                    e.localizedMessage ?: "Unable to reach the AI Health Assistant right now."
                )
            }
        }
    }

    fun dismissEmergencyCard() {
        _showEmergencyCard.value = false
    }

    fun clearError() {
        if (_responseState.value is AiResponseState.Error) {
            _responseState.value = AiResponseState.Idle
        }
    }

    private companion object {
        const val TAG = "AiAssistantVM"
    }
}
