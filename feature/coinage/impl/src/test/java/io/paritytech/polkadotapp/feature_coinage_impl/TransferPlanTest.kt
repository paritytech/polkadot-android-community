package io.paritytech.polkadotapp.feature_coinage_impl

import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.common.utils.emptySubstrateAccountId
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinAmountBreakdown
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher.Location
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.StrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.common.centsToDollar
import io.paritytech.polkadotapp.feature_coinage_impl.common.coinageTestPrecision
import io.paritytech.polkadotapp.feature_coinage_impl.common.testConversionContext
import io.paritytech.polkadotapp.feature_coinage_impl.domain.common.RealCoinAmountBreakdownContext
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.TransferPlanner
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.exceptions.InsufficientBalanceException
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.math.BigDecimal
import java.math.BigInteger

class TransferPlannerTest {
    private val allowedExponents = (-2..7).map { ValueExponent(it) }.toSet()

    private val realBreakdown: CoinAmountBreakdown = RealCoinAmountBreakdownContext(coinageTestPrecision, testConversionContext, allowedExponents)
    private val planner = TransferPlanner(testConversionContext, realBreakdown, 16)

    private val currentTimestamp: Timestamp = 1000L

    @Test
    fun `Strategy 1 - ExactMatch - should return exact match when coins sum matches perfectly`() = runBlocking {
        val coins = listOf(createCoin(exponent = 2), createCoin(exponent = 0))

        val plan = planner.plan(5.0.centsToDollar(), coins, emptyList())

        val strategy = plan.strategyType as StrategyType.ExactCoins
        assertCoinExponents(listOf(2, 0), strategy.coins)
    }

    @Test
    fun `Strategy 2 - SingleSplit - should split a single larger coin when no partial coverage possible`() = runBlocking {
        val coins = listOf(createCoin(exponent = 3)) // 8 > 5, doesn't fit as coverage → split entirely

        val plan = planner.plan(5.0.centsToDollar(), coins, emptyList())

        val split = plan.strategyType as StrategyType.Split
        assertEquals(ValueExponent(3), split.splitFrom.valueExponent)
        assertExponents(listOf(2, 0), split.recipientDenominations)
        assertExponents(listOf(1, 0), split.changeDenominations)
        assertCoinExponents(emptyList(), split.exactCoins)
    }

    @Test
    fun `Strategy 2 - SingleSplit - should use existing coins to partially cover amount and split remainder`() = runBlocking {
        val coins = listOf(
            createCoin(exponent = 3), // 8 — fits as coverage
            createCoin(exponent = 2), // 4 — split candidate
            createCoin(exponent = 2) // 4 — extra, not used
        )

        val plan = planner.plan(9.0.centsToDollar(), coins, emptyList())

        val split = plan.strategyType as StrategyType.Split
        assertEquals(ValueExponent(2), split.splitFrom.valueExponent)
        assertExponents(listOf(0), split.recipientDenominations)
        assertExponents(listOf(1, 0), split.changeDenominations)
        assertCoinExponents(listOf(3), split.exactCoins)
    }

    @Test
    fun `Strategy 2 - Split - splitFrom value equals sum of recipient and change denominations`() = runBlocking {
        val coins = listOf(createCoin(exponent = 2)) // 4 > 3

        val plan = planner.plan(3.0.centsToDollar(), coins, emptyList())

        val split = plan.strategyType as StrategyType.Split
        assertEquals(ValueExponent(2), split.splitFrom.valueExponent)
        assertExponents(listOf(1, 0), split.recipientDenominations)
        assertExponents(listOf(0), split.changeDenominations)
    }

