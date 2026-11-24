package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.DoseLog
import com.example.data.model.DoseStatus
import com.example.data.model.MedicalReport
import com.example.data.model.Medicine
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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

class UserAndCareRepository(
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

    // --- USER PROFILE ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user profile observation." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val path = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            check(uid == user.uid)
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to (
                        user.displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
                            ?: user.phoneNumber?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
                            ?: "Patient"
                        ),
                    "email" to (user.email?.take(120) ?: ""),
                    "photoUrl" to user.photoUrl?.toString()?.take(500),
                    "bloodGroup" to "",
                    "allergies" to "",
                    "emergencyContactName" to "",
                    "emergencyContactPhone" to "",
                    "dateOfBirth" to "",
                    "providerMode" to false,
                    "notificationsEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        displayName: String,
        bloodGroup: String,
        allergies: String,
        emergencyContactName: String,
        emergencyContactPhone: String,
        dateOfBirth: String,
        providerMode: Boolean,
        notificationsEnabled: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid"
        return try {
            val verifiedUid = requireUserId()
            val cleanName = displayName.trim().take(80).ifBlank { "Patient" }
            val payload = mapOf(
                "displayName" to cleanName,
                "bloodGroup" to bloodGroup.trim().take(12),
                "allergies" to allergies.trim().take(300),
                "emergencyContactName" to emergencyContactName.trim().take(80),
                "emergencyContactPhone" to emergencyContactPhone.trim().take(30),
                "dateOfBirth" to dateOfBirth.trim().take(20),
                "providerMode" to providerMode,
                "notificationsEnabled" to notificationsEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(verifiedUid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- MEDICINES ---

    fun observeMedicines(userId: String): Flow<List<Medicine>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/medicines"
        emitAll(
            db.collection("users").document(uid).collection("medicines")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Medicine::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun addMedicine(
        name: String,
        dosage: String,
        instructions: String,
        scheduleTimes: List<String>,
        remindersEnabled: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/medicines"
        return try {
            val verifiedUid = requireUserId()
            require(name.trim().isNotEmpty()) { "Medicine name is required." }
            require(dosage.trim().isNotEmpty()) { "Dosage is required." }
            val cleanTimes = scheduleTimes.map { it.trim() }.filter { it.isNotEmpty() }.take(6)
            require(cleanTimes.isNotEmpty()) { "Select at least one reminder time." }

            val docRef = db.collection("users").document(verifiedUid).collection("medicines").document()
            val payload = mapOf(
                "userId" to verifiedUid,
                "name" to name.trim().take(100),
                "dosage" to dosage.trim().take(60),
                "instructions" to instructions.trim().take(200),
                "scheduleTimes" to cleanTimes,
                "remindersEnabled" to remindersEnabled,
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleMedicineReminder(medicineId: String, enabled: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/medicines/$medicineId"
        return try {
            val verifiedUid = requireUserId()
            db.collection("users").document(verifiedUid).collection("medicines").document(medicineId)
                .update(
                    mapOf(
                        "remindersEnabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMedicineScheduleTimes(
        medicineId: String,
        scheduleTimes: List<String>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/medicines/$medicineId"
        return try {
            val verifiedUid = requireUserId()
            val cleanTimes = scheduleTimes.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(6)
            require(cleanTimes.isNotEmpty()) { "Select at least one reminder time." }
            db.collection("users").document(verifiedUid).collection("medicines").document(medicineId)
                .update(
                    mapOf(
                        "scheduleTimes" to cleanTimes,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMedicine(medicineId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/medicines/$medicineId"
        return try {
            val verifiedUid = requireUserId()
            db.collection("users").document(verifiedUid).collection("medicines").document(medicineId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- DOSE LOGS ---

    fun observeDoseLogs(userId: String): Flow<List<DoseLog>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/doseLogs"
        emitAll(
            db.collection("users").document(uid).collection("doseLogs")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(DoseLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun setDoseStatus(
        medicine: Medicine,
        dateIso: String,
        scheduledTime: String,
        status: DoseStatus
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val slotKey = scheduledTime.replace(Regex("[^a-zA-Z0-9]"), "_")
        val logId = "${medicine.id}_${dateIso}_$slotKey".take(128)
        val path = "users/$uid/doseLogs/$logId"
        return try {
            val verifiedUid = requireUserId()
            val docRef = db.collection("users").document(verifiedUid).collection("doseLogs").document(logId)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "status" to status.name,
                        "medicineName" to medicine.name.take(100),
                        "dosage" to medicine.dosage.take(60),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to verifiedUid,
                        "medicineId" to medicine.id,
                        "medicineName" to medicine.name.take(100),
                        "dosage" to medicine.dosage.take(60),
                        "dateIso" to dateIso.take(20),
                        "scheduledTime" to scheduledTime.take(20),
                        "status" to status.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- MEDICAL REPORTS ---

    fun observeReports(userId: String): Flow<List<MedicalReport>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/reports"
        emitAll(
            db.collection("users").document(uid).collection("reports")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(MedicalReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.reportDate }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun addMedicalReport(
        title: String,
        category: String,
        reportDate: String,
        storedFile: StoredReportFile,
        notes: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/reports"
        return try {
            val verifiedUid = requireUserId()
            require(title.trim().isNotEmpty()) { "Report title is required." }
            val docRef = db.collection("users").document(verifiedUid).collection("reports").document()
            docRef.set(
                mapOf(
                    "userId" to verifiedUid,
                    "title" to title.trim().take(120),
                    "category" to category,
                    "reportDate" to reportDate.trim().take(20),
                    "fileName" to storedFile.fileName.take(160),
                    "mimeType" to storedFile.mimeType.take(80),
                    "fileSizeBytes" to storedFile.sizeBytes.coerceIn(0L, 52_428_800L),
                    "localRelativePath" to storedFile.localRelativePath.take(260),
                    "notes" to notes.trim().take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
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

    suspend fun deleteReport(reportId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/reports/$reportId"
        return try {
            val verifiedUid = requireUserId()
            db.collection("users").document(verifiedUid).collection("reports").document(reportId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
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
