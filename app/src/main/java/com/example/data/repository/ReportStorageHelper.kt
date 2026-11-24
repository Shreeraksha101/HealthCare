package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class StoredReportFile(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localRelativePath: String
)

class ReportStorageHelper(private val appContext: Context) {

    suspend fun copyUriToPrivateStorage(userId: String, sourceUri: Uri): StoredReportFile =
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            val mimeType = resolver.getType(sourceUri) ?: "application/octet-stream"
            val originalName = queryDisplayName(sourceUri) ?: "report_${System.currentTimeMillis()}"
            val safeName = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val uniqueName = "${UUID.randomUUID().toString().take(8)}_$safeName"

            val userDir = File(appContext.filesDir, "reports/$userId")
            if (!userDir.exists()) {
                userDir.mkdirs()
            }
            val destFile = File(userDir, uniqueName)

            resolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to read selected file.")

            StoredReportFile(
                fileName = originalName,
                mimeType = mimeType,
                sizeBytes = destFile.length(),
                localRelativePath = "reports/$userId/$uniqueName"
            )
        }

    fun deleteLocalFile(localRelativePath: String) {
        if (localRelativePath.isBlank()) return
        val file = File(appContext.filesDir, localRelativePath)
        if (file.exists()) {
            file.delete()
        }
    }

    fun getFile(localRelativePath: String): File? {
        if (localRelativePath.isBlank()) return null
        val file = File(appContext.filesDir, localRelativePath)
        return if (file.exists()) file else null
    }

    fun createViewIntent(localRelativePath: String, mimeType: String): Intent? {
        val file = getFile(localRelativePath) ?: return null
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createShareIntent(localRelativePath: String, mimeType: String, title: String): Intent? {
        val file = getFile(localRelativePath) ?: return null
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share Medical Report")
    }

    private fun queryDisplayName(uri: Uri): String? {
        return appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else null
        }
    }
}
