package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.repository.PhoneValidationResult
import com.example.data.repository.attemptAutoSignIn
import com.example.data.repository.onGoogleSignInClicked
import com.example.data.repository.sendPhoneOtp
import com.example.data.repository.validateIndianMobileNumber
import com.example.data.repository.verifyPhoneOtp
import com.example.ui.theme.HealthCareTheme
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessEmerald
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay

private enum class AuthMethodStep {
    PHONE_ENTRY,
    OTP_VERIFICATION
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    HealthCareTheme(darkTheme = false) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember(context) { CredentialManager.create(context) }

        var authStep by remember { mutableStateOf(AuthMethodStep.PHONE_ENTRY) }
        var mobileInput by remember { mutableStateOf("") }
        var mobileFieldError by remember { mutableStateOf<String?>(null) }

        var otpCode by remember { mutableStateOf("") }
        var formattedTargetPhone by remember { mutableStateOf("") }
        var verificationId by remember { mutableStateOf<String?>(null) }
        var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }

        var resendCountdownSeconds by remember { mutableIntStateOf(0) }
        var countdownTrigger by remember { mutableIntStateOf(0) }

        var isSendingOtp by remember { mutableStateOf(false) }
        var isVerifyingOtp by remember { mutableStateOf(false) }
        var isGoogleLoading by remember { mutableStateOf(false) }
        var authErrorBanner by remember { mutableStateOf<String?>(null) }
        var otpSentBanner by remember { mutableStateOf<String?>(null) }
        var isPhoneProviderDisabled by remember { mutableStateOf(false) }
        var activeLegalTopic by remember { mutableStateOf<LegalPolicyTopic?>(null) }

        val anyLoading = isSendingOtp || isVerifyingOtp || isGoogleLoading

        fun triggerGoogleSignIn() {
            authErrorBanner = null
            isPhoneProviderDisabled = false
            isGoogleLoading = true
            onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isGoogleLoading = false
                    onAuthSuccess()
                },
                onAuthError = { msg ->
                    isGoogleLoading = false
                    authErrorBanner = msg
                },
                scope = scope,
                onAuthCancelled = {
                    isGoogleLoading = false
                }
            )
        }

        LaunchedEffect(Unit) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = {},
                scope = scope
            )
        }

        LaunchedEffect(countdownTrigger, authStep) {
            if (authStep == AuthMethodStep.OTP_VERIFICATION && resendCountdownSeconds > 0) {
                while (resendCountdownSeconds > 0) {
                    delay(1000L)
                    resendCountdownSeconds -= 1
                }
            }
        }

        fun triggerSendOtp(isResend: Boolean = false) {
            authErrorBanner = null
            otpSentBanner = null
            isPhoneProviderDisabled = false
            val validation = validateIndianMobileNumber(mobileInput)
            if (validation is PhoneValidationResult.Invalid) {
                mobileFieldError = validation.errorMessage
                authErrorBanner = validation.errorMessage
                return
            }
            val valid = validation as PhoneValidationResult.Valid
            mobileFieldError = null
            formattedTargetPhone = "+91 ${valid.formattedLocal}"
            isSendingOtp = true

            sendPhoneOtp(
                context = context,
                e164Phone = valid.e164Phone,
                resendToken = if (isResend) resendToken else null,
                scope = scope,
                onCodeSent = { vId, token ->
                    isSendingOtp = false
                    isPhoneProviderDisabled = false
                    verificationId = vId
                    resendToken = token
                    otpCode = ""
                    otpSentBanner = if (isResend) {
                        "A new 6-digit OTP was sent to +91 ${valid.formattedLocal}."
                    } else {
                        "OTP sent successfully to +91 ${valid.formattedLocal}."
                    }
                    authStep = AuthMethodStep.OTP_VERIFICATION
                    resendCountdownSeconds = 60
                    countdownTrigger += 1
                },
                onAutoVerified = {
                    isSendingOtp = false
                    isPhoneProviderDisabled = false
                    onAuthSuccess()
                },
                onError = { phoneError ->
                    isSendingOtp = false
                    authErrorBanner = phoneError.message
                    isPhoneProviderDisabled = phoneError.isProviderDisabled
                }
            )
        }

        fun triggerVerifyOtp() {
            authErrorBanner = null
            val vId = verificationId
            if (vId.isNullOrBlank()) {
                authErrorBanner = "Verification session expired. Please request a new OTP."
                return
            }
            if (otpCode.length != 6) {
                authErrorBanner = "Please enter the complete 6-digit OTP."
                return
            }
            isVerifyingOtp = true
            verifyPhoneOtp(
                verificationId = vId,
                otpCode = otpCode,
                scope = scope,
                onSuccess = {
                    isVerifyingOtp = false
                    isPhoneProviderDisabled = false
                    onAuthSuccess()
                },
                onError = { phoneError ->
                    isVerifyingOtp = false
                    authErrorBanner = phoneError.message
                    isPhoneProviderDisabled = phoneError.isProviderDisabled
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        )
                    )
                )
                .systemBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HealthAndSafety,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "HealthCare Companion",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Image(
                    painter = painterResource(id = R.drawable.img_health_hero),
                    contentDescription = "HealthCare Companion care banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(22.dp))
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = stringResource(R.string.auth_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.auth_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Authentication Card (Mobile OTP + Google)
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (authErrorBanner != null) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .testTag("auth_error_banner"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = authErrorBanner!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    if (isPhoneProviderDisabled) {
                                        Button(
                                            onClick = { triggerGoogleSignIn() },
                                            enabled = !anyLoading,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Continue with Google Instead")
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = authStep,
                            label = "mobile_auth_step"
                        ) { step ->
                            if (step == AuthMethodStep.PHONE_ENTRY) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PhoneAndroid,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Mobile Number Login or Sign Up",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    OutlinedTextField(
                                        value = mobileInput,
                                        onValueChange = { raw ->
                                            mobileInput = raw.filter(Char::isDigit).take(10)
                                            mobileFieldError = null
                                            authErrorBanner = null
                                        },
                                        label = { Text("10-Digit Mobile Number") },
                                        placeholder = { Text("98765 43210") },
                                        leadingIcon = {
                                            Box(modifier = Modifier.padding(start = 8.dp)) {
                                                CountryCodePrefixChip(code = "+91", countryLabel = "IN")
                                            }
                                        },
                                        isError = mobileFieldError != null,
                                        supportingText = mobileFieldError?.let { err ->
                                            { Text(err) }
                                        } ?: {
                                            Text("We'll send a 6-digit SMS verification code.")
                                        },
                                        singleLine = true,
                                        enabled = !anyLoading,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Phone,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = { triggerSendOtp(isResend = false) }
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("mobile_number_input")
                                    )

                                    Button(
                                        onClick = { triggerSendOtp(isResend = false) },
                                        enabled = !anyLoading,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("send_otp_button")
                                    ) {
                                        if (isSendingOtp) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.5.dp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Sending OTP...")
                                        } else {
                                            Text(
                                                text = "Send OTP",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.VerifiedUser,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Enter 6-Digit OTP",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                authStep = AuthMethodStep.PHONE_ENTRY
                                                otpCode = ""
                                                otpSentBanner = null
                                                authErrorBanner = null
                                            },
                                            enabled = !anyLoading,
                                            modifier = Modifier.testTag("change_number_button")
                                        ) {
                                            Icon(
                                                Icons.Rounded.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Change")
                                        }
                                    }

                                    if (otpSentBanner != null) {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = SuccessContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = otpSentBanner!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = SuccessEmerald,
                                                modifier = Modifier
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                                    .testTag("otp_sent_banner")
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Verification code sent to $formattedTargetPhone",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    OtpSixDigitInput(
                                        otpValue = otpCode,
                                        isError = authErrorBanner != null,
                                        enabled = !anyLoading,
                                        onOtpChange = { updated ->
                                            otpCode = updated
                                            authErrorBanner = null
                                        },
                                        onDone = { triggerVerifyOtp() }
                                    )

                                    Button(
                                        onClick = { triggerVerifyOtp() },
                                        enabled = !anyLoading && otpCode.length == 6,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("verify_otp_button")
                                    ) {
                                        if (isVerifyingOtp) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.5.dp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Verifying...")
                                        } else {
                                            Text(
                                                text = "Verify & Sign In",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (resendCountdownSeconds > 0) {
                                            Text(
                                                text = "Resend OTP in ${resendCountdownSeconds}s",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.testTag("resend_otp_countdown")
                                            )
                                        } else {
                                            TextButton(
                                                onClick = { triggerSendOtp(isResend = true) },
                                                enabled = !anyLoading,
                                                modifier = Modifier.testTag("resend_otp_button")
                                            ) {
                                                Text(
                                                    text = if (isSendingOtp) "Resending..." else "Resend OTP",
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // OR Divider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "OR",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                            )
                        }

                        // Secondary Option: Google Sign-In
                        OutlinedButton(
                            onClick = { triggerGoogleSignIn() },
                            enabled = !anyLoading,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("google_sign_in_button")
                        ) {
                            if (isGoogleLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Connecting Google...")
                            } else {
                                GoogleBrandMark()
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.auth_google_button),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Privacy Policy & Terms of Service Section
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.auth_privacy_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "By continuing, you agree to our ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Terms of Service",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .clickable { activeLegalTopic = LegalPolicyTopic.TERMS_OF_SERVICE }
                                    .testTag("terms_of_service_link")
                            )
                            Text(
                                text = " & ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Privacy Policy",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .clickable { activeLegalTopic = LegalPolicyTopic.PRIVACY_POLICY }
                                    .testTag("privacy_policy_link")
                            )
                        }
                    }
                }
            }
        }

        val topic = activeLegalTopic
        if (topic != null) {
            LegalPolicyDialog(
                topic = topic,
                onDismiss = { activeLegalTopic = null }
            )
        }
    }
}
