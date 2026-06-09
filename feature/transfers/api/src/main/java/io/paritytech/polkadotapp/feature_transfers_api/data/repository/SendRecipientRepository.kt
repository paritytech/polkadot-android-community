package io.paritytech.polkadotapp.feature_transfers_api.data.repository

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.FullChainAssetId
import io.paritytech.polkadotapp.feature_transfers_api.domain.model.SendRecipient
import kotlinx.coroutines.flow.Flow

interface SendRecipientRepository {
    suspend fun getSendRecipients(): Result<List<SendRecipient>>

    fun getSendRecipientsForChainAssetFlow(chainAssetId: FullChainAssetId): Flow<List<SendRecipient>>

    suspend fun addSendRecipient(recipient: SendRecipient): Result<Unit>
}
