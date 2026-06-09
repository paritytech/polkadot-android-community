package io.paritytech.polkadotapp.feature_coinage_impl.data.helpers

import io.paritytech.polkadotapp.chains.network.binding.BlockNumber
import io.paritytech.polkadotapp.database.model.CoinageTransferWalLocal
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CheckpointBlock
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.TransferWalEntry

internal fun TransferWalEntry.toLocal(): CoinageTransferWalLocal = CoinageTransferWalLocal(
    id = id,
    chainId = chainId,
    inputCoinIndices = inputCoinIndices,
    inputVoucherIndices = inputVoucherIndices,
    expectedOutputCoinIndices = expectedOutputCoinIndices,
    checkpointBlockNumber = checkpoint.blockNumber.value.toLong(),
    checkpointBlockHash = checkpoint.blockHash,
    mortalityBlocks = mortalityBlocks.value.toLong(),
    createdAt = createdAt,
)

internal fun CoinageTransferWalLocal.toDomainOrNull(): TransferWalEntry {
    val blockNumber = checkpointBlockNumber
    val blockHash = checkpointBlockHash
    return TransferWalEntry(
        id = id,
        chainId = chainId,
        inputCoinIndices = inputCoinIndices,
        inputVoucherIndices = inputVoucherIndices,
        expectedOutputCoinIndices = expectedOutputCoinIndices,
        checkpoint = CheckpointBlock(
            blockNumber = BlockNumber(blockNumber.toBigInteger()),
            blockHash = blockHash,
        ),
        mortalityBlocks = BlockNumber(mortalityBlocks.toBigInteger()),
        createdAt = createdAt,
    )
}
