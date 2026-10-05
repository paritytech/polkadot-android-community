package io.paritytech.polkadotapp.feature_coinage_impl.domain.common

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.chains.util.amountFromPlanks
import io.paritytech.polkadotapp.chains.util.planksFromAmount
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinAmountBreakdown
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinageBalanceConversionContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import java.math.BigDecimal

class RealCoinAmountBreakdownContext(
    private val precision: Int,
    private val coinageBalanceConvertionContext: CoinageBalanceConversionContext,
    private val allowedExponents: Set<ValueExponent>
) : CoinAmountBreakdown {
    override fun breakdown(amount: BigDecimal): List<ValueExponent> {
        val result = mutableListOf<ValueExponent>()
        var remainingAmount = amount.planksFromAmount(precision)

        allowedExponents.sortedDescending()
            .forEach {
                val coinValue = it.valueInPlanks()
                while (remainingAmount >= coinValue) {
                    result.add(it)
                    remainingAmount -= coinValue
                }
            }

        require(remainingAmount.isZero()) { "Invalid amount breakdown: remainingAmount $remainingAmount is less than minimum exponent ${allowedExponents.minOf { it.value }}" }

        return result
    }

    override fun roundDownAmount(amount: BigDecimal): BigDecimal {
        val planksAmount = amount.planksFromAmount(precision).value
        val minAllowedMultiplier = allowedExponents.minOf { it.valueInPlanks() }.value

        val roundedPlanks = planksAmount.divide(minAllowedMultiplier)
            .multiply(minAllowedMultiplier)

        return roundedPlanks.intoBalance().amountFromPlanks(precision)
    }

    override fun remainderAfterBreakdown(amount: BigDecimal): Balance {
        return allowedExponents.sortedDescending()
            .fold(amount.planksFromAmount(precision).value) { remaining, exponent ->
                val coinValue = exponent.valueInPlanks().value
                if (remaining >= coinValue) remaining % coinValue else remaining
            }
            .intoBalance()
    }

    private fun ValueExponent.valueInPlanks(): Balance {
        return coinageBalanceConvertionContext.formatExponentToBalance(this)
    }
}
