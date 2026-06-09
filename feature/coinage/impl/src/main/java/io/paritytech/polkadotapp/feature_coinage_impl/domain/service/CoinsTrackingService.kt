package io.paritytech.polkadotapp.feature_coinage_impl.domain.service

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.getOrEmpty
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinUpdate
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.OnChainCoinInfo
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogE
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class CoinsTrackingService @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val coinRepository: CoinRepository
) {
    context(ComputationalScope)
    suspend fun start() {
        val asset = chainAssetProvider.asset()

        coinRepository.subscribeCoinsExcludingSpentOnChain()
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .flatMapLatest { coins ->
                coinRepository.subscribeCoinsInfoFor(asset.chainId, coins.map { it.accountId })
                    .map { it.logFailure("Can't fetch info for coins").getOrEmpty() }
                    .onEach {
                        coinRepository.updateCoins(coins.toAgeUpdates(it))
                    }
            }
            .launchIn(this@ComputationalScope)
    }

    private fun List<Coin>.toAgeUpdates(onChainData: Map<AccountId, OnChainCoinInfo?>) = mapNotNull { coin ->
        val coinInfo = onChainData[coin.accountId]
        if (coinInfo != null && coinInfo.value != coin.valueExponent.value) {
            coinageLogE("TrackingCoin: ${coin.accountId} with exponent ${coin.valueExponent} doesn't match on chain exponent ${coinInfo.value}")
        }
        when (val age = coin.age) {
            is Coin.Age.Known -> CoinUpdate(
                accountId = coin.accountId,
                age = age.value,
                spentState = if (coinInfo == null) Coin.SpentState.SPENT_ON_CHAIN else coin.spentState
            )

            is Coin.Age.Unknown -> coinInfo?.let {
                CoinUpdate(
                    accountId = coin.accountId,
                    age = coinInfo.age,
                    spentState = coin.spentState
                )
            }
        }
    }
}
