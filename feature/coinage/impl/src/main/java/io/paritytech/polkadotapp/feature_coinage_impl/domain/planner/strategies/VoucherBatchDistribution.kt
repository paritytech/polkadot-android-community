package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies

import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinAmountBreakdown
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinageBalanceConversionContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.recyclerLocationOrThrow
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.splitting.OptimalSplitting
import java.math.BigDecimal

data class VoucherBatch(
    val recyclerKey: RecyclerKey,
    val vouchers: List<RecyclerVoucher>,
    val recipientDenominations: List<ValueExponent>,
    val changeDenominations: List<ValueExponent>
)

/**
 * Decides how vouchers are cashed in for a transfer.
 *
 * Vouchers are unloaded in batches; each batch redeems vouchers of one size (`2^exponent`), so a batch can only
 * produce coins whose total is at most `voucherCount * 2^exponent`, and every coin must come whole out of one batch.
 *
 * Each batch enters as the coins of its value's binary breakdown — the fewest coins an unload of it can produce.
 * From there the recipient value is served by [OptimalSplitting]: a coin of one batch is split only where the
 * recipient value has no other way to be paid, so the number of minted coins is the smallest possible for these
 * batches. Splits stay inside the coin they start from, which keeps every output within its batch.
 */
object VoucherBatchDistribution {
    fun distribute(
        vouchers: List<RecyclerVoucher>,
        recipientAmount: BigDecimal,
        maxConsolidation: Int,
        breakdown: CoinAmountBreakdown,
        conversionContext: CoinageBalanceConversionContext
    ): List<VoucherBatch> {
        val batches = vouchers
            .groupBy { RecyclerKey(it.recyclerValue, it.recyclerLocationOrThrow().recyclerIndex) }
            .flatMap { (key, group) -> group.chunked(maxConsolidation).map { chunk -> key to chunk } }

        val batchCoins = batches.flatMapIndexed { batchIndex, (key, batchVouchers) ->
            val batchValue = conversionContext.formatExponentToAmount(key.exponent) * batchVouchers.size.toBigDecimal()
            breakdown.breakdown(batchValue).map { BatchCoin(batchIndex, it) }
        }

        val plan = OptimalSplitting.plan(breakdown.breakdown(recipientAmount), batchCoins, BatchCoin::exponent) { true }
            ?: throw IllegalStateException("Recipient amount $recipientAmount exceeds the value of the vouchers to unload")

        val recipient = batches.map { mutableListOf<ValueExponent>() }
        val change = batches.map { mutableListOf<ValueExponent>() }
        val splitCoins = plan.splits.map { it.coin }

        plan.exactCoins.forEach { recipient[it.batchIndex] += it.exponent }
        (batchCoins - plan.exactCoins.toSet() - splitCoins.toSet()).forEach { change[it.batchIndex] += it.exponent }
        plan.splits.forEach { split ->
            recipient[split.coin.batchIndex] += split.recipientDenominations
            change[split.coin.batchIndex] += split.changeDenominations
        }

        return batches.mapIndexed { batchIndex, (key, batchVouchers) ->
            VoucherBatch(
                recyclerKey = key,
                vouchers = batchVouchers,
                recipientDenominations = recipient[batchIndex].sortedDescending(),
                changeDenominations = change[batchIndex].sortedDescending()
            )
        }
    }

    /** Identity matters: one batch may hold several coins of one denomination. */
    private class BatchCoin(val batchIndex: Int, val exponent: ValueExponent)
}
