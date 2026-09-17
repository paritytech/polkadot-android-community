package io.paritytech.polkadotapp.feature_products_impl.data.config

import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

private const val MERCHANT_URL_KEY = "merchant_url"

private val PASEO = DotNsTld.parse("paseo")!!

class RemoteConfigMerchantDomainProviderTest {
    private val remoteConfigService = mock(RemoteConfigService::class.java)
    private val dotNsTldProvider = mock(DotNsTldProvider::class.java)

    private val provider = RemoteConfigMerchantDomainProvider(remoteConfigService, dotNsTldProvider)

    @Test
    fun `serves the configured domain`() = runBlocking<Unit> {
        withTld(PASEO)
        withConfiguredValue("shop.paseo")

        assertEquals("shop.paseo", merchantDomain().getOrNull())
    }

    @Test
    fun `falls back to the terminal label on the active network when the key is unset`() = runBlocking<Unit> {
        withTld(PASEO)
        withConfiguredValue("")

        assertEquals("terminal.paseo", merchantDomain().getOrNull())
    }

    @Test
    fun `falls back to the terminal label when the config read fails`() = runBlocking<Unit> {
        withTld(PASEO)
        whenever(remoteConfigService.getString(MERCHANT_URL_KEY))
            .thenReturn(Result.failure(IllegalStateException("no network")))

        assertEquals("terminal.paseo", merchantDomain().getOrNull())
    }

    @Test
    fun `reduces a configured url to its host`() = runBlocking<Unit> {
        withTld(PASEO)
        withConfiguredValue("https://shop.paseo/checkout?table=4")

        assertEquals("shop.paseo", merchantDomain().getOrNull())
    }

    @Test
    fun `falls back to the terminal label when the configured value has no host`() = runBlocking<Unit> {
        withTld(PASEO)
        withConfiguredValue("://")

        assertEquals("terminal.paseo", merchantDomain().getOrNull())
    }

    @Test
    fun `serves the configured domain without reading a TLD`() = runBlocking<Unit> {
        withNoTld()
        withConfiguredValue("shop.paseo")

        assertEquals("shop.paseo", merchantDomain().getOrNull())
    }

    @Test
    fun `fails when nothing is configured and the network reports no TLD`() = runBlocking<Unit> {
        withNoTld()
        withConfiguredValue("")

        assertTrue(merchantDomain().isFailure)
    }

    private suspend fun merchantDomain(): Result<String> = with(StalenessReportCollector.NoOp) {
        provider.getMerchantDomain()
    }

    private suspend fun withNoTld() {
        whenever(dotNsTldProvider.getTld()).thenReturn(Result.failure(IllegalStateException("chain is down")))
    }

    private suspend fun withTld(tld: DotNsTld) {
        whenever(dotNsTldProvider.getTld()).thenReturn(Result.success(tld))
    }

    private suspend fun withConfiguredValue(value: String) {
        whenever(remoteConfigService.getString(MERCHANT_URL_KEY)).thenReturn(Result.success(value))
    }
}
