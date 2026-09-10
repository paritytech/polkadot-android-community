package io.paritytech.polkadotapp.feature_coinage_impl.data.transaction

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableChainProvider
import javax.inject.Inject

/**
 * The chain the durability engine watches.
 *
 * Supplied by coinage because it is the only domain on the ledger today. A second domain on another chain
 * turns this into a per-domain lookup; nothing in the engine assumes there is one.
 */
class CoinageDurableChainProvider @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
) : DurableChainProvider {
    override suspend fun chainId(): ChainId = chainAssetProvider.chainId()
}
