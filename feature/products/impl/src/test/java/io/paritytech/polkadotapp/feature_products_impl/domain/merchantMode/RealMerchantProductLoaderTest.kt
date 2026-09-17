package io.paritytech.polkadotapp.feature_products_impl.domain.merchantMode

import android.net.Uri
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class RealMerchantProductLoaderTest {
    private val resolvedUri = mock(Uri::class.java)

    @Test
    fun `resolves the content before handing out the url`() = runBlocking<Unit> {
        val resolver = FakeDotNsResolver(Result.success(resolvedUri))

        val url = loader(Result.success("terminal.paseo"), resolver).merchantUrl()

        assertEquals("https://terminal.paseo", url.getOrNull())
        assertEquals(listOf("terminal.paseo"), resolver.resolvedNames)
    }

    @Test
    fun `fails when the terminal publishes no content`() = runBlocking<Unit> {
        val resolver = FakeDotNsResolver(Result.failure(IllegalStateException("not registered")))

        assertTrue(loader(Result.success("terminal.paseo"), resolver).merchantUrl().isFailure)
    }

    @Test
    fun `does not reach for content when the domain is unknown`() = runBlocking<Unit> {
        val resolver = FakeDotNsResolver(Result.success(resolvedUri))

        val url = loader(Result.failure(IllegalStateException("chain is down")), resolver).merchantUrl()

        assertTrue(url.isFailure)
        assertTrue(resolver.resolvedNames.isEmpty())
    }

    private fun loader(domain: Result<String>, resolver: DotNsResolver) =
        RealMerchantProductLoader(FakeMerchantDomainProvider(domain), resolver)

    private suspend fun MerchantProductLoader.merchantUrl(): Result<String> = with(StalenessReportCollector.NoOp) {
        getMerchantUrl()
    }
}

private class FakeMerchantDomainProvider(private val domain: Result<String>) : MerchantDomainProvider {
    context(diagnostics: StalenessReportCollector)
    override suspend fun getMerchantDomain(): Result<String> = domain
}

private class FakeDotNsResolver(private val resolved: Result<Uri>) : DotNsResolver {
    val resolvedNames = mutableListOf<String>()

    override suspend fun resolveToLocalUri(dotNsName: String): Result<Uri> {
        resolvedNames += dotNsName

        return resolved
    }

    override suspend fun getMetadataEntry(dotNsName: String, key: String): Result<String?> = notUsed()

    override fun getProgressByDomain(dotNsName: String): Flow<DotNsLoadProgress> = notUsed()

    override suspend fun clearCache() = notUsed()

    private fun notUsed(): Nothing = throw UnsupportedOperationException("Not used by the loader")
}
