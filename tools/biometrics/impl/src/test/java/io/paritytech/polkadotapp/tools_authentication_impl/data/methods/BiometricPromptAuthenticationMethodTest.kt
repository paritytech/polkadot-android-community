package io.paritytech.polkadotapp.tools_authentication_impl.data.methods

import androidx.fragment.app.FragmentActivity
import io.paritytech.polkadotapp.common.presentation.navigation.OverlayCoordinator
import io.paritytech.polkadotapp.common.presentation.resources.ContextManager
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.test_shared.assertFailure
import io.paritytech.polkadotapp.test_shared.assertFailureOf
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationFailedException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.mock

class BiometricPromptAuthenticationMethodTest {
    private val contextManager: ContextManager = mock(ContextManager::class.java)

    private val overlayCoordinator: OverlayCoordinator = mock(OverlayCoordinator::class.java)

    private val method = BiometricPromptAuthenticationMethod(
        contextManager,
        mock(CoroutineDispatchers::class.java),
        overlayCoordinator,
    )

    @Test
    fun `returns a failure instead of throwing when no activity is attached`() = runBlocking<Unit> {
        withNoActivityAttached()

        val result = method.authenticateUser()

        assertFailureOf<AuthenticationFailedException>(result)
    }

    @Test
    fun `returns a failure instead of throwing when the prompt setup throws`() = runBlocking<Unit> {
        withActivityAttached()
        withOverlaysForbidden()

        val result = method.authenticateUser()

        assertFailure(result)
    }

    private fun withActivityAttached() {
        whenever(contextManager.getActivity()).thenReturn(mock(FragmentActivity::class.java))
    }

    private suspend fun withOverlaysForbidden() {
        whenever(overlayCoordinator.forbidOverlays()).thenReturn(mock(OverlayCoordinator.OverlayLock::class.java))
    }

    private fun withNoActivityAttached() {
        whenever(contextManager.getActivity()).thenReturn(null)
        whenever(contextManager.requireActivity()).thenThrow(IllegalArgumentException("Required value was null."))
    }
}
