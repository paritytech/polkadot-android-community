package io.paritytech.polkadotapp.tools_authentication_impl.data.methods

import io.paritytech.polkadotapp.common.presentation.navigation.OverlayCoordinator
import io.paritytech.polkadotapp.common.presentation.resources.ContextManager
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.test_shared.assertFailureOf
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationFailedException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.mock

class BiometricPromptAuthenticationMethodTest {
    private val contextManager: ContextManager = mock(ContextManager::class.java)

    private val method = BiometricPromptAuthenticationMethod(
        contextManager,
        mock(CoroutineDispatchers::class.java),
        mock(OverlayCoordinator::class.java),
    )

    @Test
    fun `returns a failure instead of throwing when no activity is attached`() = runBlocking<Unit> {
        val result = method.authenticateUser()

        assertFailureOf<AuthenticationFailedException>(result)
    }
}
