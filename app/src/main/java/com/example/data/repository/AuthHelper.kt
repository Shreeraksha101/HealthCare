package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val AUTH_TAG = "Auth"
private val INDIA_MOBILE_REGEX = Regex("^[6-9]\\d{9}$")

sealed interface PhoneValidationResult {
    data class Valid(val e164Phone: String, val formattedLocal: String) : PhoneValidationResult
    data class Invalid(val errorMessage: String) : PhoneValidationResult
}

fun validateIndianMobileNumber(rawInput: String): PhoneValidationResult {
    val digits = rawInput.filter(Char::isDigit)
    return when {
        digits.isEmpty() -> PhoneValidationResult.Invalid("Please enter your 10-digit mobile number.")
        digits.length != 10 -> PhoneValidationResult.Invalid("Mobile number must be exactly 10 digits.")
        !INDIA_MOBILE_REGEX.matches(digits) -> PhoneValidationResult.Invalid(
            "Enter a valid Indian mobile number starting with 6, 7, 8, or 9."
        )
        else -> PhoneValidationResult.Valid(
            e164Phone = "+91$digits",
            formattedLocal = "${digits.substring(0, 5)} ${digits.substring(5)}"
        )
    }
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

data class PhoneAuthError(
    val message: String,
    val isProviderDisabled: Boolean = false
)

fun sendPhoneOtp(
    context: Context,
    e164Phone: String,
    resendToken: PhoneAuthProvider.ForceResendingToken?,
    scope: CoroutineScope,
    onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
    onAutoVerified: () -> Unit,
    onError: (PhoneAuthError) -> Unit
) {
    val activity = context as? Activity
    if (activity == null) {
        onError(PhoneAuthError("Phone verification requires an Activity context."))
        return
    }

    Firebase.auth.useAppLanguage()

    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            scope.launch {
                try {
                    Firebase.auth.signInWithCredential(credential).await()
                    onAutoVerified()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val mapped = mapPhoneAuthError(e)
                    Log.w(AUTH_TAG, "Phone auto-verification sign-in did not complete: ${mapped.message}")
                    onError(mapped)
                }
            }
        }

        override fun onVerificationFailed(e: FirebaseException) {
            val mapped = mapPhoneAuthError(e)
            if (mapped.isProviderDisabled) {
                Log.w(AUTH_TAG, "Phone Auth provider disabled in Firebase Console (${e.message})")
            } else {
                Log.w(AUTH_TAG, "Phone verification failed: ${e.message}")
            }
            onError(mapped)
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            Log.d(AUTH_TAG, "OTP code sent for verificationId=${verificationId.take(8)}...")
            onCodeSent(verificationId, token)
        }

        override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
            Log.d(AUTH_TAG, "SMS auto-retrieval timed out; manual 6-digit OTP entry remains active.")
        }
    }

    val optionsBuilder = PhoneAuthOptions.newBuilder(Firebase.auth)
        .setPhoneNumber(e164Phone)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(callbacks)

    if (resendToken != null) {
        optionsBuilder.setForceResendingToken(resendToken)
    }

    try {
        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val mapped = mapPhoneAuthError(e)
        Log.w(AUTH_TAG, "Phone verification request failed immediately: ${mapped.message}")
        onError(mapped)
    }
}

fun verifyPhoneOtp(
    verificationId: String,
    otpCode: String,
    scope: CoroutineScope,
    onSuccess: () -> Unit,
    onError: (PhoneAuthError) -> Unit
) {
    val cleanOtp = otpCode.filter(Char::isDigit)
    if (cleanOtp.length != 6) {
        onError(PhoneAuthError("Please enter the complete 6-digit OTP."))
        return
    }

    scope.launch {
        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, cleanOtp)
            Firebase.auth.signInWithCredential(credential).await()
            onSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val mapped = mapPhoneAuthError(e)
            Log.w(AUTH_TAG, "OTP verification failed: ${mapped.message}")
            onError(mapped)
        }
    }
}

private fun mapPhoneAuthError(error: Exception): PhoneAuthError = when (error) {
    is FirebaseAuthInvalidCredentialsException -> when (error.errorCode) {
        "ERROR_INVALID_VERIFICATION_CODE" -> PhoneAuthError(
            "Invalid 6-digit OTP. Please check the SMS code and try again."
        )
        "ERROR_SESSION_EXPIRED" -> PhoneAuthError(
            "This OTP has expired. Please tap Resend OTP to receive a new code."
        )
        "ERROR_INVALID_PHONE_NUMBER" -> PhoneAuthError(
            "Invalid mobile number format. Enter a valid 10-digit +91 number."
        )
        else -> PhoneAuthError(error.localizedMessage ?: "Invalid verification code or phone number.")
    }
    is FirebaseTooManyRequestsException -> PhoneAuthError(
        "SMS delivery temporarily blocked due to too many requests. Please wait and try again or continue with Google."
    )
    is FirebaseAuthException -> when (error.errorCode) {
        "ERROR_OPERATION_NOT_ALLOWED" -> PhoneAuthError(
            message = "Phone Sign-In is disabled on Firebase project gen-lang-client-0432714215. Enable Authentication → Sign-in method → Phone in the Firebase Console, or sign in with Google below.",
            isProviderDisabled = true
        )
        "ERROR_APP_NOT_AUTHORIZED", "ERROR_INVALID_APP_CREDENTIAL", "ERROR_MISSING_CLIENT_IDENTIFIER" -> PhoneAuthError(
            message = "SMS app verification could not be completed. Verify Phone Sign-In, Play Integrity / reCAPTCHA, and SHA fingerprints in Firebase Console, or continue with Google.",
            isProviderDisabled = true
        )
        "ERROR_QUOTA_EXCEEDED" -> PhoneAuthError(
            "Daily SMS OTP quota exceeded for this Firebase project. Try again later or continue with Google."
        )
        "ERROR_CAPTCHA_CHECK_FAILED" -> PhoneAuthError(
            "Security verification (reCAPTCHA) was canceled or failed. Please try Send OTP again."
        )
        else -> PhoneAuthError(error.localizedMessage ?: "SMS OTP delivery failed. Please try again.")
    }
    else -> PhoneAuthError(error.localizedMessage ?: "Unable to deliver OTP. Check your network and try again.")
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val activity = context as? Activity
                ?: throw IllegalStateException("Sign-In requires an Activity context.")
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
