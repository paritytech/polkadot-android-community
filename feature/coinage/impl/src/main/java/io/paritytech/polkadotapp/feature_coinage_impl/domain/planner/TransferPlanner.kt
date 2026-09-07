package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner

import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinAmountBreakdown
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinageBalanceConversionContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinSplit
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.StrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferPlan
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ageOrDefault
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isAgeValidToSpend
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isInRecycler
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.recyclerLocationOrThrow
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.exceptions.InsufficientBalanceException
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.splitting.OptimalSplitting
import java.math.BigDecimal

class TransferPlanner(
    private val conversionContext: CoinageBalanceConversionContext,
    private val breakdownAmount: CoinAmountBreakdown,
    private val coinMaxRecyclingAge: Int
) {
    fun plan(
        amount: BigDecimal,
        coins: List<Coin>,
        vouchers: List<RecyclerVoucher>
    ): Result<TransferPlan> {
        return runCatching {
            val amountDenominations = breakdownAmount.breakdown(amount)
            val spendableCoins = coins.orderForSpending()

            val strategyType = tryGetExactMatchPlan(amount, spendableCoins)
                ?: tryGetSplitPlan(amountDenominations, spendableCoins)
                ?: tryGetCoinsAndUnloadPlan(amount, amountDenominations, spendableCoins, vouchers)
                ?: throw InsufficientBalanceException()

            TransferPlan(strategyType)
        }
    }

    private fun tryGetExactMatchPlan(
        amount: BigDecimal,
        coins: List<Coin>
    ): StrategyType? {
        val selectedCoins = findSubsetSum(coins, amount) ?: return null

        return StrategyType.ExactCoins(coins = selectedCoins)
    }

    /** Fewest new coins over any sequence of splits; null when the coins fall short of the amount. */
    private fun tryGetSplitPlan(
        amountDenominations: List<ValueExponent>,
        coins: List<Coin>
    ): StrategyType? {
        val plan = OptimalSplitting.plan(amountDenominations, coins, Coin::valueExponent) { true } ?: return null

        return StrategyType.Split(
            splits = plan.splits.map { CoinSplit(it.coin, it.recipientDenominations, it.changeDenominations) },
            exactCoins = plan.exactCoins
        )
    }

    private fun tryGetCoinsAndUnloadPlan(
        amount: BigDecimal,
        amountDenominations: List<ValueExponent>,
        coins: List<Coin>,
        vouchers: List<RecyclerVoucher>
    ): StrategyType? {
        val (coveringCoins, coveredCoinAmount) = findMaxCoinCoverage(coins, amount)

        val remainingAmount = amount - coveredCoinAmount

        if (remainingAmount <= BigDecimal.ZERO) throw IllegalStateException("Not needed to unload vouchers: transfer amount may be covered by exact coins.")

        val readyVouchers = vouchers.filter { it.isInRecycler() }

        val (selectedVouchers, _) = findMinimalVoucherCover(readyVouchers, remainingAmount) ?: return null

        val exactCoins = findCoinsSparingUnloadSplits(amountDenominations, coins, selectedVouchers) ?: coveringCoins

        return StrategyType.UnloadAndSplit(
            vouchersToUnload = selectedVouchers,
            recipientAmount = amount - exactCoins.sumOf { it.valueExponent.toAmount() },
            exactCoins = exactCoins
        )
    }

    /**
     * Own coins to hand off so that unloading [vouchers] mints the fewest coins. Each unloaded group enters as
     * the coins of its value's breakdown, which the unload may split freely, while own coins are not split
     * here. Null when the best plan would split an own coin; the caller then falls back to maximal coverage.
     */
    private fun findCoinsSparingUnloadSplits(
        amountDenominations: List<ValueExponent>,
        coins: List<Coin>,
        vouchers: List<RecyclerVoucher>
    ): List<Coin>? {
        val unloadedDenominations = vouchers
            .groupBy { VoucherUnloadGroupKey(it.recyclerValue, it.recyclerLocationOrThrow().recyclerIndex) }
            .values
            .flatMap { group -> breakdownAmount.breakdown(group.sumOf { it.recyclerValue.toAmount() }) }

        val sources = coins.map { UnloadSource.Own(it) } + unloadedDenominations.map { UnloadSource.Unloaded(it) }

        return OptimalSplitting.plan(amountDenominations, sources, UnloadSource::exponent) { it is UnloadSource.Unloaded }
            ?.exactCoins
            ?.filterIsInstance<UnloadSource.Own>()
            ?.map { it.coin }
    }

    private sealed interface UnloadSource {
        val exponent: ValueExponent

        class Own(val coin: Coin) : UnloadSource {
            override val exponent get() = coin.valueExponent
        }

        class Unloaded(override val exponent: ValueExponent) : UnloadSource
    }

    private fun findSubsetSum(coins: List<Coin>, target: BigDecimal): List<Coin>? {
        if (target.compareTo(BigDecimal.ZERO) == 0) return emptyList()
        if (target < BigDecimal.ZERO) return null

        var remaining = target
        val result = mutableListOf<Coin>()

        for (coin in coins) {
            if (remaining <= BigDecimal.ZERO) break
            val value = coin.valueExponent.toAmount()
            if (value <= remaining) {
                result.add(coin)
                remaining -= value
            }
        }

        return if (remaining.compareTo(BigDecimal.ZERO) == 0) result else null
    }

    private fun findMaxCoinCoverage(coins: List<Coin>, targetAmount: BigDecimal): Pair<List<Coin>, BigDecimal> {
        val selected = mutableListOf<Coin>()
        var covered = BigDecimal.ZERO

        for (coin in coins) {
            val newAmount = covered + coin.valueExponent.toAmount()
            if (newAmount <= targetAmount) {
                selected.add(coin)
                covered = newAmount
            } else {
                break
            }
        }

        return selected to covered
    }

    /**
     * Spendable coins, largest first and within a denomination oldest first: the oldest are handed off whole
     * while they still can be, and the youngest is the one to split.
     */
    private fun List<Coin>.orderForSpending(): List<Coin> {
        return filter { it.isAgeValidToSpend(coinMaxRecyclingAge) }
            .sortedWith(compareByDescending<Coin> { it.valueExponent }.thenByDescending { it.ageOrDefault() })
    }

    private fun findMinimalVoucherCover(
        vouchers: List<RecyclerVoucher>,
        targetAmount: BigDecimal
    ): Pair<List<RecyclerVoucher>, BigDecimal>? {
        val orderedVouchers = vouchers.orderForUnload()

        val selectedVouchers = mutableListOf<RecyclerVoucher>()
        var totalAmount = BigDecimal.ZERO
        var remaining = targetAmount

        for (voucher in orderedVouchers) {
            if (remaining <= BigDecimal.ZERO) break
            val amount = voucher.recyclerValue.toAmount()
            selectedVouchers += voucher
            totalAmount += amount
            remaining -= amount
        }

        return if (remaining <= BigDecimal.ZERO) selectedVouchers to totalAmount else null
    }

    private fun List<RecyclerVoucher>.orderForUnload(): List<RecyclerVoucher> {
        return groupBy {
            VoucherUnloadGroupKey(
                valueExponent = it.recyclerValue,
                recyclerIndex = it.recyclerLocationOrThrow().recyclerIndex
            )
        }.values
            .map { group -> group to group.sumOf { it.recyclerValue.toAmount() } }
            .sortedByDescending { (_, total) -> total }
            .flatMap { (group, _) -> group }
    }

    private data class VoucherUnloadGroupKey(
        val valueExponent: ValueExponent,
        val recyclerIndex: RecyclerIndex
    )

    private fun ValueExponent.toAmount() = conversionContext.formatExponentToAmount(this)
}
