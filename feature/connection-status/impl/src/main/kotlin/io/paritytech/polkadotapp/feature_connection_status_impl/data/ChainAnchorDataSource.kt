package io.paritytech.polkadotapp.feature_connection_status_impl.data

import io.paritytech.polkadotapp.chains.di.RemoteSourceQualifier
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.toBlockNumber
import io.paritytech.polkadotapp.chains.network.rpc.RpcCalls
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSource
import io.paritytech.polkadotapp.chains.storage.source.query.api.queryNonNull
import io.paritytech.polkadotapp.chains.storage.typed.now
import io.paritytech.polkadotapp.chains.storage.typed.timestamp
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.BlockProductionAnchor
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class ChainAnchorDataSource @Inject constructor(
    private val rpcCalls: RpcCalls,
    @RemoteSourceQualifier private val remoteStorage: StorageDataSource,
) {
    /**
     * Reads how long the chain took over its most recent [blocks] blocks, by its own timestamps.
     * Returns null when the chain is shorter than the span asked for, or when its timestamps do not
     * move forward across it — an anchor built on either would claim a healthy chain on input
     * already known to be unusable.
     */
    suspend fun fetch(chainId: ChainId, blocks: Int): BlockProductionAnchor? {
        // The height is read off the head hash rather than resolved separately, so the two cannot
        // land a block apart and flatten the measurement by exactly that block.
        val headHash = rpcCalls.getBlockHash(chainId)
        val headHeight = rpcCalls.getBlockHeader(chainId, headHash).number
        if (headHeight < blocks) return null

        val previousHash = rpcCalls.getBlockHash(chainId, (headHeight - blocks).toBigInteger().toBlockNumber())

        val headTime = timestampAt(chainId, headHash)
        val previousTime = timestampAt(chainId, previousHash)
        val span = (headTime - previousTime).toLong().milliseconds
        if (!span.isPositive()) return null

        return BlockProductionAnchor(headHeight = headHeight, blocks = blocks, span = span)
    }

    private suspend fun timestampAt(chainId: ChainId, hash: String): BigInteger =
        remoteStorage.query(chainId, at = hash) { runtime.metadata.timestamp.now.queryNonNull() }
}
