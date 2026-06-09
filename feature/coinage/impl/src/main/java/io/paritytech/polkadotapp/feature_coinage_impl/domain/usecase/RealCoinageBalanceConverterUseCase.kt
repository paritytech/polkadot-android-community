package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.withRuntime
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinageBalanceConversionContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.coinage
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.underlyingAssetUnit
import io.paritytech.polkadotapp.feature_coinage_impl.domain.common.RealCoinageBalanceConversionContext
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import javax.inject.Inject

class RealCoinageBalanceConverterUseCase @Inject constructor(
    private val chainRegistry: ChainRegistry,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider
) : CoinageBalanceConverterUseCase {
    override suspend fun create(): Result<CoinageBalanceConversionContext> {
        return runCatching {
            val unit = chainRegistry.withRuntime(chainAssetProvider.chain().id) {
                runtime.metadata.coinage.underlyingAssetUnit
            }

            RealCoinageBalanceConversionContext(
                unit = unit,
                precision = chainAssetProvider.asset().precision
            )
        }
    }
}
