package io.paritytech.polkadotapp.feature_dotns_impl.domain.dotNs

import android.net.Uri
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolutionError
import io.paritytech.polkadotapp.feature_dotns_impl.data.contract.DotNsContractApi
import io.paritytech.polkadotapp.feature_dotns_impl.data.ipfs.CarFetcher
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.ContentHashOverrides
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.DotNsContentStorage
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.assertFailureOf
import io.paritytech.polkadotapp.test_shared.testDispatchers
import io.paritytech.polkadotapp.test_shared.thenThrowUnsafe
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.io.File
import java.io.IOException

class RealDotNsResolverTest {
    private val contractApi: DotNsContractApi = mock()
    private val carFetcher: CarFetcher = mock()
    private val contentStorage: DotNsContentStorage = mock()
    private val contentHashOverrides: ContentHashOverrides = mock()

    private val contentUri: Uri = mock()

    private val domain = "peopl.testnet"

    // A host screen shows a spinner for as long as progress says the content is on its way. A domain
    // with nothing registered behind it never downloads and never unpacks, so unless the failure is
    // reported the spinner is the last thing the user ever sees.
    @Test
    fun `a domain with no content registered reports failure rather than staying silent`() = runTest {
        withNoContentOnChain()
        val resolver = resolver()

        val result = resolver.resolveToLocalUri(domain)

        assertTrue(result.isFailure)
        assertTrue(resolver.getProgressByDomain(domain).first() is DotNsLoadProgress.Failed)
    }

    @Test
    fun `a chain that cannot be reached reports failure too`() = runTest {
        withChainUnreachable()
        val resolver = resolver()

        val result = resolver.resolveToLocalUri(domain)

        assertTrue(result.isFailure)
        assertTrue(resolver.getProgressByDomain(domain).first() is DotNsLoadProgress.Failed)
    }

    @Test
    fun `a failed resolution stays cached until it is invalidated`() = runTest {
        withChainUnreachable()
        val resolver = resolver()

        val first = resolver.resolveToLocalUri(domain)
        val second = resolver.resolveToLocalUri(domain)

        assertSame(first.exceptionOrNull(), second.exceptionOrNull())
        verifyContentHashReads(times = 1)
    }

    @Test
    fun `invalidating a failed resolution reads the chain again`() = runTest {
        withChainUnreachableOnce()
        withContentOnDisk()
        val resolver = resolver()

        resolver.resolveToLocalUri(domain)
        resolver.invalidate(domain)
        val result = resolver.resolveToLocalUri(domain)

        assertEquals(contentUri, result.getOrNull())
        verifyContentHashReads(times = 2)
    }

    @Test
    fun `keeps a successful resolution cached`() = runTest {
        withContentOnChain()
        withContentOnDisk()
        val resolver = resolver()

        val first = resolver.resolveToLocalUri(domain)
        val second = resolver.resolveToLocalUri(domain)

        assertEquals(contentUri, first.getOrNull())
        assertEquals(contentUri, second.getOrNull())
        verifyContentHashReads(times = 1)
    }

    @Test
    fun `a domain with no content registered fails as not found`() = runTest {
        withNoContentOnChain()

        val result = resolver().resolveToLocalUri(domain)

        assertFailureOf<DotNsResolutionError.NotFound>(result)
    }

    @Test
    fun `an archive that cannot be stored fails as damaged`() = runTest {
        withContentOnChain()
        withContentNotOnDisk()
        withCarDownloaded()
        withContentStorageFailing()

        val result = resolver().resolveToLocalUri(domain)

        assertFailureOf<DotNsResolutionError.ContentDamaged>(result)
    }

    @Test
    fun `content missing after it was saved fails as not found`() = runTest {
        withContentOnChain()
        withContentNotOnDisk()
        withCarDownloaded()

        val result = resolver().resolveToLocalUri(domain)

        assertFailureOf<DotNsResolutionError.NotFound>(result)
    }

    private suspend fun withNoContentOnChain() {
        whenever(contentHashOverrides.getContentHashOverride(domain)).thenReturn(null)
        whenever(contractApi.resolveContentHash(domain)).thenReturn(Result.success(null))
    }

    private suspend fun withChainUnreachable() {
        whenever(contentHashOverrides.getContentHashOverride(domain)).thenReturn(null)
        whenever(contractApi.resolveContentHash(domain)).thenReturn(Result.failure(IllegalStateException("no node")))
    }

    private suspend fun withChainUnreachableOnce() {
        whenever(contentHashOverrides.getContentHashOverride(domain)).thenReturn(null)
        whenever(contractApi.resolveContentHash(domain))
            .thenReturn(Result.failure(IllegalStateException("no node")), Result.success(CONTENT_HASH))
    }

    private suspend fun withContentOnChain() {
        whenever(contentHashOverrides.getContentHashOverride(domain)).thenReturn(null)
        whenever(contractApi.resolveContentHash(domain)).thenReturn(Result.success(CONTENT_HASH))
    }

    private fun withContentNotOnDisk() {
        whenever(contentStorage.getContentDirectory(CONTENT_HASH_HEX)).thenReturn(null)
    }

    private fun withContentOnDisk() {
        whenever(contentStorage.getContentDirectory(CONTENT_HASH_HEX)).thenReturn(File("content"))
    }

    private suspend fun withContentStorageFailing() {
        whenever(contentStorage.saveContentStreaming(any(), any())).thenThrowUnsafe(IOException("disk full"))
    }

    private suspend fun withCarDownloaded() {
        whenever(carFetcher.fetchCarToFile(any(), any())).thenReturn(Result.success(File("archive.car")))
    }

    private suspend fun verifyContentHashReads(times: Int) {
        verify(contractApi, times(times)).resolveContentHash(domain)
    }

    private fun TestScope.resolver() = RealDotNsResolver(
        contractApi = contractApi,
        carFetcher = carFetcher,
        contentStorage = contentStorage,
        contentHashOverrides = contentHashOverrides,
        fileUriProvider = { contentUri },
        dispatchers = testDispatchers(),
    )

    private companion object {
        val CONTENT_HASH = byteArrayOf(0x01, 0x70, 0x12, 0x20)
        const val CONTENT_HASH_HEX = "01701220"
    }
}
