package io.paritytech.polkadotapp.feature_transactions.api.domain.durable

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId

/**
 * The chain the durability engine watches.
 *
 * Supplied by a consumer rather than assumed, so a domain on another chain needs no change to the engine.
 */
interface DurableChainProvider {
    suspend fun chainId(): ChainId
}
