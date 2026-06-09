package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.BlockNumber
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_impl.domain.common.TRANSFER_WAL_MORTALITY_BLOCKS
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.TransferWalEntry
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transfer.CheckpointBlockFetcher
import java.util.UUID
import javax.inject.Inject

class WalEntryBuilder @Inject constructor(
    private val checkpointBlockFetcher: CheckpointBlockFetcher
) {
    suspend fun createNewWalEntry(
        chainId: ChainId,
        inputCoins: List<Coin>,
        inputVouchers: List<RecyclerVoucher>,
        outputCoins: List<Coin>
    ): Result<TransferWalEntry> = checkpointBlockFetcher.fetch(chainId).map { checkpoint ->
        TransferWalEntry(
            id = UUID.randomUUID().toString(),
            chainId = chainId,
            inputCoinIndices = inputCoins.map { it.derivationIndex },
            inputVoucherIndices = inputVouchers.map { it.ringVrfKeyIndex },
            expectedOutputCoinIndices = outputCoins.map { it.derivationIndex },
            checkpoint = checkpoint,
            mortalityBlocks = BlockNumber(TRANSFER_WAL_MORTALITY_BLOCKS.toBigInteger()),
            createdAt = System.currentTimeMillis(),
        )
    }
}
