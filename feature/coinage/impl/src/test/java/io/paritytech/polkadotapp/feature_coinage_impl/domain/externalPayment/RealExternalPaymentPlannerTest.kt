package io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentPlan
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.PowerOfTwoConversion
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.CoinageAssetSelector
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.SpendScope
import io.paritytech.polkadotapp.feature_coinage_impl.planks
import io.paritytech.polkadotapp.feature_coinage_impl.testKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which funds pay an external payment. Private funds come first; anything else the chain accepts is only
 * reached for when they fall short, because by then the user has confirmed giving up the privacy.
 *
 * A voucher or coin of exponent `e` is worth 2^e planks.
 */
class RealExternalPaymentPlannerTest {
    private val assetSelector: CoinageAssetSelector = mockk()
    private val converter: CoinageBalanceConverterUseCase = mockk {
        coEvery { create() } returns Result.success(PowerOfTwoConversion)
    }

    private val planner = RealExternalPaymentPlanner(assetSelector, converter)

    @Test
    fun `private vouchers that cover the amount are offboarded`() = runBlocking<Unit> {
        val big = voucher(1, exponent = 3)
        val small = voucher(2, exponent = 1)
        givenFunds(privateVouchers = listOf(small, big))

        val plan = planner.plan(planks(6)).getOrThrow()

        assertTrue(plan is ExternalPaymentPlan.Ready)
        plan as ExternalPaymentPlan.Ready
        assertEquals(listOf(big), plan.offboarding.vouchers)
        assertEquals(planks(2), plan.offboarding.surplus)
    }

    /** Coins are recycled for the difference, and every private voucher is offboarded next to what they become. */
    @Test
    fun `private coins make up what private vouchers lack`() = runBlocking<Unit> {
        val privateVoucher = voucher(1, exponent = 2)
        val coinToLoad = coin(2, exponent = 3)
        givenFunds(privateVouchers = listOf(privateVoucher), privateCoins = listOf(coin(3, exponent = 1), coinToLoad))

        val plan = planner.plan(planks(10)).getOrThrow()

        assertTrue(plan is ExternalPaymentPlan.LoadCoins)
        plan as ExternalPaymentPlan.LoadCoins
        assertEquals(listOf(coinToLoad), plan.coinsToLoad)
        assertEquals(listOf(privateVoucher), plan.exactVouchers)
    }

    /**
     * Privacy-gaining vouchers alone would pay at once, but private coins can pay too, at the cost of waiting for
     * them to be recycled. Waiting costs nothing but time; spending the vouchers costs the user privacy.
     */
    @Test
    fun `private funds are preferred even when privacy-gaining vouchers would pay at once`() = runBlocking<Unit> {
        val privateVoucher = voucher(1, exponent = 2)
        givenFunds(
            privateVouchers = listOf(privateVoucher),
            privateCoins = listOf(coin(2, exponent = 3)),
            onChainVouchers = listOf(privateVoucher, voucher(3, exponent = 4)),
        )

        val plan = planner.plan(planks(10)).getOrThrow()

        assertTrue(plan is ExternalPaymentPlan.LoadCoins)
        coVerify(exactly = 0) { assetSelector.getOnChainSpendableVouchers() }
    }

    @Test
    fun `vouchers still gaining privacy pay when private funds fall short`() = runBlocking<Unit> {
        val privateVoucher = voucher(1, exponent = 1)
        val gainingPrivacy = voucher(2, exponent = 3)
        givenFunds(privateVouchers = listOf(privateVoucher), onChainVouchers = listOf(privateVoucher, gainingPrivacy))

        val plan = planner.plan(planks(8)).getOrThrow()

        assertTrue(plan is ExternalPaymentPlan.Ready)
        plan as ExternalPaymentPlan.Ready
        assertEquals(listOf(gainingPrivacy), plan.offboarding.vouchers)
    }

    /** A coin the strategy wanted recycled for privacy is still money the chain accepts. */
    @Test
    fun `any coin the chain accepts makes up the rest`() = runBlocking<Unit> {
        val onChainVoucher = voucher(1, exponent = 1)
        val coinHeldBack = coin(2, exponent = 3)
        givenFunds(onChainVouchers = listOf(onChainVoucher), onChainCoins = listOf(coinHeldBack))

        val plan = planner.plan(planks(9)).getOrThrow()

        assertTrue(plan is ExternalPaymentPlan.LoadCoins)
        plan as ExternalPaymentPlan.LoadCoins
        assertEquals(listOf(coinHeldBack), plan.coinsToLoad)
        assertEquals(listOf(onChainVoucher), plan.exactVouchers)
    }

    @Test
    fun `an amount nothing covers is reported with what there was`() = runBlocking<Unit> {
        givenFunds(onChainVouchers = listOf(voucher(1, exponent = 1)), onChainCoins = listOf(coin(2, exponent = 2)))

        val plan = planner.plan(planks(16)).getOrThrow()

        assertEquals(
            ExternalPaymentPlan.NotEnoughAmount(
                activeVouchers = planks(2),
                activeCoins = planks(4),
                deficitToCoverWithCoins = planks(14),
            ),
            plan,
        )
    }

    private fun givenFunds(
        privateVouchers: List<RecyclerVoucher> = emptyList(),
        privateCoins: List<Coin> = emptyList(),
        onChainVouchers: List<RecyclerVoucher> = privateVouchers,
        onChainCoins: List<Coin> = privateCoins,
    ) {
        coEvery { assetSelector.getSelectableVouchers(SpendScope.SPENDABLE) } returns privateVouchers
        coEvery { assetSelector.getSelectableCoins(SpendScope.SPENDABLE) } returns privateCoins
        coEvery { assetSelector.getOnChainSpendableVouchers() } returns onChainVouchers
        coEvery { assetSelector.getOnChainSpendableCoins() } returns onChainCoins
    }

    private fun voucher(index: Int, exponent: Int) = RecyclerVoucher(
        ringVrfKeyIndex = testKey(index),
        ringVrfPublicKey = byteArrayOf(index.toByte()).toDataByteArray(),
        recyclerValue = ValueExponent(exponent),
        location = RecyclerVoucher.Location.Unknown,
    )

    private fun coin(index: Int, exponent: Int) = Coin(
        derivationIndex = testKey(index),
        valueExponent = ValueExponent(exponent),
        age = Coin.Age.Known(0),
        isOnChain = true,
        accountId = byteArrayOf(index.toByte()).intoAccountId(),
    )
}
