package io.paritytech.polkadotapp.feature_chats_impl.data.repository

import io.paritytech.polkadotapp.database.dao.ChatSeenDao
import io.paritytech.polkadotapp.database.model.ChatMessageLocal
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.LastSeenMessage
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.LastSentSeen
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.toLocal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val DISPLAYABLE_MESSAGE_TYPES = listOf(
    ChatMessageLocal.Type.TEXT,
    ChatMessageLocal.Type.RICH_TEXT,
    ChatMessageLocal.Type.PAYMENT,
    ChatMessageLocal.Type.DATA_CHANNEL_OFFER,
    ChatMessageLocal.Type.DATA_CHANNEL_ANSWER,
    ChatMessageLocal.Type.DATA_CHANNEL_CLOSED,
    ChatMessageLocal.Type.CHAT_REQUEST,
    ChatMessageLocal.Type.UNSUPPORTED,
)

interface ChatSeenRepository {
    suspend fun getOutgoingOrder(chatId: ChatId, messageId: ChatMessageId): Long?

    suspend fun getHighestSentOutgoingOrderUpTo(chatId: ChatId, timestamp: Long): Long?

    suspend fun getIncomingOrder(chatId: ChatId, messageId: ChatMessageId): Long?

    suspend fun raisePeerSeenOrder(chatId: ChatId, order: Long)

    suspend fun raiseLastSentSeen(chatId: ChatId, lastSentSeen: LastSentSeen)

    suspend fun getLastSentSeen(chatId: ChatId): LastSentSeen?

    suspend fun getLastSeenIncomingMessage(chatId: ChatId): LastSeenMessage?

    fun subscribeLastSeenIncomingMessage(chatId: ChatId): Flow<LastSeenMessage?>

    fun subscribePeerSeenOrder(chatId: ChatId): Flow<Long>
}

class RealChatSeenRepository @Inject constructor(
    private val dao: ChatSeenDao,
) : ChatSeenRepository {
    override suspend fun getOutgoingOrder(chatId: ChatId, messageId: ChatMessageId): Long? {
        return dao.getOutgoingOrder(chatId.toLocal(), messageId)
    }

    override suspend fun getHighestSentOutgoingOrderUpTo(chatId: ChatId, timestamp: Long): Long? {
        return dao.getHighestSentOutgoingOrderUpTo(chatId.toLocal(), timestamp)
    }

    override suspend fun getIncomingOrder(chatId: ChatId, messageId: ChatMessageId): Long? {
        return dao.getIncomingOrder(chatId.toLocal(), messageId)
    }

    override suspend fun raisePeerSeenOrder(chatId: ChatId, order: Long) {
        dao.raisePeerSeenOrder(chatId.toLocal(), order)
    }

    override suspend fun raiseLastSentSeen(chatId: ChatId, lastSentSeen: LastSentSeen) {
        dao.raiseLastSentSeen(chatId.toLocal(), lastSentSeen.sortOrder, lastSentSeen.timestamp)
    }

    override suspend fun getLastSentSeen(chatId: ChatId): LastSentSeen? {
        return dao.getSeenState(chatId.toLocal())?.let { LastSentSeen(it.lastSentSeenOrder, it.lastSentSeenTimestamp) }
    }

    override suspend fun getLastSeenIncomingMessage(chatId: ChatId): LastSeenMessage? {
        return dao.getLastSeenIncomingMessage(chatId.toLocal(), DISPLAYABLE_MESSAGE_TYPES)?.toDomain()
    }

    override fun subscribeLastSeenIncomingMessage(chatId: ChatId): Flow<LastSeenMessage?> {
        return dao.subscribeLastSeenIncomingMessage(chatId.toLocal(), DISPLAYABLE_MESSAGE_TYPES).map { it?.toDomain() }
    }

    private fun ChatSeenDao.LastSeenMessageProjection.toDomain(): LastSeenMessage {
        return LastSeenMessage(id, sortOrder, timestamp)
    }

    override fun subscribePeerSeenOrder(chatId: ChatId): Flow<Long> {
        return dao.subscribePeerSeenOrder(chatId.toLocal()).map { it ?: 0 }
    }
}
