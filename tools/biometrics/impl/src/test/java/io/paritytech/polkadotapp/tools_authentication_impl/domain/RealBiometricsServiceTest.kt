package io.paritytech.polkadotapp.tools_authentication_impl.domain

import io.paritytech.polkadotapp.test_shared.assertFailure
import io.paritytech.polkadotapp.test_shared.assertFailureOf
import io.paritytech.polkadotapp.test_shared.assertSuccess
import io.paritytech.polkadotapp.test_shared.settle
import io.paritytech.polkadotapp.test_shared.thenSuspend
import io.paritytech.polkadotapp.test_shared.thenThrowUnsafe
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationCancelledException
import io.paritytech.polkadotapp.tools_authentication_impl.data.AuthenticationMethod
import io.paritytech.polkadotapp.tools_authentication_impl.data.InMemoryAuthenticationSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class RealBiometricsServiceTest {
    private val authMethod: AuthenticationMethod = mock(AuthenticationMethod::class.java)

    private val service = RealBiometricsService(authMethod, InMemoryAuthenticationSession())

    @Test
    fun `prompts on every gate call when the session is already authenticated`() = runBlocking<Unit> {
        withAuthenticationSucceeding()

        service.performSessionAuthentication()
        val first = service.performOneTimeAuthentication()
        val second = service.performOneTimeAuthentication()

        assertSuccess(first)
        assertSuccess(second)
        verifyPromptedTimes(3)
    }

    @Test
    fun `fails at once when a gate is requested while another prompt is live`() = runBlocking<Unit> {
        val livePrompt = withAuthenticationPending()

        val first = async { service.performOneTimeAuthentication() }
        settle()
        val second = async { service.performOneTimeAuthentication() }
        settle()

        assertTrue("expected the second gate to return without waiting", second.isCompleted)
        assertFailureOf<AuthenticationCancelledException>(second.await())
        verifyPromptedTimes(1)

        livePrompt.complete(Result.success(Unit))
        assertSuccess(first.await())
    }

    @Test
    fun `returns a failure instead of throwing when no activity is attached`() = runBlocking<Unit> {
        withAuthenticationThrowing()

        val result = service.performOneTimeAuthentication()

        assertFailure(result)
    }

    @Test
    fun `prompts again when the previous gate was cancelled while its prompt was live`() = runBlocking<Unit> {
        val livePrompt = withAuthenticationPendingThenSucceeding()
        val first = async { service.performOneTimeAuthentication() }
        settle()
        livePrompt.cancel()
        first.join()

        val second = service.performOneTimeAuthentication()

        assertSuccess(second)
        verifyPromptedTimes(2)
    }

    private suspend fun withAuthenticationSucceeding() {
        whenever(authMethod.authenticateUser()).thenReturn(Result.success(Unit))
    }

    private suspend fun withAuthenticationPending(): CompletableDeferred<Result<Unit>> {
        val livePrompt = CompletableDeferred<Result<Unit>>()
        whenever(authMethod.authenticateUser()).thenSuspend { livePrompt.await() }
        return livePrompt
    }

    private suspend fun withAuthenticationPendingThenSucceeding(): CompletableDeferred<Result<Unit>> {
        val livePrompt = CompletableDeferred<Result<Unit>>()
        whenever(authMethod.authenticateUser())
            .thenSuspend { livePrompt.await() }
            .thenReturn(Result.success(Unit))
        return livePrompt
    }

    private suspend fun withAuthenticationThrowing() {
        whenever(authMethod.authenticateUser()).thenThrowUnsafe(IllegalArgumentException("Required value was null."))
    }

    private suspend fun verifyPromptedTimes(times: Int) {
        verify(authMethod, times(times)).authenticateUser()
    }
}
