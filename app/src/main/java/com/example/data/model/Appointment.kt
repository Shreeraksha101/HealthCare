package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ConsultationType(val label: String) {
    VIRTUAL("Virtual Video Call"),
    IN_PERSON("In-Person Clinic");

    companion object {
        fun fromRaw(raw: String?): ConsultationType =
            entries.firstOrNull { it.name == raw } ?: IN_PERSON
    }
}

enum class AppointmentStatus(val label: String) {
    UPCOMING("Upcoming"),
    IN_CALL("In Call"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    companion object {
        fun fromRaw(raw: String?): AppointmentStatus =
            entries.firstOrNull { it.name == raw } ?: UPCOMING
    }
}

@Keep
@IgnoreExtraProperties
data class Appointment(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val doctorId: String = "",
    val doctorOwnerUid: String = "",
    val doctorName: String = "",
    val department: String = "",
    val hospitalName: String = "",
    val consultationType: String = ConsultationType.IN_PERSON.name,
    val dateIso: String = "",
    val timeSlot: String = "",
    val reason: String = "",
    val status: String = AppointmentStatus.UPCOMING.name,
    val roomId: String? = null,
    val slotKey: String? = null,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: ConsultationType get() = ConsultationType.fromRaw(consultationType)
    val parsedStatus: AppointmentStatus get() = AppointmentStatus.fromRaw(status)
}
