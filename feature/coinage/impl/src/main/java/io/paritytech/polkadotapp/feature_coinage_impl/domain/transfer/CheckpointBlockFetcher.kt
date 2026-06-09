package io.paritytech.polkadotapp.feature_coinage_impl.domain.transfer

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.BlockNumber
import io.paritytech.polkadotapp.chains.network.rpc.RpcCalls
import io.paritytech.polkadotapp.chains.network.rpc.getBlockNumber
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CheckpointBlock
import javax.inject.Inject

class CheckpointBlockFetcher @Inject constructor(
    private val rpcCalls: RpcCalls,
) {
    suspend fun fetch(chainId: ChainId): Result<CheckpointBlock> = runCatching {
        val blockHash = rpcCalls.getFinalizedHead(chainId)
        val blockNumber = rpcCalls.getBlockNumber(chainId, blockHash)
        CheckpointBlock(
            blockNumber = BlockNumber(blockNumber.toBigInteger()),
            blockHash = blockHash,
        )
    }
}
