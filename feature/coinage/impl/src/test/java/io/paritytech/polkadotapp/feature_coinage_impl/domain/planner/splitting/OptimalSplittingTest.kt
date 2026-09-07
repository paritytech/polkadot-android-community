package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.splitting

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.PriorityQueue
import kotlin.random.Random

/**
 * The plan is the one that attains the lower bound of the optimal splitting algorithm: it creates exactly one
 * coin per forced index, and every split is a comb over one maximal run of forced indices. The tests pin the
 * examples from the algorithm note, the structural guarantees of the theorems, and — on random inputs — the
 * bound itself against an independent reading of its definition and, on small inputs, against exhaustive search.
 */
class OptimalSplittingTest {
    @Test
    fun `plans no split when the amount is reachable from the coins as they are`() {
        val plan = plan(amount(5), coins("a" to 2, "b" to 0))

        assertNoSplits(plan)
        assertExactCoins(listOf("a", "b"), plan)
    }

    @Test
    fun `plans no split for a zero amount`() {
        val plan = plan(amount(0), coins("a" to 2))

        assertNoSplits(plan)
        assertExactCoins(emptyList(), plan)
    }

    @Test
    fun `returns null when the amount exceeds the total value`() {
        assertNull(planOrNull(amount(5), coins("a" to 2)))
    }

    @Test
    fun `creates one coin where dividing the big coin would create K (doc example 1)`() {
        val plan = plan(amount(pow(K - 1) + BigInteger.ONE), coins("small" to 0, "big" to K))

        assertSplits(listOf(split("big", recipient = listOf(K - 1), change = listOf(K - 1))), plan)
        assertExactCoins(listOf("small"), plan)
        assertEquals(1, plan.newCoins())
    }

    @Test
    fun `splits the small coin rather than the one that brings the total to the amount (doc example 2)`() {
        val plan = plan(amount(pow(K) + BigInteger.ONE), coins("two" to 1, "half" to K - 1, "big" to K))

        assertSplits(listOf(split("two", recipient = listOf(0), change = listOf(0))), plan)
        assertExactCoins(listOf("big"), plan)
        assertEquals(1, plan.newCoins())
    }

    @Test
    fun `one run of forced indices becomes one comb split`() {
        val plan = plan(amount(1), coins("big" to 5))

        assertSplits(listOf(split("big", recipient = listOf(0), change = listOf(4, 3, 2, 1, 0))), plan)
        assertExactCoins(emptyList(), plan)
        assertEquals(5, plan.newCoins())
    }

    @Test
    fun `separate runs of forced indices become independent comb splits`() {
        // 19 = 16 + 2 + 1 against coins 4, 128, 256: forced indices are {1, 2} and {5, 6, 7}.
        val plan = plan(amount(19), coins("four" to 2, "big" to 7, "bigger" to 8))

        assertSplits(
            listOf(
                split("four", recipient = listOf(1, 0), change = listOf(0)),
                split("big", recipient = listOf(4), change = listOf(6, 5, 4))
            ),
            plan
        )
        assertExactCoins(emptyList(), plan)
        assertEquals(5, plan.newCoins())
    }

    @Test
    fun `alternating forced indices give the maximum of ceil(K over 2) splits`() {
        // One coin of every odd denomination and an amount with every even bit set below K.
        val oddCoins = (1..K step 2).map { "coin$it" to it }
        val evenBits = (0 until K step 2).sumOf { pow(it) }

        val plan = plan(amount(evenBits), coins(*oddCoins.toTypedArray()))

        val expected = (1..K step 2).map { split("coin$it", recipient = listOf(it - 1), change = listOf(it - 1)) }
        assertSplits(expected, plan)
        assertEquals((K + 1) / 2, plan.splits.size)
        assertExactCoins(emptyList(), plan)
    }

    @Test
    fun `hands off the earliest coin of a denomination and splits the latest`() {
        val plan = plan(amount(6), coins("a" to 2, "b" to 2, "c" to 2))

        assertSplits(listOf(split("c", recipient = listOf(1), change = listOf(1))), plan)
        assertExactCoins(listOf("a"), plan)
    }

    @Test
    fun `never splits a coin marked unsplittable`() {
        val plan = plan(amount(5), coins("own" to 0, "bit" to 3), splittable = { it.name == "bit" })

        assertSplits(listOf(split("bit", recipient = listOf(2), change = listOf(2))), plan)
        assertExactCoins(listOf("own"), plan)
    }

