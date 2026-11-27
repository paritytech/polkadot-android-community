package io.paritytech.polkadotapp.tools_authentication_impl.data.methods

import androidx.biometric.BiometricPrompt
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationCancelledException
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationFailedException
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPromptErrorTest {
    @Test
    fun `maps to a cancel when the user or the system dismisses the prompt`() {
        listOf(
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED,
        ).forEach { code ->
            assertTrue("code $code", biometricPromptError(code, "") is AuthenticationCancelledException)
        }
    }

    @Test
    fun `maps to a failure when the prompt ends for any other reason`() {
        listOf(
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
            BiometricPrompt.ERROR_TIMEOUT,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        ).forEach { code ->
            assertTrue("code $code", biometricPromptError(code, "") is AuthenticationFailedException)
        }
    }
}
