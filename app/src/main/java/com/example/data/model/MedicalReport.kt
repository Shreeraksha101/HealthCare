package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ReportCategory(val displayName: String) {
    LAB_RESULTS("Lab Results"),
    PRESCRIPTIONS("Prescriptions"),
    IMAGING("Imaging & Scans"),
    DISCHARGE("Discharge Summary"),
    VACCINATION("Vaccination"),
    OTHER("Other");

    companion object {
        fun fromRaw(raw: String?): ReportCategory =
            entries.firstOrNull { it.displayName == raw } ?: OTHER
    }
}

@Keep
@IgnoreExtraProperties
data class MedicalReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = ReportCategory.LAB_RESULTS.displayName,
    val reportDate: String = "",
    val fileName: String = "",
    val mimeType: String = "application/pdf",
    val fileSizeBytes: Long = 0L,
    val localRelativePath: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
