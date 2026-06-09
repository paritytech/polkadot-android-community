package io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinageBalanceConversionContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.totalBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentPlan
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentPlan.DelayReason
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentPlanner
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.VoucherOffboarding
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.filterSpendable
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isReadyToUse
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import javax.inject.Inject

class RealExternalPaymentPlanner @Inject constructor(
    private val voucherRepository: VoucherRepository,
    private val coinRepository: CoinRepository,
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

    context(CoinageBalanceConversionContext)
    private suspend fun determinePlan(amount: Balance): ExternalPaymentPlan {
        val activeVouchers = voucherRepository.getActiveVouchers()
        val availableVouchers = activeVouchers.filter { it.isReadyToUse() }

        if (availableVouchers.totalBalance() >= amount) {
            val offboarding = pickVoucherForOffboardingOrThrow(availableVouchers, target = amount)
            return ExternalPaymentPlan.Ready(offboarding)
        }

        // If total number of active vouchers is sufficient but not all of them are available - prefer delay instead of trying to load with coins
        if (activeVouchers.totalBalance() >= amount) {
            return ExternalPaymentPlan.NeedsDelayedRetry(DelayReason.VOUCHERS_NOT_READY)
        }

        val deficit = amount - activeVouchers.totalBalance()
        val activeCoins = coinRepository.getActiveCoins()
        val availableCoins = activeCoins.filterSpendable(coinRepository.getCoinRecyclingAge())

        if (availableCoins.totalBalance() >= deficit) {
            val coinsToLoad = pickCoinsForDeficit(availableCoins, deficit)
            return ExternalPaymentPlan.LoadCoins(coinsToLoad)
        }

        if (activeCoins.totalBalance() >= deficit) {
            return ExternalPaymentPlan.NeedsDelayedRetry(DelayReason.COINS_NOT_READY)
        }

        return ExternalPaymentPlan.NotEnoughAmount(
            activeVouchers = activeVouchers.totalBalance(),
            activeCoins = activeCoins.totalBalance(),
            deficitToCoverWithCoins = deficit,
        )
    }

    context(CoinageBalanceConversionContext)
    private fun pickVoucherForOffboardingOrThrow(
        vouchers: List<RecyclerVoucher>,
        target: Balance,
    ): VoucherOffboarding {
        require(vouchers.totalBalance() >= target) {
            "Insufficient vouchers balance ${vouchers.totalBalance()} to cover target $target"
        }

        val sorted = vouchers.sortedByDescending { formatExponentToBalance(it.recyclerValue) }

        val selected = mutableListOf<RecyclerVoucher>()
        var accumulated = Balance.ZERO

        for (voucher in sorted) {
            if (accumulated >= target) break
            selected.add(voucher)
            accumulated += formatExponentToBalance(voucher.recyclerValue)
        }

        val surplus = (accumulated - target)

        return VoucherOffboarding(selected, surplus)
    }

    context(CoinageBalanceConversionContext)
    private fun pickCoinsForDeficit(
        coins: List<Coin>,
        deficitPlanks: Balance,
    ): List<Coin> {
        val sorted = coins.sortedByDescending { formatExponentToBalance(it.valueExponent) }

        val selected = mutableListOf<Coin>()
        var accumulated = Balance.ZERO

        for (coin in sorted) {
            if (accumulated >= deficitPlanks) break
            selected.add(coin)
            accumulated += formatExponentToBalance(coin.valueExponent)
        }

        return selected
    }
}
