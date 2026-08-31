package io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher.Location
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.VoucherUsabilityContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.paramsFor
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.preClassifyCoins
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.preClassifyVouchers
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageAssetState
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageTransactionStatus
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedCoin
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedVoucher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.math.BigInteger

private const val FULL_RING = 767

class CoinagePreClassificationTest {
    private val minPrivacy = ParametricRecyclingStrategy(RecyclingStrategyType.MIN_PRIVACY.paramsFor(FORCED_AGE))
    private val maxPrivacy = ParametricRecyclingStrategy(RecyclingStrategyType.MAX_PRIVACY.paramsFor(FORCED_AGE))

    @Test
    fun `a settled coin is minted`() {
        val coin = coinOf(age = Coin.Age.Known(3), onChain = true)

        val buckets = listOf(tracked(coin)).preClassifyCoins()

        assertEquals(listOf(coin), buckets.minted)
        assertEquals(emptyList<Coin>(), buckets.minting)
    }

    /**
     * The guard the strategies lean on: gating needs an age, and a coin whose age nothing has read yet must
     * never be picked for recycling. Asserted on the bucket rather than on the sentinel that produces it.
     */
    @Test
    fun `an on-chain coin whose age is unknown is never minted`() {
        val coin = coinOf(age = Coin.Age.Unknown, onChain = true)

        val buckets = listOf(tracked(coin, state = CoinageAssetState.UNTRACKED)).preClassifyCoins()

        assertEquals(emptyList<Coin>(), buckets.minted)
    }

    @Test
    fun `a coin absent from chain is minting only while its mint is live`() {
        val arriving = coinOf(age = Coin.Age.Unknown, onChain = false, derivationIndex = 1)
        val failed = coinOf(age = Coin.Age.Unknown, onChain = false, derivationIndex = 2)

        val buckets = listOf(
            tracked(arriving, minterStatus = CoinageTransactionStatus.PENDING),
            tracked(failed, minterStatus = CoinageTransactionStatus.FAILURE),
        ).preClassifyCoins()

        assertEquals(listOf(arriving), buckets.minting)
    }

    /** A coin that will never arrive is not the user's money, so it is left out of the total entirely. */
    @Test
    fun `a coin whose mint failed is in no bucket`() {
        val failed = coinOf(age = Coin.Age.Unknown, onChain = false)

        val buckets = listOf(tracked(failed, minterStatus = CoinageTransactionStatus.FAILURE)).preClassifyCoins()

        assertTrue(buckets.total.isEmpty())
    }

    @Test
    fun `a claimed coin is in no bucket`() {
        val claimed = coinOf(age = Coin.Age.Known(1), onChain = true)
        val state = CoinageAssetState(handedOff = true, minterStatus = null, consumerStatus = null)

        val buckets = listOf(TrackedCoin(claimed, state)).preClassifyCoins()

        assertTrue(buckets.total.isEmpty())
    }

    @Test
    fun `min privacy makes an in-recycler voucher usable at once`() {
        val voucher = voucherOf(Location.InRecycler(RecyclerIndex(BigInteger.ONE), recyclerMembers = 0))

        val buckets = listOf(trackedVoucher(voucher)).preClassifyVouchers(minPrivacy, context())

        assertEquals(listOf(voucher), buckets.usable)
    }

    @Test
    fun `max privacy holds an in-recycler voucher back until the ring is full`() {
        val partial = voucherOf(Location.InRecycler(RecyclerIndex(BigInteger.ONE), recyclerMembers = FULL_RING - 1))

        val buckets = listOf(trackedVoucher(partial)).preClassifyVouchers(maxPrivacy, context())

        assertEquals(listOf(partial), buckets.gainingPrivacy)
        assertTrue(buckets.usable.isEmpty())
    }

    @Test
    fun `an onboarding voucher is minting, whatever the ledger says about its minter`() {
        val onboarding = voucherOf(Location.Onboarding)

        val buckets = listOf(trackedVoucher(onboarding)).preClassifyVouchers(minPrivacy, context())

        assertEquals(listOf(onboarding), buckets.minting)
    }

    @Test
    fun `buckets never overlap`() {
        val usable = voucherOf(Location.InRecycler(RecyclerIndex(BigInteger.ONE), FULL_RING), ringVrfKeyIndex = 1)
        val onboarding = voucherOf(Location.Onboarding, ringVrfKeyIndex = 2)

        val buckets = listOf(trackedVoucher(usable), trackedVoucher(onboarding))
            .preClassifyVouchers(maxPrivacy, context())

        assertEquals(buckets.total.size, buckets.total.distinct().size)
        assertEquals(2, buckets.total.size)
    }

    private fun context() = VoucherUsabilityContext(ringCapacities = mapOf(ValueExponent(1) to FULL_RING))

    private fun tracked(
        coin: Coin,
        minterStatus: CoinageTransactionStatus? = null,
        state: CoinageAssetState = CoinageAssetState(false, minterStatus, null),
    ) = TrackedCoin(coin, state)

    private fun trackedVoucher(voucher: RecyclerVoucher) = TrackedVoucher(
        voucher,
        CoinageAssetState(handedOff = false, minterStatus = CoinageTransactionStatus.PENDING, consumerStatus = null),
    )

    private fun coinOf(age: Coin.Age, onChain: Boolean, derivationIndex: Int = 0) = Coin(
        derivationIndex = derivationIndex,
        valueExponent = ValueExponent(1),
        age = age,
        isOnChain = onChain,
        accountId = mock(),
    )

    private fun voucherOf(location: Location, ringVrfKeyIndex: Int = 0) = RecyclerVoucher(
        ringVrfKeyIndex = ringVrfKeyIndex,
        ringVrfPublicKey = mock(),
        recyclerValue = ValueExponent(1),
        location = location,
    )

    private companion object {
        const val FORCED_AGE = 14
    }
}
