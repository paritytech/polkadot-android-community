package io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.domain

import io.paritytech.polkadotapp.common.utils.filterResultSuccess
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TotalBalanceUseCase
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.domain.model.BalanceBreakdown
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface BalanceDetailsInteractor {
    fun observeBalance(): Flow<BalanceBreakdown>
}

class RealBalanceDetailsInteractor @Inject constructor(
    private val totalBalanceUseCase: TotalBalanceUseCase,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
) : BalanceDetailsInteractor {
    override fun observeBalance(): Flow<BalanceBreakdown> = flow {
        val asset = chainAssetProvider.asset()
        emitAll(
            totalBalanceUseCase.subscribeTotalBalance()
                .logFailure("BalanceDetailsInteractor: Failed to get coinage balance")
                .filterResultSuccess()
                .filterNotNull()
                .map { balance ->
                    BalanceBreakdown(
                        asset = asset,
                        total = balance.totalBalance,
                        availableNow = balance.spendableBalance.total,
                        availableNowSecured = balance.spendableBalance.secured,
                        availableNowLowPrivacy = balance.spendableBalance.degraded,
                        availableSoon = balance.pendingBalance,
                    )
                }
        )
    }
}
