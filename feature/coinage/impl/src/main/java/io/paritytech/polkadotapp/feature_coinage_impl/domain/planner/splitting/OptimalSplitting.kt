package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.splitting

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import java.math.BigInteger

data class PlannedSplit<T>(
    val coin: T,
    val recipientDenominations: List<ValueExponent>,
    val changeDenominations: List<ValueExponent>
)

/** [exactCoins] are handed over as they are; the recipient denominations of [splits] are minted for them. */
data class OptimalSplitPlan<T>(
    val exactCoins: List<T>,
    val splits: List<PlannedSplit<T>>
)

/**
 * Splits coins so that an amount becomes payable while creating the fewest new coins.
 *
 * Index j is forced when the amount modulo 2^j exceeds the total value held below denomination j. Every plan
 * creates at least one coin per forced index, and this one creates exactly that: each maximal run (t, k] of
 * forced indices is served by splitting one coin of denomination k into a comb — one coin of every denomination
 * from k-1 down to t, plus a second coin of t — after which the amount is payable without further splits.
 */
object OptimalSplitting {
    /**
     * [amount] is given as the denominations of its breakdown. [coins] are in preference order: within one
     * denomination the first coins are handed off and the last splittable one is split.
     *
     * Returns null when the amount exceeds the total value, or when some run of forced indices could only be
     * served by a coin that [splittable] rejects.
     */
    fun <T> plan(
        amount: List<ValueExponent>,
        coins: List<T>,
        exponentOf: (T) -> ValueExponent,
        splittable: (T) -> Boolean
    ): OptimalSplitPlan<T>? {
        if (coins.isEmpty()) return if (amount.isEmpty()) OptimalSplitPlan(emptyList(), emptyList()) else null

        val exponents = coins.map { exponentOf(it).value }
        val scale = Scale(base = (exponents + amount.map { it.value }).min())
        val amountValue = amount.sumOf { scale.valueOf(it.value) }
        if (amountValue > exponents.sumOf(scale::valueOf)) return null

        val combs = forcedRuns(amount, exponents, scale).map { run ->
            val coinIndex = exponents.indices.lastOrNull { exponents[it] == run.top && splittable(coins[it]) }
                ?: return null
            Comb(coinIndex, outputs = ((run.top - 1) downTo run.bottom).toList() + run.bottom)
        }

        return assign(amountValue, coins, exponents, combs, scale)
    }

    private fun forcedRuns(amount: List<ValueExponent>, exponents: List<Int>, scale: Scale): List<ForcedRun> {
        val amountDigits = amount.map { it.value }
        val runs = mutableListOf<ForcedRun>()
        var valueBelow = BigInteger.ZERO
        var amountBelow = BigInteger.ZERO

        for (index in (scale.base + 1)..exponents.max()) {
            valueBelow += exponents.count { it == index - 1 }.toBigInteger() * scale.valueOf(index - 1)
            amountBelow += amountDigits.count { it == index - 1 }.toBigInteger() * scale.valueOf(index - 1)

            if (amountBelow > valueBelow) {
                val last = runs.lastOrNull()
                if (last != null && last.top == index - 1) {
                    runs[runs.lastIndex] = last.copy(top = index)
                } else {
                    runs += ForcedRun(bottom = index - 1, top = index)
                }
            }
        }

        return runs
    }

    /** Takes coins from the top denomination down, whole coins before split outputs; the theorem guarantees success. */
    private fun <T> assign(
        amountValue: BigInteger,
        coins: List<T>,
        exponents: List<Int>,
        combs: List<Comb>,
        scale: Scale
    ): OptimalSplitPlan<T> {
        val splitIndices = combs.map { it.coinIndex }.toSet()
        val exactCoins = mutableListOf<T>()
        val recipientOutputs = combs.map { mutableListOf<Int>() }
        var remaining = amountValue

        for (exponent in exponents.max() downTo scale.base) {
            val value = scale.valueOf(exponent)

            coins.indices
                .filter { it !in splitIndices && exponents[it] == exponent }
                .forEach { index ->
                    if (value <= remaining) {
                        exactCoins += coins[index]
                        remaining -= value
                    }
                }

            combs.forEachIndexed { combIndex, comb ->
                comb.outputs.filter { it == exponent }.forEach {
                    if (value <= remaining) {
                        recipientOutputs[combIndex] += exponent
                        remaining -= value
                    }
                }
            }
        }

        check(remaining.signum() == 0) { "Amount not payable after optimal splits" }

        val splits = combs.mapIndexed { combIndex, comb ->
            val recipient = recipientOutputs[combIndex]
            val change = comb.outputs.toMutableList().also { outputs -> recipient.forEach { outputs.remove(it) } }
            PlannedSplit(
                coin = coins[comb.coinIndex],
                recipientDenominations = recipient.map(::ValueExponent),
                changeDenominations = change.map(::ValueExponent)
            )
        }

        return OptimalSplitPlan(exactCoins, splits)
    }

    /** Forced indices bottom+1..top, i.e. the half-open interval (bottom, top]. */
    private data class ForcedRun(val bottom: Int, val top: Int)

    /** Outputs in descending order. */
    private class Comb(val coinIndex: Int, val outputs: List<Int>)

    /** Values in units of 2^base, so that denominations below the unit stay integral. */
    private class Scale(val base: Int) {
        fun valueOf(exponent: Int): BigInteger = BigInteger.ONE.shiftLeft(exponent - base)
    }
}
