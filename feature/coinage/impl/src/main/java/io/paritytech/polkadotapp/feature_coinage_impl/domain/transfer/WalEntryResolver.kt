package io.paritytech.polkadotapp.feature_coinage_impl.domain.transfer

import io.paritytech.polkadotapp.chains.network.binding.BlockNumber
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.TransferWalEntry
import javax.inject.Inject

sealed interface ResolveAction {
    val entry: TransferWalEntry

    data class CommitOutputs(override val entry: TransferWalEntry) : ResolveAction

    data class MarkInputsSpent(override val entry: TransferWalEntry) : ResolveAction

    data class RevertInputs(override val entry: TransferWalEntry) : ResolveAction

    data class Wait(override val entry: TransferWalEntry) : ResolveAction
}

data class WalEntryChainSnapshot(
    val inputsOnChain: Boolean,
    val outputsOnChain: Boolean,
    val checkpointStillCanonical: Boolean,
)

class WalEntryResolver @Inject constructor() {
    fun resolve(
        entry: TransferWalEntry,
        finalizedBlockNumber: BlockNumber,
        snapshot: WalEntryChainSnapshot,
    ): ResolveAction {
        if (!snapshot.checkpointStillCanonical) return ResolveAction.RevertInputs(entry)
        if (snapshot.outputsOnChain) return ResolveAction.CommitOutputs(entry)
        if (!snapshot.inputsOnChain) return ResolveAction.MarkInputsSpent(entry)

        val isExpired = finalizedBlockNumber > entry.checkpoint.blockNumber + entry.mortalityBlocks
        return if (isExpired) {
            ResolveAction.RevertInputs(entry)
        } else {
            ResolveAction.Wait(entry)
        }
    }
}
