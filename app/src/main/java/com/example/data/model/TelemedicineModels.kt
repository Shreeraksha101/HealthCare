package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class RoomStatus(val label: String) {
    WAITING("Waiting to Join"),
    IN_CALL("Connected"),
    ENDED("Call Ended");

    companion object {
        fun fromRaw(raw: String?): RoomStatus =
            entries.firstOrNull { it.name == raw } ?: WAITING
    }
}

@Keep
@IgnoreExtraProperties
data class TelemedicineRoom(
    @DocumentId val id: String = "",
    val appointmentId: String = "",
    val patientId: String = "",
    val doctorOwnerUid: String = "",
    val participantUids: List<String> = emptyList(),
    val doctorName: String = "",
    val patientName: String = "",
    val status: String = RoomStatus.WAITING.name,
    val patientMicOn: Boolean = true,
    val patientCamOn: Boolean = true,
    val doctorMicOn: Boolean = true,
    val doctorCamOn: Boolean = true,
    val durationSeconds: Long = 0L,
    val summaryNotes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedStatus: RoomStatus get() = RoomStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class TelemedicineMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val participantUids: List<String> = emptyList(),
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "PATIENT",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
