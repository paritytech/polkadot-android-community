package io.paritytech.polkadotapp.feature_chats_impl.data.repository

import io.paritytech.polkadotapp.database.dao.CoinageTransferDetectionDao
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toDomain
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toLocal
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import javax.inject.Inject

interface CoinageTransferDetectionRepository {
    suspend fun getCoinageTransferDetection(messageId: ChatMessageId): CoinageTransferDetection?

    suspend fun saveCoinageTransferDetection(
        messageId: ChatMessageId,
        coinageTransferDetection: CoinageTransferDetection
    )
}

internal class RealCoinageTransferDetectionRepository @Inject constructor(
    private val dao: CoinageTransferDetectionDao
) : CoinageTransferDetectionRepository {
    override suspend fun getCoinageTransferDetection(messageId: ChatMessageId): CoinageTransferDetection? {
        return dao.getCoinageTransferDetection(messageId)?.toDomain()
    }

    override suspend fun saveCoinageTransferDetection(
        messageId: ChatMessageId,
        coinageTransferDetection: CoinageTransferDetection
    ) {
        dao.insertCoinageTransferDetection(coinageTransferDetection.toLocal(messageId))
    }
}
