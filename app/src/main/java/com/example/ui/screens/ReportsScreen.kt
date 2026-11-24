package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.model.MedicalReport
import com.example.data.model.ReportCategory
import com.example.data.repository.ReportStorageHelper
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.viewmodel.CareHubViewModel
import kotlinx.coroutines.launch

@Composable
fun ReportsScreen(
    viewModel: CareHubViewModel,
    reportStorageHelper: ReportStorageHelper
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reportsState by viewModel.reportsState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedCategory by remember { mutableStateOf("All") }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pendingUri = uri
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingUri = uri
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 860.dp)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Medical Reports Vault", style = MaterialTheme.typography.headlineMedium)

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Private & scoped to your Google account. Files stay sandboxed inside app storage.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { pdfPicker.launch(arrayOf("application/pdf", "image/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("upload_pdf_button")
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload PDF")
                        }
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("upload_image_button")
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Photo")
                        }
                    }

                    val categories = listOf("All") + ReportCategory.entries.map { it.displayName }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }

                when (val state = reportsState) {
                    is UiState.Loading -> LoadingStateBox()
                    is UiState.Error -> Box(modifier = Modifier.padding(16.dp)) {
                        ErrorStateCard(message = state.message)
                    }
                    is UiState.Success -> {
                        val filtered = if (selectedCategory == "All") {
                            state.data
                        } else {
                            state.data.filter { it.category == selectedCategory }
                        }

                        if (filtered.isEmpty()) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                EmptyStateCard(
                                    icon = Icons.Rounded.Description,
                                    title = "No medical reports yet",
                                    subtitle = "Upload blood tests, scans, prescriptions, or discharge summaries to organize them by date."
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filtered, key = { it.id }) { report ->
                                    MedicalReportCard(
                                        report = report,
                                        onView = {
                                            val viewIntent = reportStorageHelper.createViewIntent(
                                                report.localRelativePath,
                                                report.mimeType
                                            )
                                            if (viewIntent != null) {
                                                runCatching { context.startActivity(viewIntent) }
                                                    .onFailure {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("No viewer app found for ${report.mimeType}")
                                                        }
                                                    }
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Report file unavailable on this device.")
                                                }
                                            }
                                        },
                                        onShare = {
                                            val shareIntent = reportStorageHelper.createShareIntent(
                                                report.localRelativePath,
                                                report.mimeType,
                                                report.title
                                            )
                                            if (shareIntent != null) {
                                                context.startActivity(shareIntent)
                                            }
                                        },
                                        onDelete = { viewModel.deleteReport(report) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val picked = pendingUri
    if (picked != null) {
        SaveReportDialog(
            defaultDate = viewModel.todayIso,
            isSaving = isSaving,
            onDismiss = { pendingUri = null },
            onSave = { title, category, date, notes ->
                viewModel.uploadReport(title, category, date, picked, notes) {
                    pendingUri = null
                }
            }
        )
    }
}

@Composable
private fun MedicalReportCard(
    report: MedicalReport,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val isPdf = report.mimeType.contains("pdf", ignoreCase = true)
    val sizeKb = (report.fileSizeBytes / 1024L).coerceAtLeast(1L)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isPdf) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Image,
                        contentDescription = null,
                        tint = if (isPdf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(report.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${report.category} • ${report.reportDate} • ${sizeKb} KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete report",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (report.notes.isNotBlank()) {
                Text(
                    report.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onView, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View File")
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export / Share")
                }
            }
        }
    }
}

@Composable
private fun SaveReportDialog(
    defaultDate: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ReportCategory.LAB_RESULTS.displayName) }
    var reportDate by remember { mutableStateOf(defaultDate) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Medical Report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Report Title (e.g. Complete Blood Count)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat.displayName,
                            onClick = { category = cat.displayName },
                            label = { Text(cat.displayName) }
                        )
                    }
                }
                OutlinedTextField(
                    value = reportDate,
                    onValueChange = { reportDate = it },
                    label = { Text("Report Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, category, reportDate, notes) },
                enabled = !isSaving && title.trim().isNotEmpty()
            ) {
                Text(if (isSaving) "Saving..." else "Save Privately")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
