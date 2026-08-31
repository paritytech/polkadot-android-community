package io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling

import io.paritytech.polkadotapp.common.utils.Fraction
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinageBalanceConversionContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.balance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinRecyclingState
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclingVerdicts
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ageOrDefault
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isInRecycler
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.recyclerMembersOrZero
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.CoinRecyclingStrategy
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingParams
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingSnapshot
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.VoucherUsabilityContext
import java.math.RoundingMode

/**
 * The only recycling policy there is. The named strategies differ by [params] alone, so an intermediate
 * position between them is a new set of numbers rather than a new class.
 */
class ParametricRecyclingStrategy(private val params: RecyclingParams) : CoinRecyclingStrategy {
    context(conversion: CoinageBalanceConversionContext)
    override suspend fun evaluate(coins: List<Coin>, snapshot: RecyclingSnapshot): RecyclingVerdicts {
        val budget = snapshot.total * params.maxUnavailableBalance.fraction
        var unavailable = snapshot.unavailable

        // Oldest first: a coin nearer the age the chain stops accepting has the most to lose by waiting,
        // so it gets first claim on the budget.
        return coins.sortedByDescending(Coin::ageOrDefault).associate { coin ->
            // Headroom, not fit. While any budget is left the next coin is admitted even if it overshoots,
            // so a coin worth more than the whole budget still recycles instead of sitting untouched until
            // the age limit forces it. The coin after an overshoot then finds no headroom and waits.
            val gated = coin.ageOrDefault() >= params.minRecyclingAge && unavailable < budget

            if (gated) unavailable += coin.balance()

            coin.derivationIndex to if (gated) CoinRecyclingState.TO_RECYCLE else CoinRecyclingState.ALLOW_USE
        }
    }

    override fun isVoucherUsable(voucher: RecyclerVoucher, context: VoucherUsabilityContext): Boolean {
        if (!voucher.isInRecycler()) return false

        val requiredMembers = context.capacityFor(voucher.recyclerValue)
            .toBigDecimal()
            .multiply(params.requiredRingFill.fraction)
            .setScale(0, RoundingMode.CEILING)
            .toInt()

        // Short of a full ring the random unload delay stands in for anonymity-set size — it buys the same
        // unlinkability by time instead of by crowd. At a full ring nothing but the ring will do.
        val delayElapsed = params.requiredRingFill < Fraction.FULL && voucher.delayUnloadUntil < context.now

        return voucher.recyclerMembersOrZero() >= requiredMembers || delayElapsed
    }

    override fun allowsConfirmedSpend(): Boolean = params.allowsConfirmedSpend
}
