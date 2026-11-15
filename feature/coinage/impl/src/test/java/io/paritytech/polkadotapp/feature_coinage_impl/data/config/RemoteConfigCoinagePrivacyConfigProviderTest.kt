package io.paritytech.polkadotapp.feature_coinage_impl.data.config

import io.mockk.coEvery
import io.mockk.mockk
import io.paritytech.polkadotapp.common.utils.Fraction.Companion.percents
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.AnonymityRequirements
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.CoinagePrivacyConfig
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.VoucherReadiness
import io.paritytech.polkadotapp.test_shared.TestCoroutineDispatchers
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import io.paritytech.polkadotapp.tools_remoteconfig_api.getJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class RemoteConfigCoinagePrivacyConfigProviderTest {
    private val remoteConfigService: RemoteConfigService = mockk()
    private val provider = RemoteConfigCoinagePrivacyConfigProvider(
        remoteConfigService,
        TestCoroutineDispatchers(Dispatchers.Unconfined),
    )

    @Test
    fun `an unset key falls back to the bundled defaults`() = runBlocking<Unit> {
        withRemote(Result.success(null))

        assertEquals(BUNDLED, provider.privacyConfigOrDefault())
    }

    @Test
    fun `a remote read failure falls back to the bundled defaults`() = runBlocking<Unit> {
        withRemote(Result.failure(IllegalStateException("not activated")))

        assertEquals(BUNDLED, provider.privacyConfigOrDefault())
    }

    @Test
    fun `a newly activated value applies on the next read`() = runBlocking<Unit> {
        withRemote(Result.success(null))
        val before = provider.privacyConfigOrDefault()
        withRemote(Result.success(PUBLISHED))

        val after = provider.privacyConfigOrDefault()

        assertEquals(BUNDLED, before)
        assertEquals(AnonymityRequirements(5.minutes, 4), after.balanced.anonymityRequirements)
    }

    private fun withRemote(result: Result<CoinagePrivacyConfigRemote?>) {
        coEvery { remoteConfigService.getJsonObject<CoinagePrivacyConfigRemote?>("coinage_privacy_config") } returns result
    }

    private companion object {
        val PUBLISHED = CoinagePrivacyConfigRemote(
            balanced = PrivacyModeRemote(ringFillPercent = 30, anonymityMinutes = 5, anonymityMembers = 4),
            maxPrivacy = PrivacyModeRemote(ringFillPercent = 80, anonymityMinutes = 7, anonymityMembers = 12),
        )

        val BUNDLED = CoinagePrivacyConfig(
            balanced = VoucherReadiness(20.percents, AnonymityRequirements(anonymityTime = 10.minutes, anonymityMembers = 8)),
            maxPrivacy = VoucherReadiness(90.percents, AnonymityRequirements(anonymityTime = 10.minutes, anonymityMembers = 16)),
        )
    }
}