    @Test
    fun `returns null when only an unsplittable coin could be split`() {
        // 9 = 8 + 1 needs a 1: the comb would start from the 4c coin, and that one may not be split.
        assertNull(planOrNull(amount(9), coins("own" to 2, "bit" to 3), splittable = { it.name == "bit" }))
    }

    @Test
    fun `handles denominations below the unit`() {
        // 0.75 = 2^-1 + 2^-2 from a single 2c coin: every index from -1 to 1 is forced.
        val plan = plan(denominations(-1, -2), coins("two" to 1))

        assertSplits(listOf(split("two", recipient = listOf(-1, -2), change = listOf(0, -2))), plan)
        assertExactCoins(emptyList(), plan)
    }

    @Test
    fun `creates exactly one coin per forced index and hands the amount over in full`() {
        val random = Random(SEED)

        repeat(RANDOM_INSTANCES) {
            val instance = randomInstance(random, maxExponent = 12, maxCount = 3)

            val plan = plan(amount(instance.amount), instance.coins)

            assertEquals(instance.describe(), forcedIndices(instance).size, plan.newCoins())
            assertSplitsAreCombs(instance, plan)
            assertRecipientReceives(instance, plan)
        }
    }

    @Test
    fun `matches the exhaustive optimum on small instances`() {
        val random = Random(SEED)

        repeat(EXHAUSTIVE_INSTANCES) {
            val instance = randomInstance(random, maxExponent = 5, maxCount = 2)

            val plan = plan(amount(instance.amount), instance.coins)

            assertEquals(instance.describe(), exhaustiveMinimumNewCoins(instance), plan.newCoins())
        }
    }

    // --- Properties checked on random instances

    private fun assertSplitsAreCombs(instance: Instance, plan: OptimalSplitPlan<Item>) {
        val splitCoins = plan.splits.map { it.coin }
        assertEquals(instance.describe(), splitCoins.size, splitCoins.toSet().size)

        plan.splits.forEach { split ->
            val outputs = (split.recipientDenominations + split.changeDenominations).map { it.value }.sortedDescending()
            val top = split.coin.exponent
            val bottom = outputs.last()

            assertTrue(instance.describe(), split.coin in instance.coins)
            assertEquals(instance.describe(), pow(top), outputs.sumOf { pow(it) })
            assertEquals(instance.describe(), (bottom until top).toList().reversed() + bottom, outputs)
        }
    }

    private fun assertRecipientReceives(instance: Instance, plan: OptimalSplitPlan<Item>) {
        val handedOff = plan.exactCoins.sumOf { pow(it.exponent) } +
            plan.splits.flatMap { it.recipientDenominations }.sumOf { pow(it.value) }
        assertEquals(instance.describe(), instance.amount, handedOff)

        assertEquals(instance.describe(), plan.exactCoins.size, plan.exactCoins.toSet().size)
        assertTrue(instance.describe(), plan.exactCoins.none { it in plan.splits.map { split -> split.coin } })
    }

    /** Straight from the definition: index j is forced when the amount modulo 2^j exceeds the value below j. */
    private fun forcedIndices(instance: Instance): Set<Int> {
        val maxExponent = instance.coins.maxOf { it.exponent }

        return (1..maxExponent).filter { j ->
            val modulus = pow(j)
            val amountBelow = instance.amount.mod(modulus)
            val valueBelow = instance.coins.filter { it.exponent < j }.sumOf { pow(it.exponent) }
            amountBelow > valueBelow
        }.toSet()
    }

    /** Dijkstra over count vectors: every split of any coin into any multiset of powers of two is an edge. */
    private fun exhaustiveMinimumNewCoins(instance: Instance): Int {
        val start = instance.coins.map { it.exponent }.sorted()
        val best = mutableMapOf(start to 0)
        val queue = PriorityQueue<Pair<Int, List<Int>>>(compareBy { it.first })
        queue += 0 to start

        while (queue.isNotEmpty()) {
            val (cost, state) = queue.poll()
            if (cost > best.getValue(state)) continue
            if (isReachable(instance.amount, state)) return cost

            state.distinct().forEach { exponent ->
                val rest = state.toMutableList().also { it.remove(exponent) }
                splitsOf(exponent).forEach { outputs ->
                    val next = (rest + outputs).sorted()
                    val nextCost = cost + outputs.size - 1
                    if (nextCost < (best[next] ?: Int.MAX_VALUE)) {
                        best[next] = nextCost
                        queue += nextCost to next
                    }
                }
            }
        }

        error("Amount ${instance.amount} is not reachable in ${instance.describe()}")
    }

