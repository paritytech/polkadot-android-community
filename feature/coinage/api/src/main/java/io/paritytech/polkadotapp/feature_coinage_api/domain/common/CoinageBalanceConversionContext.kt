package io.paritytech.polkadotapp.feature_coinage_api.domain.common

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.network.binding.sumByBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import java.math.BigDecimal

interface CoinageBalanceConversionContext {
    fun formatExponentToBalance(exponent: ValueExponent): Balance

    fun formatExponentToAmount(exponent: ValueExponent): BigDecimal
}

fun CoinageBalanceConversionContext.formatCoinsToBalance(coins: List<Coin>) = formatExponentsToBalance(coins.map { it.valueExponent })
fun CoinageBalanceConversionContext.formatVouchersToBalance(vouchers: List<RecyclerVoucher>) = formatExponentsToBalance(vouchers.map { it.recyclerValue })

fun CoinageBalanceConversionContext.formatExponentsToBalance(exponents: List<ValueExponent>): Balance {
    return exponents.map { formatExponentToBalance(it) }
        .sumByBalance { it }
}

context(CoinageBalanceConversionContext)
fun RecyclerVoucher.balance(): Balance {
    return formatExponentToBalance(recyclerValue)
}

@JvmName("totalVoucherBalance")
context(CoinageBalanceConversionContext)
fun List<RecyclerVoucher>.totalBalance(): Balance {
    return sumByBalance { it.balance() }
}

@JvmName("totalCoinBalance")
context(CoinageBalanceConversionContext)
fun Coin.balance(): Balance {
    return formatExponentToBalance(valueExponent)
}

context(CoinageBalanceConversionContext)
fun List<Coin>.totalBalance(): Balance {
    return sumByBalance { it.balance() }
}

context(CoinageBalanceConversionContext)
fun ValueExponent.balance(): Balance {
    return formatExponentToBalance(this)
}
