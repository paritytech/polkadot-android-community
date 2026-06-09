package io.paritytech.polkadotapp.feature_transfers_api.domain.usecase

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.FullChainAssetId
import io.paritytech.polkadotapp.feature_transfers_api.domain.model.SendRecipientLabeled
import kotlinx.coroutines.flow.Flow

interface PreviousSendRecipientsUseCase {
    suspend operator fun invoke(): Result<List<SendRecipientLabeled>>

    fun getSendRecipientsForChainAssetFlow(chainAssetId: FullChainAssetId): Flow<List<SendRecipientLabeled>>
}