    /** Greedy descending selection decides reachability for powers of two. */
    private fun isReachable(amount: BigInteger, exponents: List<Int>): Boolean {
        var remaining = amount
        exponents.sortedDescending().forEach { exponent ->
            if (pow(exponent) <= remaining) remaining -= pow(exponent)
        }
        return remaining.signum() == 0
    }

    /** All multisets of exponents strictly below [exponent] that sum to 2^exponent. */
    private fun splitsOf(exponent: Int): List<List<Int>> {
        fun partitions(value: BigInteger, maxExponent: Int): List<List<Int>> {
            if (value.signum() == 0) return listOf(emptyList())
            if (maxExponent < 0) return emptyList()
            val withMax = if (pow(maxExponent) <= value) {
                partitions(value - pow(maxExponent), maxExponent).map { listOf(maxExponent) + it }
            } else {
                emptyList()
            }
            return withMax + partitions(value, maxExponent - 1)
        }
        return partitions(pow(exponent), exponent - 1)
    }

    // --- Fixtures

    private data class Item(val name: String, val exponent: Int)

    private class Instance(val amount: BigInteger, val coins: List<Item>) {
        fun describe() = "amount=$amount coins=${coins.map { it.exponent }.sorted()}"
    }

    private fun randomInstance(random: Random, maxExponent: Int, maxCount: Int): Instance {
        val exponentBound = random.nextInt(1, maxExponent + 1)
        val coins = (0..exponentBound).flatMap { exponent ->
            List(random.nextInt(maxCount + 1)) { index -> Item("c$exponent-$index", exponent) }
        }.ifEmpty { listOf(Item("c0-0", 0)) }
        val total = coins.sumOf { pow(it.exponent) }
        val amount = BigInteger.valueOf(random.nextLong(total.toLong() + 1))
        return Instance(amount, coins)
    }

    private fun coins(vararg items: Pair<String, Int>) = items.map { (name, exponent) -> Item(name, exponent) }

    private fun amount(value: Long) = amount(BigInteger.valueOf(value))

    private fun amount(value: BigInteger): List<ValueExponent> =
        (0 until value.bitLength()).filter { value.testBit(it) }.map(::ValueExponent)

    private fun denominations(vararg exponents: Int) = exponents.map(::ValueExponent)

    private fun pow(exponent: Int): BigInteger = BigInteger.ONE.shiftLeft(exponent)

    private fun planOrNull(
        amount: List<ValueExponent>,
        coins: List<Item>,
        splittable: (Item) -> Boolean = { true }
    ): OptimalSplitPlan<Item>? = OptimalSplitting.plan(amount, coins, { ValueExponent(it.exponent) }, splittable)

    private fun plan(
        amount: List<ValueExponent>,
        coins: List<Item>,
        splittable: (Item) -> Boolean = { true }
    ): OptimalSplitPlan<Item> {
        val plan = planOrNull(amount, coins, splittable)
        assertNotNull("expected a plan", plan)
        return plan!!
    }

    private fun split(name: String, recipient: List<Int>, change: List<Int>) =
        Triple(name, recipient, change)

    private fun assertSplits(expected: List<Triple<String, List<Int>, List<Int>>>, plan: OptimalSplitPlan<Item>) {
        val actual = plan.splits.map { split ->
            Triple(split.coin.name, split.recipientDenominations.map { it.value }, split.changeDenominations.map { it.value })
        }
        assertEquals(expected, actual)
    }

    private fun assertNoSplits(plan: OptimalSplitPlan<Item>) = assertEquals(emptyList<PlannedSplit<Item>>(), plan.splits)

    private fun assertExactCoins(expected: List<String>, plan: OptimalSplitPlan<Item>) =
        assertEquals(expected, plan.exactCoins.map { it.name })

    private fun OptimalSplitPlan<Item>.newCoins(): Int =
        splits.sumOf { it.recipientDenominations.size + it.changeDenominations.size - 1 }

    private companion object {
        const val K = 14
        const val SEED = 20260902
        const val RANDOM_INSTANCES = 2000
        const val EXHAUSTIVE_INSTANCES = 300
    }
}
