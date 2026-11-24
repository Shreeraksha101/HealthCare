package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class Doctor(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val specialization: String = "",
    val qualifications: String = "",
    val hospitalName: String = "",
    val clinicAddress: String = "",
    val phone: String = "",
    val experienceYears: Int = 0,
    val rating: Double = 4.8,
    val inPersonFee: Int = 80,
    val virtualFee: Int = 50,
    val telemedicineEnabled: Boolean = true,
    val listedInDirectory: Boolean = true,
    val avatarKey: String = "female",
    val bio: String = "",
    val virtualSlots: List<String> = emptyList(),
    val clinicSlots: List<String> = emptyList(),
    val availableDays: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BookedSlot(
    @DocumentId val id: String = "",
    val doctorId: String = "",
    val dateIso: String = "",
    val timeSlot: String = "",
    val consultationType: String = "VIRTUAL",
    val bookedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
