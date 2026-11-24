package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.model.RoomStatus
import com.example.data.model.TelemedicineRoom
import com.example.ui.components.DoctorAvatar
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.TelemedicineCallViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCallScreen(
    currentUserId: String,
    currentUserName: String,
    viewModel: TelemedicineCallViewModel,
    onCallExited: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val roomState by viewModel.roomState.collectAsStateWithLifecycle()
    val messages by viewModel.messagesState.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val micOn by viewModel.localMicOn.collectAsStateWithLifecycle()
    val camOn by viewModel.localCamOn.collectAsStateWithLifecycle()
    val useFrontCamera by viewModel.useFrontCamera.collectAsStateWithLifecycle()
    val speakerOn by viewModel.speakerOn.collectAsStateWithLifecycle()
    val callEnded by viewModel.callEnded.collectAsStateWithLifecycle()

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPerm = results[Manifest.permission.CAMERA] == true || hasCameraPerm
        hasAudioPerm = results[Manifest.permission.RECORD_AUDIO] == true || hasAudioPerm
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm || !hasAudioPerm) {
            permLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    // Configure Android AudioManager speakerphone for voice consultation
    DisposableEffect(speakerOn) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val prevMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        runCatching {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = speakerOn
        }
        onDispose {
            runCatching {
                audioManager?.mode = prevMode
            }
        }
    }

    var showChatSheet by remember { mutableStateOf(false) }
    var showEndDialog by remember { mutableStateOf(false) }
    var summaryNotes by remember { mutableStateOf("") }
    var chatDraft by remember { mutableStateOf("") }

    val room = (roomState as? UiState.Success)?.data
    LaunchedEffect(room?.id) {
        if (room != null) {
            viewModel.startSessionIfNeeded(room)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A1924), Color(0xFF0F2938), Color(0xFF07131C))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when (val state = roomState) {
            is UiState.Loading -> LoadingStateBox(modifier = Modifier.align(Alignment.Center))
            is UiState.Error -> Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                ErrorStateCard(message = state.message, onRetry = onCallExited)
            }
            is UiState.Success -> {
                val activeRoom = state.data
                if (activeRoom == null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Telemedicine room not found.",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(onClick = onCallExited) { Text("Back to Visits") }
                    }
                } else {
                    val isDoctorView = activeRoom.doctorOwnerUid == currentUserId &&
                        activeRoom.patientId != currentUserId
                    val remoteName = if (isDoctorView) activeRoom.patientName else activeRoom.doctorName
                    val remoteRole = if (isDoctorView) "Patient" else "Attending Physician"

                    // Main Remote Video Stage
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Encrypted Status & Timer Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                text = "Encrypted Room",
                                containerColor = Color(0xFF12384A),
                                contentColor = Color(0xFF7DD3FC),
                                icon = Icons.Rounded.Lock
                            )
                            Surface(
                                color = Color(0xFF12384A),
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (activeRoom.parsedStatus == RoomStatus.ENDED) EmergencyRed
                                                else SuccessEmerald
                                            )
                                    )
                                    Text(
                                        text = formatElapsed(elapsedSeconds),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Remote Participant Avatar & Live Status
                        if (isDoctorView) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF173D52)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = remoteName,
                                    tint = Color(0xFF7DD3FC),
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        } else {
                            DoctorAvatar(
                                avatarKey = "female",
                                contentDescription = remoteName,
                                size = 128.dp
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = remoteName,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$remoteRole • ${activeRoom.parsedStatus.label}",
                            color = Color(0xFFBAE6FD),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (!hasCameraPerm || !hasAudioPerm) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                color = Color(0xFF1F3B4D),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.clickable {
                                    permLauncher.launch(
                                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                    )
                                }
                            ) {
                                Text(
                                    text = "Tap to grant Camera & Microphone permissions for live video",
                                    color = Color(0xFF7DD3FC),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Bottom Controls Bar
                        Surface(
                            color = Color(0xFF102432).copy(alpha = 0.92f),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CallControlButton(
                                    icon = if (micOn) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                    label = if (micOn) "Mute" else "Unmute",
                                    active = micOn,
                                    onClick = { viewModel.toggleMic(activeRoom) },
                                    modifier = Modifier.testTag("call_toggle_mic")
                                )
                                CallControlButton(
                                    icon = if (camOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                                    label = if (camOn) "Cam On" else "Cam Off",
                                    active = camOn,
                                    onClick = { viewModel.toggleCam(activeRoom) },
                                    modifier = Modifier.testTag("call_toggle_cam")
                                )
                                CallControlButton(
                                    icon = Icons.Rounded.Cameraswitch,
                                    label = "Flip",
                                    active = true,
                                    onClick = { viewModel.flipCamera() },
                                    modifier = Modifier.testTag("call_flip_cam")
                                )
                                CallControlButton(
                                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                                    label = if (speakerOn) "Speaker" else "Earpiece",
                                    active = speakerOn,
                                    onClick = { viewModel.toggleSpeaker() }
                                )
                                BadgedBox(
                                    badge = {
                                        if (messages.isNotEmpty()) {
                                            Badge { Text("${messages.size}") }
                                        }
                                    }
                                ) {
                                    CallControlButton(
                                        icon = Icons.AutoMirrored.Rounded.Chat,
                                        label = "Chat",
                                        active = true,
                                        onClick = { showChatSheet = true },
                                        modifier = Modifier.testTag("call_open_chat")
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(EmergencyRed)
                                            .clickable { showEndDialog = true }
                                            .testTag("call_end_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CallEnd,
                                            contentDescription = "End video call",
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("End", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Local CameraX Picture-in-Picture Preview (Top-Right)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 64.dp, end = 20.dp)
                            .size(width = 108.dp, height = 148.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1B3344)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPerm && camOn && !callEnded) {
                            CameraXPreviewBox(
                                useFrontCamera = useFrontCamera,
                                lifecycleOwner = lifecycleOwner,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.VideocamOff,
                                    contentDescription = "Camera off",
                                    tint = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = "You",
                                    color = Color(0xFF94A3B8),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChatSheet && room != null) {
        ModalBottomSheet(onDismissRequest = { showChatSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .padding(16.dp)
            ) {
                Text(
                    "In-Call Secure Notes & Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Text(
                                "Share symptoms, pharmacy preferences, or follow-up notes during this consultation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(messages, key = { it.id }) { msg ->
                            val isMine = msg.senderUid == currentUserId
                            Surface(
                                color = if (isMine) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${msg.senderName} (${msg.senderRole})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(text = msg.text, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chatDraft,
                        onValueChange = { chatDraft = it },
                        placeholder = { Text("Send message to consultation...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (chatDraft.isNotBlank()) {
                                viewModel.sendMessage(room, currentUserName, chatDraft)
                                chatDraft = ""
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send in-call message")
                    }
                }
            }
        }
    }

    if (showEndDialog && room != null) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("End Video Consultation?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Duration: ${formatElapsed(elapsedSeconds)}. Add optional consultation notes or next steps below:"
                    )
                    OutlinedTextField(
                        value = summaryNotes,
                        onValueChange = { summaryNotes = it },
                        label = { Text("Consultation Summary Notes") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.endCall(room, summaryNotes) {
                            showEndDialog = false
                            onCallExited()
                        }
                    }
                ) {
                    Text("Complete & Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) { Text("Resume Call") }
            }
        )
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (active) Color(0xFF1D3B4E) else Color(0xFF334155))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color(0xFFE2E8F0), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CameraXPreviewBox(
    useFrontCamera: Boolean,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(useFrontCamera) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
            }.onFailure { err ->
                Log.w("VideoCallScreen", "CameraX preview unavailable on this device: ${err.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

private fun formatElapsed(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
