package io.paritytech.polkadotapp.feature_coinage_impl.data.mappers

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.mockk.mockk
import io.paritytech.polkadotapp.common.utils.Fraction.Companion.percents
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerFungibility
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.AnonymityRequirements
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.CoinagePrivacyConfig
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.VoucherReadiness
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.params
import io.paritytech.polkadotapp.feature_coinage_impl.data.config.CoinagePrivacyConfigRemote
import io.paritytech.polkadotapp.feature_coinage_impl.data.config.PrivacyModeRemote
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.FetchedVoucherUsabilityContext
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.ParametricRecyclingStrategy
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.forcedAgeOf
import io.paritytech.polkadotapp.feature_coinage_impl.testKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CoinagePrivacyConfigMapperTest {
    @Test
    fun `published values are kept`() {
        val remote = CoinagePrivacyConfigRemote(
            balanced = PrivacyModeRemote(ringFillPercent = 30, anonymityMinutes = 5, anonymityMembers = 4),
            maxPrivacy = PrivacyModeRemote(ringFillPercent = 80, anonymityMinutes = 7, anonymityMembers = 12),
        )

        assertEquals(CoinagePrivacyConfig(readiness(30, 5, 4), readiness(80, 7, 12)), remote.toDomain())
    }

    @Test
    fun `an unset key maps to the bundled defaults`() {
        val unset: CoinagePrivacyConfigRemote? = null

        assertEquals(BUNDLED, unset.toDomain())
    }

    @Test
    fun `an empty object maps to the bundled defaults`() {
        assertEquals(BUNDLED, CoinagePrivacyConfigRemote(balanced = null, maxPrivacy = null).toDomain())
    }

    @Test
    fun `a missing mode takes its own defaults and keeps the other`() {
        val remote = CoinagePrivacyConfigRemote(
            balanced = PrivacyModeRemote(ringFillPercent = 30, anonymityMinutes = 5, anonymityMembers = 4),
            maxPrivacy = null,
        )

        assertEquals(CoinagePrivacyConfig(readiness(30, 5, 4), BUNDLED.maxPrivacy), remote.toDomain())
    }

    @Test
    fun `a missing field takes its own default`() {
        val remote = CoinagePrivacyConfigRemote(
            balanced = PrivacyModeRemote(ringFillPercent = 30, anonymityMinutes = null, anonymityMembers = null),
            maxPrivacy = PrivacyModeRemote(ringFillPercent = null, anonymityMinutes = 7, anonymityMembers = 12),
        )

        assertEquals(CoinagePrivacyConfig(readiness(30, 10, 8), readiness(90, 7, 12)), remote.toDomain())
    }

    @Test
    fun `an out of range field takes its own default`() {
        val cases = listOf(
            PrivacyModeRemote(ringFillPercent = 30, anonymityMinutes = 5, anonymityMembers = 0) to readiness(30, 5, 8),
            PrivacyModeRemote(ringFillPercent = 30, anonymityMinutes = 5, anonymityMembers = -1) to readiness(30, 5, 8),
            PrivacyModeRemote(ringFillPercent = 30, anonymityMinutes = 0, anonymityMembers = 4) to readiness(30, 10, 4),
            PrivacyModeRemote(ringFillPercent = 30, anonymityMinutes = -5, anonymityMembers = 4) to readiness(30, 10, 4),
            PrivacyModeRemote(ringFillPercent = -1, anonymityMinutes = 5, anonymityMembers = 4) to readiness(20, 5, 4),
            PrivacyModeRemote(ringFillPercent = 101, anonymityMinutes = 5, anonymityMembers = 4) to readiness(20, 5, 4),
        )

        cases.forEach { (balanced, expected) ->
            val mapped = CoinagePrivacyConfigRemote(balanced = balanced, maxPrivacy = null).toDomain()

            assertEquals(expected, mapped.balanced)
        }
    }

    @Test
    fun `a zero ring fill is kept`() {
        val remote = CoinagePrivacyConfigRemote(
            balanced = PrivacyModeRemote(ringFillPercent = 0, anonymityMinutes = 5, anonymityMembers = 4),
            maxPrivacy = null,
        )

        assertEquals(readiness(0, 5, 4), remote.toDomain().balanced)
    }

    @Test
    fun `an invalid anonymity budget never releases a balanced voucher at once`() {
        val invalid = listOf(0, -1, null)
        val modes = invalid.map { PrivacyModeRemote(ringFillPercent = null, anonymityMinutes = it, anonymityMembers = 8) } +
            invalid.map { PrivacyModeRemote(ringFillPercent = null, anonymityMinutes = 10, anonymityMembers = it) }
        val voucher = RecyclerVoucher(
            ringVrfKeyIndex = testKey(0),
            ringVrfPublicKey = mockk(),
            recyclerValue = ValueExponent(1),
            location = RecyclerVoucher.Location.InRecycler(RecyclerIndex(BigInteger.ONE), 8, ENTERED_AT, recyclerUnloaded = 0),
            recyclerFungibility = RecyclerFungibility.NONE,
            maxRecyclerFungibility = RecyclerFungibility.NONE,
        )
        val context = FetchedVoucherUsabilityContext(ringCapacities = mapOf(ValueExponent(1) to 767), now = ENTERED_AT)

        modes.forEach { mode ->
            val config = CoinagePrivacyConfigRemote(balanced = mode, maxPrivacy = null).toDomain()
            val strategy = ParametricRecyclingStrategy(RecyclingStrategyType.BALANCED.params(config), forcedAgeOf(14))

            assertFalse(
                "minutes ${mode.anonymityMinutes}, members ${mode.anonymityMembers}",
                strategy.isVoucherUsable(voucher, context),
            )
        }
    }

    @Test
    fun `the published json field names map onto the config`() {
        val documented = """{"balanced":{"ringFillPercent":20,"anonymityMinutes":10,"anonymityMembers":8},""" +
            """"maxPrivacy":{"ringFillPercent":90,"anonymityMinutes":10,"anonymityMembers":16}}"""
        val custom = """{"balanced":{"ringFillPercent":30,"anonymityMinutes":5,"anonymityMembers":4},""" +
            """"maxPrivacy":{"ringFillPercent":80,"anonymityMinutes":7,"anonymityMembers":12}}"""

        assertEquals(BUNDLED, documented.parsedConfig())
        assertEquals(CoinagePrivacyConfig(readiness(30, 5, 4), readiness(80, 7, 12)), custom.parsedConfig())
    }

    @Test
    fun `a quoted integer is read as the number it holds`() {
        val quoted = """{"balanced":{"ringFillPercent":"20","anonymityMinutes":10,"anonymityMembers":8}}"""

        assertEquals(readiness(20, 10, 8), quoted.parsedConfig().balanced)
    }

    @Test
    fun `a fractional number does not parse at all`() {
        val fractional = """{"balanced":{"ringFillPercent":20.5,"anonymityMinutes":10,"anonymityMembers":8}}"""

        assertThrows(JsonSyntaxException::class.java) { fractional.parsedConfig() }
    }

    private fun String.parsedConfig() = Gson().fromJson(this, CoinagePrivacyConfigRemote::class.java).toDomain()

    private fun readiness(ringFillPercent: Int, anonymityMinutes: Int, anonymityMembers: Int) = VoucherReadiness(
        requiredRingFill = ringFillPercent.percents,
        anonymityRequirements = AnonymityRequirements(anonymityMinutes.minutes, anonymityMembers),
    )

    private companion object {
        val ENTERED_AT = Instant.fromEpochMilliseconds(0)

        val BUNDLED = CoinagePrivacyConfig(
            balanced = VoucherReadiness(20.percents, AnonymityRequirements(anonymityTime = 10.minutes, anonymityMembers = 8)),
            maxPrivacy = VoucherReadiness(90.percents, AnonymityRequirements(anonymityTime = 10.minutes, anonymityMembers = 16)),
        )
    }
}
