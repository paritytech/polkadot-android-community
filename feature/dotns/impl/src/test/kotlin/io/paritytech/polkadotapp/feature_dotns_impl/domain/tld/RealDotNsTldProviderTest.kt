package io.paritytech.polkadotapp.feature_dotns_impl.domain.tld

import io.paritytech.polkadotapp.common.utils.RealCoroutineDispatchers
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_dotns_impl.data.repository.NetworkSuffixRepository
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.DotNsTldStorage
import io.paritytech.polkadotapp.test_shared.assertFailureOf
import io.paritytech.polkadotapp.test_shared.thenSuspend
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
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

private val PASEO = DotNsTld.parse("paseo")!!

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun `a network suffix read that never answers fails at the limit and not before`() = runTest {
        withUnansweredRead()

        val read = startReading()
        advanceTimeBy(READ_LIMIT_MS - 1)
        runCurrent()
        assertFalse("pending until the limit", read.isCompleted)

        advanceTimeBy(1)
        runCurrent()
        assertTrue("failed at the limit", read.isCompleted)
        assertFailureOf<TimeoutException>(read.await())
        assertNull(storage.stored)
    }

    @Test
    fun `a read after a timed-out one asks the chain again`() = runTest {
        withUnansweredReadThen(PASEO)

        val timedOut = startReading()
        advanceTimeBy(READ_LIMIT_MS)
        runCurrent()
        assertTrue("first read gave up at the limit", timedOut.isCompleted)
        assertFailureOf<TimeoutException>(timedOut.await())

        assertEquals(PASEO, provider.getTld().getOrThrow())
        verifySuffixRead(times = 2)
    }

    @Test
    fun `a caller queued behind a stuck read gets its own read once that one times out`() = runTest {
        withUnansweredReadThenSlowAnswer(PASEO, answerAfterMs = QUEUED_READ_MS)

        startReading()
        advanceTimeBy(QUEUED_CALLER_AT_MS)
        val queued = startReading()
        advanceTimeBy(READ_LIMIT_MS - QUEUED_CALLER_AT_MS - 1)
        runCurrent()
        assertFalse("queued caller waits behind the stuck read", queued.isCompleted)

        advanceTimeBy(1 + QUEUED_READ_MS)
        runCurrent()
        assertTrue("queued caller's own read answered", queued.isCompleted)
        assertEquals(PASEO, queued.await().getOrThrow())
    }

    private fun TestScope.startReading(): Deferred<Result<DotNsTld>> = backgroundScope.async { provider.getTld() }

    private suspend fun withUnansweredRead() {
        whenever(networkSuffixRepository.networkSuffix()).thenSuspend { awaitCancellation() }
    }

    private suspend fun withUnansweredReadThen(tld: DotNsTld) {
        whenever(networkSuffixRepository.networkSuffix())
            .thenSuspend { awaitCancellation() }
            .thenReturn(Result.success(tld))
    }

    private suspend fun withUnansweredReadThenSlowAnswer(tld: DotNsTld, answerAfterMs: Long) {
        whenever(networkSuffixRepository.networkSuffix())
            .thenSuspend { awaitCancellation() }
            .thenSuspend {
                delay(answerAfterMs)
                Result.success(tld)
            }
    }

    private suspend fun verifySuffixRead(times: Int) {
        verify(networkSuffixRepository, times(times)).networkSuffix()
    }

    private suspend fun withReportedSuffix(tld: DotNsTld?) {
        whenever(networkSuffixRepository.networkSuffix()).thenReturn(Result.success(tld))
    }

    private suspend fun withFailingRead() {
        whenever(networkSuffixRepository.networkSuffix()).thenReturn(Result.failure(IllegalStateException("rpc down")))
    }

    private companion object {
        const val READ_LIMIT_MS = 30_000L
        const val QUEUED_CALLER_AT_MS = 5_000L
        const val QUEUED_READ_MS = 10_000L
    }

    private class FakeDotNsTldStorage : DotNsTldStorage {
        var stored: DotNsTld? = null

        override fun getTld(): DotNsTld? = stored

        override fun putTld(tld: DotNsTld) {
            stored = tld
        }
    }
}
