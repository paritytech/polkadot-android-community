package io.paritytech.polkadotapp.feature_products_impl.data.config

import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

private const val MERCHANT_URL_KEY = "merchant_url"

class RemoteConfigMerchantDomainProviderTest {
    private val remoteConfigService = mock(RemoteConfigService::class.java)

    private val provider = RemoteConfigMerchantDomainProvider(remoteConfigService)

    @Test
    fun `serves the configured domain`() = runBlocking<Unit> {
        withConfiguredValue("shop.paseo")

        assertEquals("shop.paseo", assertSuccess(provider.getMerchantDomain()))
    }

    @Test
    fun `reduces a configured url to its host`() = runBlocking<Unit> {
        withConfiguredValue("https://shop.paseo/checkout?table=4")

        assertEquals("shop.paseo", assertSuccess(provider.getMerchantDomain()))
    }

    @Test
    fun `fails when the key is unset rather than falling back to a built-in domain`() = runBlocking<Unit> {
        withConfiguredValue("")

        assertFailure(provider.getMerchantDomain())
    }

    @Test
    fun `fails when the configured value carries no host`() = runBlocking<Unit> {
        withConfiguredValue("://")

        assertFailure(provider.getMerchantDomain())
    }

    @Test
    fun `fails when the config read fails`() = runBlocking<Unit> {
        whenever(remoteConfigService.getSyncedString(MERCHANT_URL_KEY))
            .thenReturn(Result.failure(IllegalStateException("no network")))

        assertFailure(provider.getMerchantDomain())
    }

    @Test
    fun `reads the synced value rather than whatever is already cached`() = runBlocking<Unit> {
        withConfiguredValue("shop.paseo")

        provider.getMerchantDomain()

        verifySyncedReadOnly()
    }

    private suspend fun verifySyncedReadOnly() {
        verify(remoteConfigService).getSyncedString(MERCHANT_URL_KEY)
        verify(remoteConfigService, never()).getString(MERCHANT_URL_KEY)
    }

    private suspend fun withConfiguredValue(value: String) {
        whenever(remoteConfigService.getSyncedString(MERCHANT_URL_KEY)).thenReturn(Result.success(value))
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
