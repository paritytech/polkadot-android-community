package io.paritytech.polkadotapp.feature_dotns_impl.domain.tld

import io.paritytech.polkadotapp.common.utils.RealCoroutineDispatchers
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_dotns_impl.data.repository.NetworkSuffixRepository
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.DotNsTldStorage
import io.paritytech.polkadotapp.test_shared.assertFailureOf
import io.paritytech.polkadotapp.test_shared.testDispatchers
import io.paritytech.polkadotapp.test_shared.thenSuspend
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val PASEO = DotNsTld.parse("paseo")!!
private val READ_LIMIT = 30.seconds

class RealDotNsTldProviderTest {
    private val networkSuffixRepository: NetworkSuffixRepository = mock(NetworkSuffixRepository::class.java)
    private val storage = FakeDotNsTldStorage()

    private val provider by lazy {
        RealDotNsTldProvider(networkSuffixRepository, storage, RealCoroutineDispatchers())
    }

    @Test
    fun `settles and persists the suffix the chain reports`() = runBlocking<Unit> {
        withReportedSuffix(PASEO)

        val result = provider.getTld()

        assertEquals(PASEO, result.getOrThrow())
        assertEquals(PASEO, storage.stored)
    }

    @Test
    fun `fails without settling when the chain reports no usable suffix`() = runBlocking<Unit> {
        withReportedSuffix(null)

        assertTrue(provider.getTld().isFailure)
        assertNull(storage.stored)
    }

    @Test
    fun `fails without settling when the read fails - then retries on the next call`() = runBlocking<Unit> {
        withFailingRead()

        assertTrue(provider.getTld().isFailure)
        assertNull(storage.stored)

        withReportedSuffix(PASEO)

        assertEquals(PASEO, provider.getTld().getOrThrow())
    }

    @Test
    fun `serves the settled value without a second read`() = runBlocking<Unit> {
        withReportedSuffix(PASEO)

        provider.getTld()
        provider.getTld()

        verify(networkSuffixRepository, times(1)).networkSuffix()
        assertEquals(PASEO, provider.currentTldOrNull())
    }

    @Test
    fun `currentTldOrNull serves the persisted value before the first read settles`() = runBlocking<Unit> {
        withFailingRead()
        storage.putTld(PASEO)

        assertEquals(PASEO, provider.currentTldOrNull())
    }

    @Test
    fun `currentTldOrNull is null without a settled or persisted value`() = runBlocking<Unit> {
        withFailingRead()

        assertNull(provider.currentTldOrNull())
    }

    @Test
    fun `a network suffix read that never answers fails at 30 seconds and not before`() = runTest {
        withUnansweredRead()
        val provider = providerOnTestClock()

        val read = async { provider.getTld() }
        advanceTimeBy(READ_LIMIT - 1.milliseconds)
        assertFalse(read.isCompleted)
        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertTrue(read.isCompleted)

        assertFailureOf<TimeoutException>(read.await())
    }

    @Test
    fun `the next read after a timed-out one asks the chain again`() = runTest {
        withUnansweredReadThenSuffix(PASEO)
        val provider = providerOnTestClock()
        provider.getTld()

        val result = provider.getTld()

        assertEquals(PASEO, result.getOrNull())
        verifyChainReads(count = 2)
    }

    private fun TestScope.providerOnTestClock() = RealDotNsTldProvider(
        networkSuffixRepository = networkSuffixRepository,
        tldStorage = storage,
        dispatchers = testDispatchers(),
    )

    private suspend fun withUnansweredRead() {
        whenever(networkSuffixRepository.networkSuffix()).thenSuspend { awaitCancellation() }
    }

    private suspend fun withUnansweredReadThenSuffix(tld: DotNsTld) {
        whenever(networkSuffixRepository.networkSuffix())
            .thenSuspend { awaitCancellation() }
            .thenReturn(Result.success(tld))
    }

    private suspend fun withReportedSuffix(tld: DotNsTld?) {
        whenever(networkSuffixRepository.networkSuffix()).thenReturn(Result.success(tld))
    }

    private suspend fun withFailingRead() {
        whenever(networkSuffixRepository.networkSuffix()).thenReturn(Result.failure(IllegalStateException("rpc down")))
    }

    private suspend fun verifyChainReads(count: Int) {
        verify(networkSuffixRepository, times(count)).networkSuffix()
    }

    private class FakeDotNsTldStorage : DotNsTldStorage {
        var stored: DotNsTld? = null

        override fun getTld(): DotNsTld? = stored

        override fun putTld(tld: DotNsTld) {
            stored = tld
        }
    }
}
