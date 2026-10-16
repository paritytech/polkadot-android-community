package io.paritytech.polkadotapp.tools_remoteconfig_impl.presentation

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.utils.network.NetworkStateService
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Every getSynced* read waits for the first successful sync, so a sync that gives up early hangs them for the rest
 * of the process, and one that never stops retrying hammers the backend.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteConfigSyncInitializerTest {
    private val isNetworkAvailable = MutableStateFlow(false)
    private val syncOutcomes = ArrayDeque<Boolean>()
    private val syncAttemptTimes = mutableListOf<Duration>()

    private val networkStateService = object : NetworkStateService {
        override val isNetworkAvailable = this@RemoteConfigSyncInitializerTest.isNetworkAvailable
    }

    @Test
    fun `does not sync while offline`() = runTest {
        withNetworkAvailable(false)

        startInitializer()
        advanceTimeBy(10.minutes)

        assertSyncAttemptsAt(emptyList())
    }

    @Test
    fun `syncs as soon as the network comes up after an offline start`() = runTest {
        withNetworkAvailable(false)
        withSyncOutcomes(true)

        startInitializer()
        advanceTimeBy(5.minutes)
        withNetworkAvailable(true)
        runCurrent()

        assertSyncAttemptsAt(listOf(5.minutes))
    }

    @Test
    fun `retries a failed sync with growing delays while online`() = runTest {
        withNetworkAvailable(true)
        withSyncOutcomes(false, false, true)

        startInitializer()
        advanceTimeBy(1.hours)

        assertSyncAttemptsAt(listOf(0.seconds, 2.seconds, 6.seconds))
    }

    @Test
    fun `retry delay stops growing at one minute`() = runTest {
        withNetworkAvailable(true)
        withSyncOutcomes(false, false, false, false, false, false, false, true)

        startInitializer()
        advanceTimeBy(1.hours)

        assertRetryDelays(listOf(2.seconds, 4.seconds, 8.seconds, 16.seconds, 32.seconds, 1.minutes, 1.minutes))
    }

    @Test
    fun `losing the network pauses retries and getting it back retries at once`() = runTest {
        withNetworkAvailable(true)
        withSyncOutcomes(false, true)

        startInitializer()
        advanceTimeBy(1.seconds)
        withNetworkAvailable(false)
        advanceTimeBy(10.minutes)
        withNetworkAvailable(true)
        runCurrent()

        assertSyncAttemptsAt(listOf(0.seconds, 10.minutes + 1.seconds))
    }

    @Test
    fun `stops syncing after the first success`() = runTest {
        withNetworkAvailable(true)
        withSyncOutcomes(true)

        startInitializer()
        advanceTimeBy(1.minutes)
        withNetworkAvailable(false)
        advanceTimeBy(1.minutes)
        withNetworkAvailable(true)
        advanceTimeBy(1.hours)

        assertSyncAttemptsAt(listOf(0.seconds))
    }

    private fun TestScope.startInitializer() {
        val remoteConfigService = object : RemoteConfigService by mock(RemoteConfigService::class.java) {
            override suspend fun sync(): Result<Unit> {
                syncAttemptTimes += currentTime.milliseconds
                return if (syncOutcomes.removeFirst()) Result.success(Unit) else Result.failure(IOException("offline"))
            }
        }
        val initializer = RemoteConfigSyncInitializer(
            remoteConfigService = remoteConfigService,
            networkStateService = networkStateService,
        )

        with(ComputationalScope(backgroundScope)) {
            initializer.initialize()
        }
    }

    private fun withNetworkAvailable(available: Boolean) {
        isNetworkAvailable.value = available
    }

    private fun withSyncOutcomes(vararg succeeded: Boolean) {
        syncOutcomes += succeeded.toList()
    }

    private fun assertSyncAttemptsAt(times: List<Duration>) {
        assertEquals(times, syncAttemptTimes)
    }

    private fun assertRetryDelays(delays: List<Duration>) {
        assertEquals(delays, syncAttemptTimes.zipWithNext { previous, next -> next - previous })
    }
}
