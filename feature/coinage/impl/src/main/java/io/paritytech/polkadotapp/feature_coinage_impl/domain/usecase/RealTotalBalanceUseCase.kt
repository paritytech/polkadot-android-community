package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.common.utils.currentTimestampFlow
import io.paritytech.polkadotapp.common.utils.transformPair
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.formatCoinsToBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.formatVouchersToBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isReadyToUseSecured
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageAssetsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TotalBalanceUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedCoin
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.isAwaitingRecycling
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.isMinting
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.isOnboarding
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.isSelectable
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.annotations.VisibleForTesting
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private val TOTAL_BALANCE_UPDATE_INTERVAL = 6.seconds

class RealTotalBalanceUseCase @Inject constructor(
    private val coinRepository: CoinRepository,
    private val coinageAssetsUseCase: CoinageAssetsUseCase,
    private val coinageBalanceConverterUseCase: CoinageBalanceConverterUseCase
) : TotalBalanceUseCase {
    override fun subscribeTotalBalance(): Flow<Result<CoinageBalance>> {
        val recyclingAge = coinRepository.getCoinRecyclingAge()

        return combine(
            currentTimestampFlow(interval = TOTAL_BALANCE_UPDATE_INTERVAL),
            coinageAssetsUseCase.subscribeCoins(),
            coinageAssetsUseCase.subscribeVouchers()
        ) { currentTimeMillis, coins, vouchers ->
            calculateCoinageBalance(coins, vouchers, currentTimeMillis, recyclingAge)
        }.distinctUntilChanged()
    }

    override suspend fun getBalance(): Result<CoinageBalance> {
        return calculateCoinageBalance(
            coins = coinageAssetsUseCase.getCoins(),
            vouchers = coinageAssetsUseCase.getVouchers(),
            currentTimeMillis = System.currentTimeMillis(),
            recyclingAge = coinRepository.getCoinRecyclingAge(),
        )
    }

    @VisibleForTesting
    /**
     * A locked or already-consumed asset counts nowhere; of the rest, presence decides spendable from
     * pending, and absence is only pending while the transaction minting it is still live.
     */
    internal suspend fun calculateCoinageBalance(
        coins: List<TrackedCoin>,
        vouchers: List<TrackedVoucher>,
        currentTimeMillis: Timestamp,
        recyclingAge: Int,
    ): Result<CoinageBalance> = coinageBalanceConverterUseCase.create()
        .map { conversionContext ->
            val spendableCoinsBalance = conversionContext.formatCoinsToBalance(
                coins.filter { it.isSelectable(recyclingAge) }.map { it.coin }
            )
            val pendingCoinsBalance = conversionContext.formatCoinsToBalance(
                coins.filter { it.isMinting() || it.isAwaitingRecycling(recyclingAge) }.map { it.coin }
            )

            val (readyVouchers, unreadyVouchers) = vouchers
                .filter { it.state.isFree }
                .partition { it.isSelectable() }

            val (securedVouchersBalance, degradedVouchersBalance) = readyVouchers
                .map { it.voucher }
                .partition { it.isReadyToUseSecured(currentTimeMillis) }
                .transformPair { conversionContext.formatVouchersToBalance(it) }

            val spendableSecuredBalance = spendableCoinsBalance + securedVouchersBalance

            // As for coins: an asset that is not usable counts as pending only while it is still on its
            // way. A voucher whose minting transaction failed is on its way nowhere.
            val unreadyVouchersBalance = conversionContext.formatVouchersToBalance(
                unreadyVouchers.filter { it.isOnboarding() || it.isMinting() }.map { it.voucher }
            )
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
