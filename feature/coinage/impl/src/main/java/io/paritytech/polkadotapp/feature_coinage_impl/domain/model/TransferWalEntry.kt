package io.paritytech.polkadotapp.feature_coinage_impl.domain.model

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.BlockHash
import io.paritytech.polkadotapp.chains.network.binding.BlockNumber
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.DerivationIndex

data class TransferWalEntry(
    val id: String,
    val chainId: ChainId,
    val inputCoinIndices: List<DerivationIndex>,
    val inputVoucherIndices: List<DerivationIndex>,
    val expectedOutputCoinIndices: List<DerivationIndex>,
    val checkpoint: CheckpointBlock,
    val mortalityBlocks: BlockNumber,
    val createdAt: Long,
)

data class CheckpointBlock(
    val blockNumber: BlockNumber,
    val blockHash: BlockHash,
)
