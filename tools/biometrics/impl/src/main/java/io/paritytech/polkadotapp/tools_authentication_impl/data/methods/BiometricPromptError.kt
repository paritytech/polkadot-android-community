package io.paritytech.polkadotapp.tools_authentication_impl.data.methods

import androidx.biometric.BiometricPrompt
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationCancelledException
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationFailedException

private val CANCEL_ERROR_CODES = setOf(
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_CANCELED,
)

internal fun biometricPromptError(errorCode: Int, errString: CharSequence): Exception {
    val message = "$errorCode: $errString"

    return if (errorCode in CANCEL_ERROR_CODES) {
        AuthenticationCancelledException(message)
    } else {
        AuthenticationFailedException(message)
    }
}