    @Test
    fun `Strategy 3 - CoinsAndUnload - should use coins first and unload vouchers for remainder`() = runBlocking {
        val coins = listOf(createCoin(exponent = 2))
        val vouchers = listOf(
            createVoucher(exponent = 1, isReady = true, RingIndex(BigInteger.ONE)),
            createVoucher(exponent = 1, isReady = true, RingIndex(BigInteger.ZERO))
        )

        val plan = planner.plan(5.5.centsToDollar(), coins, vouchers)

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        assertVoucherExponents(listOf(1), strategy.vouchersToUnload)
        assertAmountEquals(1.5.centsToDollar(), strategy.recipientAmount)
        assertCoinExponents(listOf(2), strategy.exactCoins)
    }

    @Test
    fun `Strategy 3_1 - CoinsAndUnload - should use coins first and unload vouchers for remainder`() = runBlocking {
        val coins = listOf(createCoin(exponent = 2))
        val vouchers = listOf(
            createVoucher(exponent = 1, isReady = true, RingIndex(BigInteger.ZERO)),
            createVoucher(exponent = 1, isReady = true, RingIndex(BigInteger.ZERO))
        )

        val plan = planner.plan(5.5.centsToDollar(), coins, vouchers)

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        // Single voucher (=2) covers the 1.5 remainder; the second voucher in the same ring stays.
        assertVoucherExponents(listOf(1), strategy.vouchersToUnload)
        assertAmountEquals(1.5.centsToDollar(), strategy.recipientAmount)
    }

    @Test(expected = InsufficientBalanceException::class)
    fun `should throw exception when not enough funds`(): Unit = runBlocking {
        val coins = listOf(createCoin(exponent = 2))

        planner.plan(10.0.centsToDollar(), coins, emptyList())
    }

    @Test
    fun `Minimal cover - picks subset of a same-ring group when it already covers remainder`() = runBlocking {
        // No coins, target 1.5 cents. Two vouchers exp=1 (=2 cents each) live in the SAME ring,
        // so they form one consolidation group of total value 4 cents. A single voucher already
        // covers the 1.5 remaining; the planner must NOT unload both.
        val ring = RingIndex(BigInteger.ZERO)
        val voucher1 = createVoucher(exponent = 1, isReady = true, index = ring)
        val voucher2 = createVoucher(exponent = 1, isReady = true, index = ring)

        val plan = planner.plan(1.5.centsToDollar(), emptyList(), listOf(voucher1, voucher2))

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        assertVoucherExponents(listOf(1), strategy.vouchersToUnload)
    }

