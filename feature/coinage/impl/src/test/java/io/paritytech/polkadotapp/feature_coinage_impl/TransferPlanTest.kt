package io.paritytech.polkadotapp.feature_coinage_impl

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

    @Test
    fun `Strategy 1 - ExactMatch - should return exact match when coins sum matches perfectly`() = runBlocking {
        val coins = listOf(createCoin(exponent = 2), createCoin(exponent = 0))

        val plan = planner.plan(5.0.centsToDollar(), coins, emptyList()).getOrThrow()

        val strategy = plan.strategyType as StrategyType.ExactCoins
        assertCoinExponents(listOf(2, 0), strategy.coins)
    }

    @Test
    fun `Strategy 2 - Split - should split a single larger coin when no partial coverage possible`() = runBlocking {
        val coins = listOf(createCoin(exponent = 3)) // 8 > 5, doesn't fit as coverage → split entirely

        val plan = planner.plan(5.0.centsToDollar(), coins, emptyList()).getOrThrow()

        val split = plan.strategyType as StrategyType.Split
        assertSplits(listOf(split(from = 3, recipient = listOf(2, 0), change = listOf(1, 0))), split)
        assertCoinExponents(emptyList(), split.exactCoins)
    }

    @Test
    fun `Strategy 2 - Split - should use existing coins to partially cover amount and split remainder`() = runBlocking {
        val coins = listOf(
            createCoin(exponent = 3), // 8 — fits as coverage
            createCoin(exponent = 2), // 4 — stays whole with the sender
            createCoin(exponent = 2) // 4 — split candidate
        )

        val plan = planner.plan(9.0.centsToDollar(), coins, emptyList()).getOrThrow()

        val split = plan.strategyType as StrategyType.Split
        assertSplits(listOf(split(from = 2, recipient = listOf(0), change = listOf(1, 0))), split)
        assertCoinExponents(listOf(3), split.exactCoins)
    }

    @Test
    fun `Strategy 2 - Split - splitFrom value equals sum of recipient and change denominations`() = runBlocking {
        val coins = listOf(createCoin(exponent = 2)) // 4 > 3

        val plan = planner.plan(3.0.centsToDollar(), coins, emptyList()).getOrThrow()

        val split = plan.strategyType as StrategyType.Split
        assertSplits(listOf(split(from = 2, recipient = listOf(1, 0), change = listOf(0))), split)
    }

    @Test
    fun `Strategy 2 - Split - creates one coin where dividing the big coin would create seven`() = runBlocking {
        // 65 = 64 + 1 against coins 1 and 128: only index 7 is forced, so the 128 halves and the 1c coin goes as is.
        val coins = listOf(createCoin(exponent = 0), createCoin(exponent = 7))

        val plan = planner.plan(65.0.centsToDollar(), coins, emptyList()).getOrThrow()

        val split = plan.strategyType as StrategyType.Split
        assertSplits(listOf(split(from = 7, recipient = listOf(6), change = listOf(6))), split)
        assertCoinExponents(listOf(0), split.exactCoins)
    }

    @Test
    fun `Strategy 2 - Split - makes one split per run of forced indices`() = runBlocking {
        // 19 = 16 + 2 + 1 against coins 4, 64, 128: forced indices are {1, 2} and {5, 6}; the 128 is not needed.
        val coins = listOf(createCoin(exponent = 2), createCoin(exponent = 6), createCoin(exponent = 7))

        val plan = planner.plan(19.0.centsToDollar(), coins, emptyList()).getOrThrow()

        val split = plan.strategyType as StrategyType.Split
        assertSplits(
            listOf(
                split(from = 2, recipient = listOf(1, 0), change = listOf(0)),
                split(from = 6, recipient = listOf(4), change = listOf(5, 4))
            ),
            split
        )
        assertCoinExponents(emptyList(), split.exactCoins)
    }

    @Test
    fun `Strategy 2 - Split - neither splits nor hands off a coin past recycling age`() = runBlocking {
        val agedCoin = createCoin(exponent = 2, ageKnown = 20) // 4c, would cover the amount exactly
        val youngCoin = createCoin(exponent = 3)

        val plan = planner.plan(4.0.centsToDollar(), listOf(agedCoin, youngCoin), emptyList()).getOrThrow()

        val split = plan.strategyType as StrategyType.Split
        assertEquals(listOf(youngCoin), split.splits.map { it.splitFrom })
        assertSplits(listOf(split(from = 3, recipient = listOf(2), change = listOf(2))), split)
        assertCoinExponents(emptyList(), split.exactCoins)
    }

    @Test
    fun `Strategy 3 - CoinsAndUnload - should use coins first and unload vouchers for remainder`() = runBlocking {
        val coins = listOf(createCoin(exponent = 2))
        val vouchers = listOf(
            createVoucher(exponent = 1, isReady = true, RingIndex(BigInteger.ONE)),
            createVoucher(exponent = 1, isReady = true, RingIndex(BigInteger.ZERO))
        )

        val plan = planner.plan(5.5.centsToDollar(), coins, vouchers).getOrThrow()

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

        val plan = planner.plan(5.5.centsToDollar(), coins, vouchers).getOrThrow()

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        // Single voucher (=2) covers the 1.5 remainder; the second voucher in the same ring stays.
        assertVoucherExponents(listOf(1), strategy.vouchersToUnload)
        assertAmountEquals(1.5.centsToDollar(), strategy.recipientAmount)
    }

    @Test
    fun `Strategy 3 - CoinsAndUnload - hands off no coins when the vouchers alone match the amount`() = runBlocking {
        // Maximal coverage would hand off the 1c coin and break the 8c voucher into 4 + 2 + 1 plus 1 change.
        val coins = listOf(createCoin(exponent = 0))
        val voucher = createVoucher(exponent = 3, isReady = true)

        val plan = planner.plan(8.0.centsToDollar(), coins, listOf(voucher)).getOrThrow()

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        assertVoucherExponents(listOf(3), strategy.vouchersToUnload)
        assertAmountEquals(8.0.centsToDollar(), strategy.recipientAmount)
        assertCoinExponents(emptyList(), strategy.exactCoins)
    }

    @Test
    fun `Strategy 3 - CoinsAndUnload - hands off the coins that spare the unloaded value a split`() = runBlocking {
        // 5 = 4 + 1: the 1c coin covers the low bit, so the 8c voucher only halves.
        val coins = listOf(createCoin(exponent = 0))
        val voucher = createVoucher(exponent = 3, isReady = true)

        val plan = planner.plan(5.0.centsToDollar(), coins, listOf(voucher)).getOrThrow()

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        assertAmountEquals(4.0.centsToDollar(), strategy.recipientAmount)
        assertCoinExponents(listOf(0), strategy.exactCoins)
    }

    @Test
    fun `Strategy 3 - CoinsAndUnload - falls back to maximal coverage when the best plan would split an own coin`() = runBlocking {
        // 9 = 8 + 1 against a 4c coin and an 8c voucher: the optimal plan splits the 4c coin, which an unload cannot do.
        val coins = listOf(createCoin(exponent = 2))
        val voucher = createVoucher(exponent = 3, isReady = true)

        val plan = planner.plan(9.0.centsToDollar(), coins, listOf(voucher)).getOrThrow()

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        assertAmountEquals(5.0.centsToDollar(), strategy.recipientAmount)
        assertCoinExponents(listOf(2), strategy.exactCoins)
    }

    @Test(expected = InsufficientBalanceException::class)
    fun `should throw exception when not enough funds`(): Unit = runBlocking {
        val coins = listOf(createCoin(exponent = 2))

        planner.plan(10.0.centsToDollar(), coins, emptyList()).getOrThrow()
    }

    @Test
    fun `Non-spendable coin - is ignored consistently so a valid unload plan is produced`() = runBlocking {
        // Regression: a NOT_SPENT but past-recycling-age coin (age >= 16) is excluded by exact-match
        // (findSubsetSum filters by age) but was previously INCLUDED by coverage
        // (findMaxCoinCoverage did not filter). Coverage then reached the amount exactly
        // (restAmount == 0) and crashed with IllegalStateException("Not needed to split coins").
        // The non-spendable coin must be ignored everywhere, leaving a valid unload plan.
        val nonSpendableCoin = createCoin(exponent = 2, ageKnown = 20) // 4 cents, age >= 16 → not spendable
        val spendableCoin = createCoin(exponent = 1) // 2 cents, spendable
        val voucher = createVoucher(exponent = 2, isReady = true) // 4 cents

        val plan = planner.plan(6.0.centsToDollar(), listOf(nonSpendableCoin, spendableCoin), listOf(voucher))
            .getOrThrow()

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        assertVoucherExponents(listOf(2), strategy.vouchersToUnload)
        assertAmountEquals(4.0.centsToDollar(), strategy.recipientAmount)
        // The non-spendable coin must NOT be selected as an exact coin; only the spendable 2-cent coin is.
        assertCoinExponents(listOf(1), strategy.exactCoins)
        assertTrue(strategy.exactCoins.none { it === nonSpendableCoin })
    }

    @Test(expected = InsufficientBalanceException::class)
    fun `Non-spendable coin - does not mask insufficient balance as illegal state`(): Unit = runBlocking {
        // Same non-spendable coin, but no other funds to cover the remainder. The planner must
        // surface InsufficientBalanceException, not IllegalStateException("Not needed to split coins").
        val nonSpendableCoin = createCoin(exponent = 2, ageKnown = 20) // 4 cents, not spendable
        val spendableCoin = createCoin(exponent = 1) // 2 cents

        planner.plan(6.0.centsToDollar(), listOf(nonSpendableCoin, spendableCoin), emptyList()).getOrThrow()
    }

    @Test
    fun `Minimal cover - picks subset of a same-ring group when it already covers remainder`() = runBlocking {
        // No coins, target 1.5 cents. Two vouchers exp=1 (=2 cents each) live in the SAME ring,
        // so they form one consolidation group of total value 4 cents. A single voucher already
        // covers the 1.5 remaining; the planner must NOT unload both.
        val ring = RingIndex(BigInteger.ZERO)
        val voucher1 = createVoucher(exponent = 1, isReady = true, index = ring)
        val voucher2 = createVoucher(exponent = 1, isReady = true, index = ring)

        val plan = planner.plan(1.5.centsToDollar(), emptyList(), listOf(voucher1, voucher2)).getOrThrow()

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        assertVoucherExponents(listOf(1), strategy.vouchersToUnload)
    }

    @Test
    fun `takes a single voucher when either one alone can cover the remainder`() = runBlocking {
        // 5.5 = 4 (coin exp=2) + 1.5 from vouchers. Either voucher alone (exp=1, =2) can cover.
        val coins = listOf(createCoin(exponent = 2))
        val first = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ONE))
        val second = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ZERO))

        val plan = planner.plan(5.5.centsToDollar(), coins, listOf(first, second)).getOrThrow()

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        assertEquals(listOf(first), strategy.vouchersToUnload)
    }

    @Test(expected = InsufficientBalanceException::class)
    fun `throws when the vouchers on offer cannot cover the amount`(): Unit = runBlocking {
        val voucher1 = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ONE))
        val voucher2 = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ZERO))

        planner.plan(100.0.centsToDollar(), emptyList(), listOf(voucher1, voucher2)).getOrThrow()
    }

    @Test
    fun `Secured ordering - within secured bucket, larger groups are picked first`() = runBlocking {
        // 9.5 from vouchers only. Three secured groups of values $2, $4, $8 in distinct rings.
        // Greedy descending: $8 → remaining 1.5 → $4 → remaining -2.5. Final picks: only the
        // top two (exp=2 + exp=3); $2 group is untouched.
        val small = createVoucher(exponent = 1, isReady = true, index = RingIndex(BigInteger.ONE))
        val medium = createVoucher(exponent = 2, isReady = true, index = RingIndex(BigInteger.valueOf(2)))
        val large = createVoucher(exponent = 3, isReady = true, index = RingIndex(BigInteger.valueOf(3)))

        val plan = planner.plan(9.5.centsToDollar(), emptyList(), listOf(small, medium, large)).getOrThrow()

        val strategy = plan.strategyType as StrategyType.UnloadAndSplit
        assertEquals(setOf(large, medium), strategy.vouchersToUnload.toSet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `should throw exception when target amount cannot be exactly represented by min exponent`(): Unit = runBlocking {
        val coins = listOf(createCoin(exponent = 2))

        planner.plan(10.10.centsToDollar(), coins, emptyList()).getOrThrow()
    }

    private fun assertCoinExponents(expected: List<Int>, coins: List<Coin>) =
        assertEquals(expected, coins.map { it.valueExponent.value })

    private fun split(from: Int, recipient: List<Int>, change: List<Int>) = Triple(from, recipient, change)

    private fun assertSplits(expected: List<Triple<Int, List<Int>, List<Int>>>, strategy: StrategyType.Split) {
        val actual = strategy.splits.map { split ->
            Triple(split.splitFrom.valueExponent.value, split.recipientDenominations.values(), split.changeDenominations.values())
        }
        assertEquals(expected, actual)
    }

    private fun List<ValueExponent>.values(): List<Int> = map { it.value }

    private fun assertVoucherExponents(expected: List<Int>, vouchers: List<RecyclerVoucher>) =
        assertEquals(expected, vouchers.map { it.recyclerValue.value })

    private fun assertAmountEquals(expected: BigDecimal, actual: BigDecimal) =
        assertEquals(0, actual.compareTo(expected))

    private var coinIndexCounter = 0

    private fun createCoin(exponent: Int, isSpent: Boolean = false, ageKnown: Int = 0): Coin {
        return Coin(
            derivationIndex = coinIndexCounter++,
            valueExponent = ValueExponent(exponent),
            age = if (isSpent) Coin.Age.Unknown else Coin.Age.Known(ageKnown),
            isOnChain = true,
            accountId = emptySubstrateAccountId()
        )
    }

    private var voucherIndexCounter = 0

    private fun createVoucher(
        exponent: Int,
        isReady: Boolean,
        index: RecyclerIndex = RecyclerIndex(BigInteger.ONE)
    ): RecyclerVoucher {
        return RecyclerVoucher(
            ringVrfKeyIndex = voucherIndexCounter++,
            ringVrfPublicKey = mock(),
            recyclerValue = ValueExponent(exponent),
            location = Location.InRecycler(index, recyclerMembers = FULL_RING),
        )
    }

    private companion object {
        const val FULL_RING = 767
    }
}
