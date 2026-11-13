package io.paritytech.polkadotapp.feature_coinage_impl.data.config

import io.mockk.coEvery
import io.mockk.mockk
import io.paritytech.polkadotapp.common.utils.Fraction.Companion.percents
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.AnonymityRequirements
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.CoinagePrivacyConfig
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.VoucherReadiness
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.params
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.FULL_RING_CAPACITY
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.FetchedVoucherUsabilityContext
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.ParametricRecyclingStrategy
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.forcedAgeOf
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.voucherOf
import io.paritytech.polkadotapp.test_shared.TestCoroutineDispatchers
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import io.paritytech.polkadotapp.tools_remoteconfig_api.getJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RemoteConfigCoinagePrivacyConfigProviderTest {
    private val remoteConfigService: RemoteConfigService = mockk()
    private val provider = RemoteConfigCoinagePrivacyConfigProvider(
        remoteConfigService,
        TestCoroutineDispatchers(Dispatchers.Unconfined),
    )

    @Test
    fun `published values replace the defaults for balanced and max privacy`() = runBlocking<Unit> {
        withRemote(Result.success(PUBLISHED))

        val config = provider.privacyConfigOrDefault()

        assertEquals(VoucherReadiness(30.percents, AnonymityRequirements(5.minutes, 4)), config.balanced)
        assertEquals(VoucherReadiness(80.percents, AnonymityRequirements(7.minutes, 12)), config.maxPrivacy)
    }

    @Test
    fun `a published config changes when a balanced voucher becomes ready`() = runBlocking<Unit> {
        withRemote(Result.success(PUBLISHED))
        val published = provider.privacyConfigOrDefault()
        withRemote(Result.success(null))
        val bundled = provider.privacyConfigOrDefault()

        assertFalse(published.balancedUsableAfter(5.minutes - 1.milliseconds))
        assertTrue(published.balancedUsableAfter(5.minutes))
        assertFalse(bundled.balancedUsableAfter(5.minutes))
    }

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

    private fun CoinagePrivacyConfig.balancedUsableAfter(elapsed: Duration): Boolean {
        val voucher = voucherOf(RecyclerVoucher.Location.InRecycler(RecyclerIndex(BigInteger.ONE), 4, ENTERED_AT, recyclerUnloaded = 0))
        val context = FetchedVoucherUsabilityContext(mapOf(ValueExponent(1) to FULL_RING_CAPACITY), now = ENTERED_AT + elapsed)

        return ParametricRecyclingStrategy(RecyclingStrategyType.BALANCED.params(this), forcedAgeOf(14))
            .isVoucherUsable(voucher, context)
    }

    private companion object {
        val ENTERED_AT = Instant.fromEpochMilliseconds(0)

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
