package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class DoseStatus(val label: String) {
    TAKEN("Taken"),
    MISSED("Missed"),
    PENDING("Pending");

    companion object {
        fun fromRaw(raw: String?): DoseStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class Medicine(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val dosage: String = "",
    val instructions: String = "",
    val scheduleTimes: List<String> = emptyList(),
    val remindersEnabled: Boolean = true,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DoseLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val medicineId: String = "",
    val medicineName: String = "",
    val dosage: String = "",
    val dateIso: String = "",
    val scheduledTime: String = "",
    val status: String = DoseStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedStatus: DoseStatus get() = DoseStatus.fromRaw(status)
}
