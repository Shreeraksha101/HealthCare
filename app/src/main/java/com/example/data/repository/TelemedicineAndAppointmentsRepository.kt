package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.BookedSlot
import com.example.data.model.ConsultationType
import com.example.data.model.Doctor
import com.example.data.model.RoomStatus
import com.example.data.model.TelemedicineMessage
import com.example.data.model.TelemedicineRoom
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TelemedicineAndAppointmentsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    // --- DOCTORS DIRECTORY & PROVIDER PORTAL ---

    fun observeDoctors(): Flow<List<Doctor>> = flow {
        val uid = requireUserId()
        val path = "doctors"
        emitAll(
            db.collection("doctors")
                .whereEqualTo("listedInDirectory", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Doctor::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.rating }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeDoctor(doctorId: String): Flow<Doctor?> = flow {
        val uid = requireUserId()
        val path = "doctors/$doctorId"
        emitAll(
            db.collection("doctors").document(doctorId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(Doctor::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeBookedSlots(doctorId: String): Flow<List<BookedSlot>> = flow {
        val uid = requireUserId()
        val path = "doctors/$doctorId/bookedSlots"
        emitAll(
            db.collection("doctors").document(doctorId).collection("bookedSlots")
                .whereEqualTo("doctorId", doctorId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(BookedSlot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun seedDemoDoctorsIfEmpty(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val existing = db.collection("doctors")
                .whereEqualTo("listedInDirectory", true)
                .limit(1)
                .get()
                .await()
            if (!existing.isEmpty) return Result.success(Unit)

            val batch = db.batch()
            val demoDoctors = listOf(
                mapOf(
                    "ownerUid" to uid,
                    "name" to "Dr. Ananya Sharma",
                    "specialization" to "Cardiology",
                    "qualifications" to "MD, FACC • Heart & Vascular Care",
                    "hospitalName" to "St. Jude Heart & Care Institute",
                    "clinicAddress" to "210 Wellness Blvd, Suite 400",
                    "phone" to "+1-555-0148",
                    "experienceYears" to 14,
                    "rating" to 4.9,
                    "inPersonFee" to 120,
                    "virtualFee" to 85,
                    "telemedicineEnabled" to true,
                    "listedInDirectory" to true,
                    "avatarKey" to "female",
                    "bio" to "Board-certified cardiologist specializing in preventive heart health, hypertension management, and virtual ECG follow-ups.",
                    "virtualSlots" to listOf("09:00 AM", "10:30 AM", "02:00 PM", "04:30 PM"),
                    "clinicSlots" to listOf("11:30 AM", "03:00 PM"),
                    "availableDays" to listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "ownerUid" to uid,
                    "name" to "Dr. Marcus Chen",
                    "specialization" to "General Medicine",
                    "qualifications" to "MBBS, MD • Internal Medicine",
                    "hospitalName" to "Metro Care Medical Center",
                    "clinicAddress" to "845 Oakridge Park Ave",
                    "phone" to "+1-555-0192",
                    "experienceYears" to 11,
                    "rating" to 4.8,
                    "inPersonFee" to 75,
                    "virtualFee" to 45,
                    "telemedicineEnabled" to true,
                    "listedInDirectory" to true,
                    "avatarKey" to "male",
                    "bio" to "Primary care physician focused on acute care, diabetes management, annual checkups, and rapid video consultations.",
                    "virtualSlots" to listOf("08:30 AM", "10:00 AM", "01:30 PM", "05:00 PM", "06:30 PM"),
                    "clinicSlots" to listOf("09:30 AM", "12:00 PM", "03:30 PM"),
                    "availableDays" to listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "ownerUid" to uid,
                    "name" to "Dr. Elena Rostova",
                    "specialization" to "Dermatology",
                    "qualifications" to "MD, FAAD • Clinical Dermatology",
                    "hospitalName" to "ClearSkin Specialty Clinic",
                    "clinicAddress" to "112 Cedar Medical Plaza",
                    "phone" to "+1-555-0167",
                    "experienceYears" to 9,
                    "rating" to 4.9,
                    "inPersonFee" to 100,
                    "virtualFee" to 65,
                    "telemedicineEnabled" to true,
                    "listedInDirectory" to true,
                    "avatarKey" to "female",
                    "bio" to "Offers high-resolution teledermatology video consultations for rashes, acne, eczema, and ongoing skin care guidance.",
                    "virtualSlots" to listOf("09:30 AM", "11:00 AM", "03:00 PM", "05:30 PM"),
                    "clinicSlots" to listOf("10:00 AM", "01:00 PM"),
                    "availableDays" to listOf("Tue", "Wed", "Thu", "Fri"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "ownerUid" to uid,
                    "name" to "Dr. David Okafor",
                    "specialization" to "Pediatrics",
                    "qualifications" to "MD, FAAP • Child & Adolescent Care",
                    "hospitalName" to "Sunrise Children's Pavilion",
                    "clinicAddress" to "530 Maple Creek Way",
                    "phone" to "+1-555-0133",
                    "experienceYears" to 16,
                    "rating" to 4.9,
                    "inPersonFee" to 90,
                    "virtualFee" to 55,
                    "telemedicineEnabled" to true,
                    "listedInDirectory" to true,
                    "avatarKey" to "male",
                    "bio" to "Dedicated pediatrician helping parents with fever triage, nutrition, developmental milestones, and convenient video follow-ups.",
                    "virtualSlots" to listOf("08:00 AM", "11:30 AM", "04:00 PM", "07:00 PM"),
                    "clinicSlots" to listOf("09:00 AM", "02:30 PM"),
                    "availableDays" to listOf("Mon", "Tue", "Thu", "Fri", "Sat"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "ownerUid" to uid,
                    "name" to "Dr. Priya Nair",
                    "specialization" to "Neurology",
                    "qualifications" to "MD, DM • Headache & Sleep Medicine",
                    "hospitalName" to "NeuroCare Clinical Tower",
                    "clinicAddress" to "780 Beacon Hill Rd",
                    "phone" to "+1-555-0174",
                    "experienceYears" to 13,
                    "rating" to 4.7,
                    "inPersonFee" to 140,
                    "virtualFee" to 95,
                    "telemedicineEnabled" to true,
                    "listedInDirectory" to true,
                    "avatarKey" to "female",
                    "bio" to "Specialist in migraines, sleep disorders, and neurological rehabilitation with virtual medication review slots.",
                    "virtualSlots" to listOf("10:00 AM", "02:30 PM", "04:30 PM"),
                    "clinicSlots" to listOf("11:00 AM", "01:30 PM"),
                    "availableDays" to listOf("Mon", "Wed", "Fri"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "ownerUid" to uid,
                    "name" to "Dr. Lucas Bennett",
                    "specialization" to "Orthopedics",
                    "qualifications" to "MS Ortho, MD • Sports & Joint Care",
                    "hospitalName" to "ActiveMotion Orthopedic Center",
                    "clinicAddress" to "95 Harbor Medical St",
                    "phone" to "+1-555-0185",
                    "experienceYears" to 10,
                    "rating" to 4.8,
                    "inPersonFee" to 115,
                    "virtualFee" to 70,
                    "telemedicineEnabled" to false,
                    "listedInDirectory" to true,
                    "avatarKey" to "male",
                    "bio" to "Focuses on joint mobility, sports injury recovery, and physical therapy planning.",
                    "virtualSlots" to emptyList<String>(),
                    "clinicSlots" to listOf("09:00 AM", "11:00 AM", "02:00 PM", "04:00 PM"),
                    "availableDays" to listOf("Mon", "Tue", "Thu", "Fri"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            demoDoctors.forEachIndexed { idx, data ->
                val docId = "seed_doc_${idx + 1}_${uid.take(8)}"
                batch.set(db.collection("doctors").document(docId), data)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "doctors")
            Result.failure(e)
        }
    }

    suspend fun upsertMyDoctorProfile(
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
        avatarKey: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val doctorId = existingDoctorId?.takeIf { it.isNotBlank() } ?: "provider_$uid"
        val path = "doctors/$doctorId"
        return try {
            val verifiedUid = requireUserId()
            val docRef = db.collection("doctors").document(doctorId)
            val snap = docRef.get().await()
            val cleanVirtualSlots = virtualSlots.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(24)
            val cleanClinicSlots = clinicSlots.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(24)
            val cleanDays = availableDays.distinct().take(7)

            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "name" to name.trim().take(100),
                        "specialization" to specialization.trim().take(60),
                        "qualifications" to qualifications.trim().take(120),
                        "hospitalName" to hospitalName.trim().take(120),
                        "clinicAddress" to clinicAddress.trim().take(200),
                        "phone" to phone.trim().take(30),
                        "experienceYears" to experienceYears.coerceIn(0, 80),
                        "inPersonFee" to inPersonFee.coerceIn(0, 100_000),
                        "virtualFee" to virtualFee.coerceIn(0, 100_000),
                        "telemedicineEnabled" to telemedicineEnabled,
                        "listedInDirectory" to true,
                        "avatarKey" to if (avatarKey == "male") "male" else "female",
                        "bio" to bio.trim().take(500),
                        "virtualSlots" to cleanVirtualSlots,
                        "clinicSlots" to cleanClinicSlots,
                        "availableDays" to cleanDays,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "ownerUid" to verifiedUid,
                        "name" to name.trim().take(100),
                        "specialization" to specialization.trim().take(60),
                        "qualifications" to qualifications.trim().take(120),
                        "hospitalName" to hospitalName.trim().take(120),
                        "clinicAddress" to clinicAddress.trim().take(200),
                        "phone" to phone.trim().take(30),
                        "experienceYears" to experienceYears.coerceIn(0, 80),
                        "rating" to 4.9,
                        "inPersonFee" to inPersonFee.coerceIn(0, 100_000),
                        "virtualFee" to virtualFee.coerceIn(0, 100_000),
                        "telemedicineEnabled" to telemedicineEnabled,
                        "listedInDirectory" to true,
                        "avatarKey" to if (avatarKey == "male") "male" else "female",
                        "bio" to bio.trim().take(500),
                        "virtualSlots" to cleanVirtualSlots,
                        "clinicSlots" to cleanClinicSlots,
                        "availableDays" to cleanDays,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(doctorId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- APPOINTMENTS ---

    fun observePatientAppointments(userId: String): Flow<List<Appointment>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "appointments"
        emitAll(
            db.collection("appointments")
                .whereEqualTo("patientId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Appointment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy<Appointment> { it.dateIso }.thenBy { it.timeSlot })
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeDoctorAppointments(userId: String): Flow<List<Appointment>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "appointments"
        emitAll(
            db.collection("appointments")
                .whereEqualTo("doctorOwnerUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Appointment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy<Appointment> { it.dateIso }.thenBy { it.timeSlot })
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun bookAppointment(
        patientName: String,
        doctor: Doctor,
        consultationType: ConsultationType,
        dateIso: String,
        timeSlot: String,
        reason: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "appointments"
        return try {
            val verifiedUid = requireUserId()
            require(dateIso.isNotBlank() && timeSlot.isNotBlank()) { "Please pick a date and time slot." }

            val slotKey = "${dateIso}_${timeSlot}".replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val apptRef = db.collection("appointments").document()
            val slotRef = db.collection("doctors").document(doctor.id)
                .collection("bookedSlots").document(slotKey)

            val roomRef = if (consultationType == ConsultationType.VIRTUAL) {
                db.collection("telemedicineRooms").document("room_${apptRef.id}")
            } else null

            val batch = db.batch()
            batch.set(
                slotRef,
                mapOf(
                    "doctorId" to doctor.id,
                    "dateIso" to dateIso.take(20),
                    "timeSlot" to timeSlot.take(20),
                    "consultationType" to consultationType.name,
                    "bookedByUid" to verifiedUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                apptRef,
                mapOf(
                    "patientId" to verifiedUid,
                    "patientName" to patientName.trim().take(80).ifBlank { "Patient" },
                    "doctorId" to doctor.id,
                    "doctorOwnerUid" to doctor.ownerUid,
                    "doctorName" to doctor.name.take(100),
                    "department" to doctor.specialization.take(60),
                    "hospitalName" to doctor.hospitalName.take(120),
                    "consultationType" to consultationType.name,
                    "dateIso" to dateIso.take(20),
                    "timeSlot" to timeSlot.take(20),
                    "reason" to reason.trim().take(400),
                    "status" to AppointmentStatus.UPCOMING.name,
                    "roomId" to roomRef?.id,
                    "slotKey" to slotKey,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
            )
            if (roomRef != null) {
                val participants = listOf(verifiedUid, doctor.ownerUid).distinct()
                batch.set(
                    roomRef,
                    mapOf(
                        "appointmentId" to apptRef.id,
                        "patientId" to verifiedUid,
                        "doctorOwnerUid" to doctor.ownerUid,
                        "participantUids" to participants,
                        "doctorName" to doctor.name.take(100),
                        "patientName" to patientName.trim().take(80).ifBlank { "Patient" },
                        "status" to RoomStatus.WAITING.name,
                        "patientMicOn" to true,
                        "patientCamOn" to true,
                        "doctorMicOn" to true,
                        "doctorCamOn" to true,
                        "durationSeconds" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(apptRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun cancelAppointment(appointment: Appointment): Result<Unit> {
        val path = "appointments/${appointment.id}"
        return try {
            val verifiedUid = requireUserId()
            val batch = db.batch()
            val apptRef = db.collection("appointments").document(appointment.id)
            batch.update(
                apptRef,
                mapOf(
                    "status" to AppointmentStatus.CANCELLED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val oldSlotKey = appointment.slotKey
            if (!oldSlotKey.isNullOrBlank() && appointment.patientId == verifiedUid) {
                val slotRef = db.collection("doctors").document(appointment.doctorId)
                    .collection("bookedSlots").document(oldSlotKey)
                batch.delete(slotRef)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun rescheduleAppointment(
        appointment: Appointment,
        newDateIso: String,
        newTimeSlot: String
    ): Result<Unit> {
        val path = "appointments/${appointment.id}"
        return try {
            val verifiedUid = requireUserId()
            val newSlotKey = "${newDateIso}_${newTimeSlot}".replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val batch = db.batch()
            val oldSlotKey = appointment.slotKey
            if (!oldSlotKey.isNullOrBlank() && oldSlotKey != newSlotKey && appointment.patientId == verifiedUid) {
                batch.delete(
                    db.collection("doctors").document(appointment.doctorId)
                        .collection("bookedSlots").document(oldSlotKey)
                )
            }
            batch.set(
                db.collection("doctors").document(appointment.doctorId)
                    .collection("bookedSlots").document(newSlotKey),
                mapOf(
                    "doctorId" to appointment.doctorId,
                    "dateIso" to newDateIso.take(20),
                    "timeSlot" to newTimeSlot.take(20),
                    "consultationType" to appointment.consultationType,
                    "bookedByUid" to appointment.patientId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("appointments").document(appointment.id),
                mapOf(
                    "dateIso" to newDateIso.take(20),
                    "timeSlot" to newTimeSlot.take(20),
                    "slotKey" to newSlotKey,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- TELEMEDICINE ROOMS & IN-CALL CHAT ---

    fun observeTelemedicineRoom(roomId: String): Flow<TelemedicineRoom?> = flow {
        val uid = requireUserId()
        val path = "telemedicineRooms/$roomId"
        emitAll(
            db.collection("telemedicineRooms").document(roomId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(TelemedicineRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    suspend fun joinOrUpdateTelemedicineRoom(
        roomId: String,
        isDoctorParticipant: Boolean,
        micOn: Boolean,
        camOn: Boolean,
        durationSeconds: Long
    ): Result<Unit> {
        val path = "telemedicineRooms/$roomId"
        return try {
            requireUserId()
            val updates = mutableMapOf<String, Any>(
                "status" to RoomStatus.IN_CALL.name,
                "durationSeconds" to durationSeconds.coerceIn(0L, 86_400L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isDoctorParticipant) {
                updates["doctorMicOn"] = micOn
                updates["doctorCamOn"] = camOn
            } else {
                updates["patientMicOn"] = micOn
                updates["patientCamOn"] = camOn
            }
            db.collection("telemedicineRooms").document(roomId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun endTelemedicineCall(
        room: TelemedicineRoom,
        durationSeconds: Long,
        summaryNotes: String
    ): Result<Unit> {
        val path = "telemedicineRooms/${room.id}"
        return try {
            requireUserId()
            val cleanNotes = summaryNotes.trim().take(1000)
            val batch = db.batch()
            batch.update(
                db.collection("telemedicineRooms").document(room.id),
                mapOf(
                    "status" to RoomStatus.ENDED.name,
                    "durationSeconds" to durationSeconds.coerceIn(0L, 86_400L),
                    "summaryNotes" to cleanNotes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (room.appointmentId.isNotBlank()) {
                batch.update(
                    db.collection("appointments").document(room.appointmentId),
                    mapOf(
                        "status" to AppointmentStatus.COMPLETED.name,
                        "notes" to cleanNotes,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeRoomMessages(roomId: String, userId: String): Flow<List<TelemedicineMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "telemedicineRooms/$roomId/messages"
        emitAll(
            db.collection("telemedicineRooms").document(roomId).collection("messages")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TelemedicineMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun sendRoomMessage(
        room: TelemedicineRoom,
        senderName: String,
        senderRole: String,
        text: String
    ): Result<Unit> {
        val path = "telemedicineRooms/${room.id}/messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(1000)
            require(cleanText.isNotEmpty()) { "Message cannot be empty." }
            val msgRef = db.collection("telemedicineRooms").document(room.id)
                .collection("messages").document()
            msgRef.set(
                mapOf(
                    "roomId" to room.id,
                    "participantUids" to room.participantUids.distinct().take(2),
                    "senderUid" to uid,
                    "senderName" to senderName.trim().take(80).ifBlank { "Participant" },
                    "senderRole" to if (senderRole == "DOCTOR") "DOCTOR" else "PATIENT",
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private fun logStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val toLog = firestoreError ?: (error as? Exception)
        if (toLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(toLog, operationType, path)
        }
    }
}
