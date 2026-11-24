package io.paritytech.polkadotapp.tools_authentication_impl.data.methods

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.paritytech.polkadotapp.common.presentation.navigation.OverlayCoordinator
import io.paritytech.polkadotapp.common.presentation.resources.ContextManager
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationCancelledException
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationFailedException
import io.paritytech.polkadotapp.tools_authentication_impl.data.AuthenticationMethod
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import io.paritytech.polkadotapp.common.R as RCommon

class BiometricPromptAuthenticationMethod @Inject constructor(
    private val contextManager: ContextManager,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val overlayCoordinator: OverlayCoordinator,
) : AuthenticationMethod {
    companion object {
        private const val ALLOWED_BIOMETRICS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }

    override suspend fun authenticateUser(): Result<Unit> {
        val activity = contextManager.getActivity() as? FragmentActivity
            ?: return Result.failure(AuthenticationFailedException("No activity attached to show the prompt"))

        return overlayCoordinator.forbidOverlays().use {
            authenticateUseInternal(activity)
        }
    }

    private suspend fun authenticateUseInternal(activity: FragmentActivity): Result<Unit> {
        val biometricManager = BiometricManager.from(activity)

        val context = contextManager.applicationContext

        return biometricManager.prepareForAuthentication(activity).flatMap {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(RCommon.string.authentication_title))
                .setDescription(context.getString(RCommon.string.authentication_description))
                .setAllowedAuthenticators(ALLOWED_BIOMETRICS)
                .build()

            promptInfo.authenticate(activity)
        }
    }

    private suspend fun BiometricPrompt.PromptInfo.authenticate(activity: FragmentActivity): Result<Unit> {
        return withContext(coroutineDispatchers.main) {
            suspendCancellableCoroutine { continuation ->
                if (activity.supportFragmentManager.isStateSaved) {
                    val exception = AuthenticationCancelledException("BiometricPrompt drops a request made after onSaveInstanceState")
                    continuation.resume(Result.failure(exception))
                    return@suspendCancellableCoroutine
                }

                val biometricPrompt = BiometricPrompt(
                    activity, ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            continuation.resume(Result.failure(biometricPromptError(errorCode, errString)))
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            continuation.resume(Result.success(Unit))
                        }
                    })

                continuation.invokeOnCancellation {
                    biometricPrompt.cancelAuthentication()
                }

                biometricPrompt.authenticate(this@authenticate)
            }
        }
    }

    private suspend fun BiometricManager.prepareForAuthentication(activity: FragmentActivity): Result<Unit> {
        return when (canAuthenticate(ALLOWED_BIOMETRICS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Result.success(Unit)

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                Result.failure(AuthenticationFailedException("No matching hardware found to perform authentication"))

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                Result.failure(AuthenticationFailedException("Hardware to perform authentication is currently unavailable. Try again later."))

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                BiometricExecutor(activity).execute()
            }

            else -> Result.failure(AuthenticationFailedException("Unknown authentication hardware state"))
        }
    }
}
