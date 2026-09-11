package io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinageBalanceConversionContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.totalBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentPlan
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentPlanner
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.VoucherOffboarding
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.CoinageAssetSelector
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.SpendScope
import javax.inject.Inject

class RealExternalPaymentPlanner @Inject constructor(
    private val assetSelector: CoinageAssetSelector,
    private val coinageBalanceConverterUseCase: CoinageBalanceConverterUseCase,
) : ExternalPaymentPlanner {
    override suspend fun plan(amount: Balance): Result<ExternalPaymentPlan> = runCancellableCatching {
        val converter = coinageBalanceConverterUseCase.create().getOrThrow()
        with(converter) { determinePlan(amount) }
    }

    override suspend fun pickOffboarding(
        availableVouchers: List<RecyclerVoucher>,
        target: Balance,
    ): Result<VoucherOffboarding> = runCancellableCatching {
        val converter = coinageBalanceConverterUseCase.create().getOrThrow()
        with(converter) { pickVoucherForOffboardingOrThrow(availableVouchers, target) }
    }

    context(coinageContext: CoinageBalanceConversionContext)
    private suspend fun determinePlan(amount: Balance): ExternalPaymentPlan {
        val privateVouchers = assetSelector.getSelectableVouchers(SpendScope.SPENDABLE)
        val privateCoins = assetSelector.getSelectableCoins(SpendScope.SPENDABLE)

        planWithin(amount, privateVouchers, privateCoins)?.let { return it }

        val onChainVouchers = assetSelector.getOnChainSpendableVouchers()
        val onChainCoins = assetSelector.getOnChainSpendableCoins()

        planWithin(amount, onChainVouchers, onChainCoins)?.let { return it }

        return ExternalPaymentPlan.NotEnoughAmount(
            activeVouchers = onChainVouchers.totalBalance(),
            activeCoins = onChainCoins.totalBalance(),
            deficitToCoverWithCoins = amount - onChainVouchers.totalBalance(),
        )
    }

    context(coinageContext: CoinageBalanceConversionContext)
    private fun planWithin(
        amount: Balance,
        vouchers: List<RecyclerVoucher>,
        coins: List<Coin>,
    ): ExternalPaymentPlan? {
        if (vouchers.totalBalance() >= amount) {
            return ExternalPaymentPlan.Ready(pickVoucherForOffboardingOrThrow(vouchers, target = amount))
        }

        val deficit = amount - vouchers.totalBalance()
        if (coins.totalBalance() < deficit) return null

        return ExternalPaymentPlan.LoadCoins(
            coinsToLoad = pickCoinsForDeficit(coins, deficit),
            exactVouchers = vouchers,
        )
    }

    context(coinageContext: CoinageBalanceConversionContext)
    private fun pickVoucherForOffboardingOrThrow(
        vouchers: List<RecyclerVoucher>,
        target: Balance,
    ): VoucherOffboarding {
        require(vouchers.totalBalance() >= target) {
            "Insufficient vouchers balance ${vouchers.totalBalance()} to cover target $target"
        }

        val sorted = vouchers.sortedByDescending { coinageContext.formatExponentToBalance(it.recyclerValue) }

        val selected = mutableListOf<RecyclerVoucher>()
        var accumulated = Balance.ZERO

        for (voucher in sorted) {
            if (accumulated >= target) break
            selected.add(voucher)
            accumulated += coinageContext.formatExponentToBalance(voucher.recyclerValue)
        }

        val surplus = (accumulated - target)

        return VoucherOffboarding(selected, surplus)
    }

    context(coinageContext: CoinageBalanceConversionContext)
    private fun pickCoinsForDeficit(
        coins: List<Coin>,
        deficitPlanks: Balance,
    ): List<Coin> {
        val sorted = coins.sortedByDescending { coinageContext.formatExponentToBalance(it.valueExponent) }

        val selected = mutableListOf<Coin>()
        var accumulated = Balance.ZERO

        for (coin in sorted) {
            if (accumulated >= deficitPlanks) break
            selected.add(coin)
            accumulated += coinageContext.formatExponentToBalance(coin.valueExponent)
        }

        return selected
    }
}
