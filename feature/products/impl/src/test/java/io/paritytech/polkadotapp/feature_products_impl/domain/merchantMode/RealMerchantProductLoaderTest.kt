package io.paritytech.polkadotapp.feature_products_impl.domain.merchantMode

import android.net.Uri
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.eq
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

private const val MERCHANT_DOMAIN = "terminal.paseo"

class RealMerchantProductLoaderTest {
    private val merchantDomainProvider = mock(MerchantDomainProvider::class.java)
    private val dotNsResolver = mock(DotNsResolver::class.java)

    private val loader = RealMerchantProductLoader(merchantDomainProvider, dotNsResolver)

    @Test
    fun `resolves the content before handing out the url`() = runBlocking<Unit> {
        withMerchantDomain(Result.success(MERCHANT_DOMAIN))
        withResolvedContent()

        val url = loader.merchantUrl()

        assertEquals("https://$MERCHANT_DOMAIN", assertSuccess(url))
        verifyContentResolvedFor(MERCHANT_DOMAIN)
    }

    @Test
    fun `fails when the terminal publishes no content`() = runBlocking<Unit> {
        val unpublished = IllegalStateException("not registered")
        withMerchantDomain(Result.success(MERCHANT_DOMAIN))
        whenever(dotNsResolver.resolveToLocalUri(MERCHANT_DOMAIN)).thenReturn(Result.failure(unpublished))

        assertEquals(unpublished, assertFailure(loader.merchantUrl()))
    }

    @Test
    fun `does not reach for content when the domain is unknown`() = runBlocking<Unit> {
        val chainDown = IllegalStateException("chain is down")
        withMerchantDomain(Result.failure(chainDown))

        val url = loader.merchantUrl()

        assertEquals(chainDown, assertFailure(url))
        verifyNoContentResolved()
    }

    private suspend fun withMerchantDomain(domain: Result<String>) {
        whenever(with(eq(StalenessReportCollector.NoOp)) { merchantDomainProvider.getMerchantDomain() })
            .thenReturn(domain)
    }

    private suspend fun withResolvedContent() {
        whenever(dotNsResolver.resolveToLocalUri(MERCHANT_DOMAIN)).thenReturn(Result.success(mock(Uri::class.java)))
    }

    private suspend fun verifyContentResolvedFor(domain: String) {
        verify(dotNsResolver).resolveToLocalUri(domain)
    }

    private suspend fun verifyNoContentResolved() {
        verify(dotNsResolver, never()).resolveToLocalUri(any())
    }

    private suspend fun MerchantProductLoader.merchantUrl(): Result<String> = with(StalenessReportCollector.NoOp) {
        getMerchantUrl()
    }

    private fun <T> assertSuccess(result: Result<T>): T {
        assertTrue("expected Result.success but was ${result.exceptionOrNull()}", result.isSuccess)

        return result.getOrNull()!!
    }

    private fun assertFailure(result: Result<*>): Throwable {
        assertTrue("expected Result.failure but was ${result.getOrNull()}", result.isFailure)

        return result.exceptionOrNull()!!
    }
}
