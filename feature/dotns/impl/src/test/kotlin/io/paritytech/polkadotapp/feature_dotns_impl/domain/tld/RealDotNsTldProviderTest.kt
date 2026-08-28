package io.paritytech.polkadotapp.feature_dotns_impl.domain.tld

import io.paritytech.polkadotapp.common.utils.RealCoroutineDispatchers
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_dotns_impl.data.contract.DotNsContractApi
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.DotNsTldStorage
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class RealDotNsTldProviderTest {
    private val contractApi: DotNsContractApi = mock(DotNsContractApi::class.java)
    private val storage = FakeDotNsTldStorage()

    private val provider by lazy {
        RealDotNsTldProvider(contractApi, storage, RealCoroutineDispatchers())
    }

    @Test
    fun `settles and persists the reported suffix when the registry answers a valid tld`() = runBlocking<Unit> {
        withReportedTld(".paseo")

        val result = provider.getTld()

        assertEquals(".paseo", result.getOrThrow().suffix)
        assertEquals(".paseo", storage.stored?.suffix)
    }

    @Test
    fun `settles the fallback when the registry answers empty`() = runBlocking<Unit> {
        withReportedTld(null)

        assertEquals(DotNsTld.FALLBACK, provider.getTld().getOrThrow())
    }

    @Test
    fun `settles the fallback when the registry answers an invalid label`() = runBlocking<Unit> {
        withReportedTld("not a label")

        assertEquals(DotNsTld.FALLBACK, provider.getTld().getOrThrow())
    }

    @Test
    fun `fails without settling when the read fails - then retries on the next call`() = runBlocking<Unit> {
        withFailingRead()

        assertTrue(provider.getTld().isFailure)
        assertNull(storage.stored)

        withReportedTld(".paseo")

        assertEquals(".paseo", provider.getTld().getOrThrow().suffix)
    }

    @Test
    fun `serves the settled value without a second read`() = runBlocking<Unit> {
        withReportedTld(".paseo")

        provider.getTld()
        provider.getTld()

        verify(contractApi, times(1)).readTld()
        assertEquals(".paseo", provider.currentTldOrNull()?.suffix)
    }

    @Test
    fun `currentTldOrNull serves the persisted value before the first read settles`() = runBlocking<Unit> {
        withFailingRead()
        storage.putTld(DotNsTld.parse("paseo")!!)

        assertEquals(".paseo", provider.currentTldOrNull()?.suffix)
    }

    @Test
    fun `currentTldOrNull is null without a settled or persisted value`() = runBlocking<Unit> {
        withFailingRead()

        assertNull(provider.currentTldOrNull())
    }

    private suspend fun withReportedTld(rawSuffix: String?) {
        whenever(contractApi.readTld()).thenReturn(Result.success(rawSuffix))
    }

    private suspend fun withFailingRead() {
        whenever(contractApi.readTld()).thenReturn(Result.failure(IllegalStateException("rpc down")))
    }

    private class FakeDotNsTldStorage : DotNsTldStorage {
        var stored: DotNsTld? = null

        override fun getTld(): DotNsTld? = stored

        override fun putTld(tld: DotNsTld) {
            stored = tld
        }
    }
}
