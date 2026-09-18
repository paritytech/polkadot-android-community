package io.paritytech.polkadotapp.feature_products_impl.domain.merchantMode

import android.net.Uri
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.usecase.ResolveProductUseCase
import io.paritytech.polkadotapp.test_shared.any
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
    private val dotNsTldProvider = mock(DotNsTldProvider::class.java)
    private val resolveProductUseCase = mock(ResolveProductUseCase::class.java)

    private val loader = RealMerchantProductLoader(
        merchantDomainProvider,
        dotNsResolver,
        dotNsTldProvider,
        resolveProductUseCase,
    )

    @Test
    fun `fails when no merchant domain is configured`() = runBlocking<Unit> {
        val notConfigured = IllegalStateException("no merchant_url")
        withMerchantDomain(Result.failure(notConfigured))

        assertEquals(notConfigured, assertFailure(loader.open()))
    }

    @Test
    fun `reads nothing from the chain when no merchant domain is configured`() = runBlocking<Unit> {
        withMerchantDomain(Result.failure(IllegalStateException("no merchant_url")))

        loader.open()

        verifyNoChainRead()
    }

    @Test
    fun `warming up fetches the archive ahead of the first open`() = runBlocking<Unit> {
        withMerchantDomain(Result.success(MERCHANT_DOMAIN))
        whenever(dotNsResolver.resolveToLocalUri(MERCHANT_DOMAIN)).thenReturn(Result.success(mock(Uri::class.java)))

        loader.warmUpMerchantLoading()

        verify(dotNsResolver).resolveToLocalUri(MERCHANT_DOMAIN)
    }

    private suspend fun verifyNoChainRead() {
        verify(dotNsTldProvider, never()).getTld()
        verify(resolveProductUseCase, never()).resolve(any())
        verify(dotNsResolver, never()).resolveToLocalUri(any())
    }

    private suspend fun withMerchantDomain(domain: Result<String>) {
        whenever(merchantDomainProvider.getMerchantDomain()).thenReturn(domain)
    }

    private suspend fun MerchantProductLoader.open(): Result<MerchantProduct> =
        with(StalenessReportCollector.NoOp) { openMerchantProduct() }

    private fun assertFailure(result: Result<*>): Throwable {
        assertTrue("expected Result.failure but was ${result.getOrNull()}", result.isFailure)

        return result.exceptionOrNull()!!
    }
}