    @Test
    fun `Secured vs degraded - prefers secured voucher when it alone can cover remainder`() = runBlocking {
        // 5.5 = 4 (coin exp=2) + 1.5 from vouchers. Either voucher alone (exp=1, =2) can cover.
        val coins = listOf(createCoin(exponent = 2))
        val securedVoucher = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ONE), enoughMembers = true)
        val degradedVoucher = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ZERO), enoughMembers = false)

        val plan = planner.plan(5.5.centsToDollar(), coins, listOf(securedVoucher, degradedVoucher))

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        assertEquals(listOf(securedVoucher), strategy.vouchersToUnload)
    }

    @Test
    fun `Secured vs degraded - falls back to degraded when secured alone is insufficient`() = runBlocking {
        // 7.5 = 4 (coin) + 3.5 from vouchers. Secured alone (=2) is not enough → must include degraded.
        val coins = listOf(createCoin(exponent = 2))
        val securedVoucher = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ONE), enoughMembers = true)
        val degradedVoucher1 = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ZERO), enoughMembers = false)
        val degradedVoucher2 = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.valueOf(2)), enoughMembers = false)

        val plan = planner.plan(7.5.centsToDollar(), coins, listOf(securedVoucher, degradedVoucher1, degradedVoucher2))

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        // Secured is exhausted first (2), then one degraded group covers the rest (2 → covers remaining 1.5).
        assertEquals(2, strategy.vouchersToUnload.size)
        assertTrue(strategy.vouchersToUnload.any { it === securedVoucher })
        assertEquals(1, strategy.vouchersToUnload.count { !it.ringHasEnoughRingMembersToWithdraw })
    }

    @Test
    fun `Secured vs degraded - uses degraded only when no secured is available`() = runBlocking {
        val coins = listOf(createCoin(exponent = 2))
        // No secured vouchers — only degraded, in different rings.
        val degraded1 = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ZERO), enoughMembers = false)
        val degraded2 = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ONE), enoughMembers = false)

        val plan = planner.plan(5.5.centsToDollar(), coins, listOf(degraded1, degraded2))

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        assertEquals(1, strategy.vouchersToUnload.size)
        assertTrue(strategy.vouchersToUnload.none { it.ringHasEnoughRingMembersToWithdraw })
    }

    @Test(expected = InsufficientBalanceException::class)
    fun `Secured vs degraded - throws when secured plus degraded combined cannot cover`(): Unit = runBlocking {
        val securedVoucher = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ONE), enoughMembers = true)
        val degradedVoucher = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ZERO), enoughMembers = false)

        planner.plan(100.0.centsToDollar(), emptyList(), listOf(securedVoucher, degradedVoucher))
    }

    @Test
    fun `Secured ordering - within secured bucket, larger groups are picked first`() = runBlocking {
        // 9.5 from vouchers only. Three secured groups of values $2, $4, $8 in distinct rings.
        // Greedy descending: $8 → remaining 1.5 → $4 → remaining -2.5. Final picks: only the
        // top two (exp=2 + exp=3); $2 group is untouched.
        val small = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ONE), enoughMembers = true)
        val medium = createVoucher(exponent = 2, isReady = true, index = RingIndex(BigInteger.valueOf(2)), enoughMembers = true)
        val large = createVoucher(exponent = 3, isReady = true, index = RingIndex(BigInteger.valueOf(3)), enoughMembers = true)

        val plan = planner.plan(9.5.centsToDollar(), emptyList(), listOf(small, medium, large))

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        assertEquals(setOf(large, medium), strategy.vouchersToUnload.toSet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `should throw exception when target amount cannot be exactly represented by min exponent`(): Unit = runBlocking {
        val coins = listOf(createCoin(exponent = 2))

        planner.plan(10.10.centsToDollar(), coins, emptyList())
    }

    private fun assertCoinExponents(expected: List<Int>, coins: List<Coin>) =
        assertEquals(expected, coins.map { it.valueExponent.value })

    private fun assertExponents(expected: List<Int>, exponents: List<ValueExponent>) =
        assertEquals(expected, exponents.map { it.value })

    private fun assertVoucherExponents(expected: List<Int>, vouchers: List<RecyclerVoucher>) =
        assertEquals(expected, vouchers.map { it.recyclerValue.value })

    private fun assertAmountEquals(expected: BigDecimal, actual: BigDecimal) =
        assertEquals(0, actual.compareTo(expected))

    private var coinIndexCounter = 0

    private fun createCoin(exponent: Int, isSpent: Boolean = false): Coin {
        return Coin(
            derivationIndex = coinIndexCounter++,
            valueExponent = ValueExponent(exponent),
            age = Coin.Age.Known(0),
            spentState = if (isSpent) Coin.SpentState.SPENT_ON_CHAIN else Coin.SpentState.NOT_SPENT,
            accountId = emptySubstrateAccountId()
        )
    }

    private var voucherIndexCounter = 0

    private fun createVoucher(
        exponent: Int,
        isReady: Boolean,
        index: RecyclerIndex = RecyclerIndex(BigInteger.ONE),
        enoughMembers: Boolean = true
    ): RecyclerVoucher {
        val delayUnloadUntil = if (isReady) currentTimestamp - 100L else currentTimestamp + 1000L

        return RecyclerVoucher(
            ringVrfKeyIndex = voucherIndexCounter++,
            ringVrfPublicKey = mock(),
            recyclerValue = ValueExponent(exponent),
            location = Location.InRecycler(index),
            allocatedAt = 0L,
            delayUnloadUntil = delayUnloadUntil,
            usageState = RecyclerVoucher.UsageState.NOT_USED,
            ringHasEnoughRingMembersToWithdraw = enoughMembers
        )
    }
}
