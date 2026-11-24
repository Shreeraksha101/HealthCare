package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

data class ChatHistoryTurn(
    val isUser: Boolean,
    val text: String
)

class AiHealthRepository {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    You are HealthCare Companion's AI Health Information Assistant.
                    Your role:
                    1. Answer general health, wellness, nutrition, sleep, exercise, and preventive care questions in clear, compassionate, easy-to-understand language.
                    2. Explain common medical terms, lab test acronyms, and prescription terminology simply.
                    3. NEVER diagnose diseases, prescribe medications, or replace a licensed physician. Always remind users to consult a qualified healthcare professional for personal medical decisions.
                    4. EMERGENCY SAFETY: If the user mentions serious or life-threatening symptoms (such as chest pain, tightness, shortness of breath, difficulty breathing, signs of stroke like facial drooping/slurred speech/sudden weakness, severe bleeding, loss of consciousness, anaphylaxis, poisoning, or self-harm), IMMEDIATELY start your response with a clear emergency warning advising them to call local emergency services (911 / 112) or go to the nearest emergency department right away.
                    5. Keep answers well-structured with short paragraphs or bullet points suitable for mobile reading across all age groups.
                    """.trimIndent()
                )
            }
        )
    }

    fun streamHealthResponse(
        history: List<ChatHistoryTurn>,
        prompt: String,
        attachedImage: Bitmap? = null
    ): Flow<String> = flow {
        val sdkHistory: List<Content> = history.takeLast(12).map { turn ->
            content(role = if (turn.isUser) "user" else "model") {
                text(turn.text)
            }
        }
        val chat = model.startChat(history = sdkHistory)
        val userContent = content("user") {
            if (attachedImage != null) {
                image(attachedImage)
            }
            text(prompt)
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                chat.sendMessageStream(userContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("AI assistant returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini health stream timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini health stream failed", e)
            throw e
        }
    }

    fun isEmergencySymptomQuery(input: String): Boolean {
        val normalized = input.lowercase()
        return EMERGENCY_KEYWORDS.any { normalized.contains(it) }
    }

    private companion object {
        const val TAG = "AiHealthRepository"
        val EMERGENCY_KEYWORDS = listOf(
            "chest pain",
            "heart attack",
            "can't breathe",
            "cannot breathe",
            "trouble breathing",
            "difficulty breathing",
            "shortness of breath",
            "stroke",
            "slurred speech",
            "face drooping",
            "sudden numbness",
            "severe bleeding",
            "unconscious",
            "fainted",
            "anaphylaxis",
            "swelling throat",
            "choking",
            "overdose",
            "suicide",
            "self harm",
            "self-harm",
            "seizure"
        )
    }
}
