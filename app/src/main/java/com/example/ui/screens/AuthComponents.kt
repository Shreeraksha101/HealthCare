package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OtpSixDigitInput(
    otpValue: String,
    isError: Boolean,
    enabled: Boolean,
    onOtpChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                focusRequester.requestFocus()
            },
        contentAlignment = Alignment.Center
    ) {
        // Hidden accessible text field captures numeric & SMS autofill input
        BasicTextField(
            value = otpValue,
            onValueChange = { raw ->
                val filtered = raw.filter(Char::isDigit).take(6)
                onOtpChange(filtered)
            },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .alpha(0.01f)
                .focusRequester(focusRequester)
                .testTag("otp_code_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            for (index in 0 until 6) {
                val char = otpValue.getOrNull(index)?.toString() ?: ""
                val isCurrentBox = index == otpValue.length.coerceAtMost(5)
                val borderColor = when {
                    isError -> MaterialTheme.colorScheme.error
                    char.isNotEmpty() -> MaterialTheme.colorScheme.primary
                    isCurrentBox -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                }
                val bgColor = when {
                    char.isNotEmpty() -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bgColor)
                        .border(
                            width = if (char.isNotEmpty() || isCurrentBox) 1.8.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun CountryCodePrefixChip(
    code: String = "+91",
    countryLabel: String = "IN"
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = countryLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = code,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun GoogleBrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val strokeWidth = size.minDimension * 0.2f
        val inset = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 215f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 140f,
            sweepAngle = 75f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 35f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -5f,
            sweepAngle = 45f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.52f, size.height * 0.5f),
            end = Offset(size.width - inset, size.height * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

enum class LegalPolicyTopic(val title: String) {
    PRIVACY_POLICY("Privacy Policy"),
    TERMS_OF_SERVICE("Terms of Service")
}

@Composable
fun LegalPolicyDialog(
    topic: LegalPolicyTopic,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(topic.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (topic == LegalPolicyTopic.PRIVACY_POLICY) {
                    Text(
                        text = "1. Account & Identity Protection",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Your mobile number or Google account authenticates your session through Firebase Authentication. Only your authenticated account can read or update your personal appointments, medicine schedules, and medical reports.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Medical Records & Vault Privacy",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Uploaded PDFs and medical report images are stored privately inside your app-sandboxed vault and linked strictly to your user ID. HealthCare Companion never shares or sells personal health data.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Telemedicine & AI Assistant",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Telemedicine consultations and in-call messages are restricted to the patient and attending doctor. AI Health Guide questions are processed strictly to explain terms and wellness guidance.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = "1. Informational & Care Coordination Use",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "HealthCare Companion helps you organize appointments, telemedicine video consultations, medication reminders, and medical documents. The AI Health Guide provides general health information only and never diagnoses diseases or replaces a licensed physician.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Emergency Situations",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "If you experience chest pain, severe breathing difficulty, sudden weakness, or another medical emergency, call emergency services immediately or visit the nearest emergency room.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Account Security",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Keep your mobile OTP codes and Google credentials confidential. You are responsible for ensuring your medication schedules and emergency contacts are kept accurate.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
