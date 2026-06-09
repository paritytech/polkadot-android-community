package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.common.utils.currentTimestampFlow
import io.paritytech.polkadotapp.common.utils.transformPair
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.formatCoinsToBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.formatVouchersToBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.canBeSpent
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isReadyToUse
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isReadyToUseSecured
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TotalBalanceUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.annotations.VisibleForTesting
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private val TOTAL_BALANCE_UPDATE_INTERVAL = 6.seconds

class RealTotalBalanceUseCase @Inject constructor(
    private val coinRepository: CoinRepository,
    private val voucherRepository: VoucherRepository,
    private val coinageBalanceConverterUseCase: CoinageBalanceConverterUseCase
) : TotalBalanceUseCase {
    override fun subscribeTotalBalance(): Flow<Result<CoinageBalance>> {
        val recyclingAge = coinRepository.getCoinRecyclingAge()

        return combine(
            currentTimestampFlow(interval = TOTAL_BALANCE_UPDATE_INTERVAL),
            coinRepository.subscribeAllNotSpentCoins(),
            voucherRepository.subscribeAllNotUsedVouchers()
        ) { currentTimeMillis, coins, vouchers ->
            calculateCoinageBalance(coins, vouchers, currentTimeMillis, recyclingAge)
        }.distinctUntilChanged()
    }

    override suspend fun getBalance(): Result<CoinageBalance> {
        return calculateCoinageBalance(
            coins = coinRepository.getAllNotSpentCoins(),
            vouchers = voucherRepository.getAllNotUsedVouchers(),
            currentTimeMillis = System.currentTimeMillis(),
            recyclingAge = coinRepository.getCoinRecyclingAge(),
        )
    }

    @VisibleForTesting
    internal suspend fun calculateCoinageBalance(
        coins: List<Coin>,
        vouchers: List<RecyclerVoucher>,
        currentTimeMillis: Timestamp,
        recyclingAge: Int,
    ): Result<CoinageBalance> = coinageBalanceConverterUseCase.create()
        .map { conversionContext ->
            val (spendableCoinsBalance, pendingCoinsBalance) = coins
                .partition { it.canBeSpent(recyclableAge = recyclingAge) }
                .transformPair { conversionContext.formatCoinsToBalance(it) }

            val (readyVouchers, notReadyVouchers) = vouchers.partition { it.isReadyToUse() }

            val (securedVouchersBalance, degradedVouchersBalance) = readyVouchers
                .partition { it.isReadyToUseSecured(currentTimeMillis) }
                .transformPair { conversionContext.formatVouchersToBalance(it) }

            val spendableSecuredBalance = spendableCoinsBalance + securedVouchersBalance

            val unreadyVouchersBalance = conversionContext.formatVouchersToBalance(notReadyVouchers)
            val pendingBalance = unreadyVouchersBalance + pendingCoinsBalance

            CoinageBalance(
                spendableBalance = CoinageBalance.SpendableBalance(
                    secured = spendableSecuredBalance,
                    degraded = degradedVouchersBalance
                ),
                pendingBalance = pendingBalance,
            )
        }
}
