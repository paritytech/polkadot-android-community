package io.paritytech.polkadotapp.feature_cross_chain_transfers_api.domain

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.feature_account_api.domain.model.MetaAccount
import io.paritytech.polkadotapp.feature_cross_chain_transfers_api.domain.model.CrossChainTransfer
import io.paritytech.polkadotapp.feature_cross_chain_transfers_api.domain.model.CrossChainTransferDirection
import io.paritytech.polkadotapp.feature_cross_chain_transfers_api.domain.model.CrossChainTransferDirectionId
import io.paritytech.polkadotapp.feature_cross_chain_transfers_api.domain.model.CrossChainTransferDryRunOrigin
import io.paritytech.polkadotapp.feature_cross_chain_transfers_api.domain.model.CrossChainTransferDryRunOutcome
import io.paritytech.polkadotapp.feature_cross_chain_transfers_api.domain.model.CrossChainTransferFeatures
import io.paritytech.polkadotapp.feature_cross_chain_transfers_api.domain.model.CrossChainTransferFee
import io.paritytech.polkadotapp.feature_cross_chain_transfers_api.domain.model.CrossChainTransferSuccess
import kotlin.time.Duration

interface CrossChainTransferService {
    context(ComputationalScope)
    suspend fun availableDirectionIds(): List<CrossChainTransferDirectionId>

    context(ComputationalScope)
    suspend fun getTransferFeatures(directionId: CrossChainTransferDirectionId): CrossChainTransferFeatures

    suspend fun getDirectionById(directionId: CrossChainTransferDirectionId): CrossChainTransferDirection

    context(ComputationalScope)
    suspend fun requiredRemainingAmountAfterTransfer(directionId: CrossChainTransferDirectionId): Balance

    context(ComputationalScope)
    suspend fun estimateMaximumExecutionTime(directionId: CrossChainTransferDirectionId): Duration

    context(ComputationalScope)
    suspend fun estimateFee(
        transfer: CrossChainTransfer,
    ): Result<CrossChainTransferFee>

    context(ComputationalScope)
    suspend fun performAndTrackTransfer(
        transfer: CrossChainTransfer,
        sender: MetaAccount
    ): Result<CrossChainTransferSuccess>

    context(ComputationalScope)
    suspend fun dryRunTransfer(
        transfer: CrossChainTransfer,
        dryRunOrigin: CrossChainTransferDryRunOrigin,
    ): Result<CrossChainTransferDryRunOutcome>
}
