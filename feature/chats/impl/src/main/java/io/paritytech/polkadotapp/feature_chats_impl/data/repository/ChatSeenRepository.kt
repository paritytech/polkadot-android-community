package io.paritytech.polkadotapp.feature_chats_impl.data.repository

import io.paritytech.polkadotapp.database.dao.ChatSeenDao
import io.paritytech.polkadotapp.database.model.ChatMessageLocal
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.LastReadMessage
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
    suspend fun saveReceivedSeen(chatId: ChatId, messageId: ChatMessageId, timestamp: Long)

    suspend fun saveDeliveredSeen(chatId: ChatId, messageId: ChatMessageId, timestamp: Long)

    suspend fun getLastSentSeen(chatId: ChatId): LastSentSeen?

    fun subscribeLastReadIncomingMessage(chatId: ChatId): Flow<LastReadMessage?>

    fun subscribePeerSeenOrder(chatId: ChatId): Flow<Long>
}

class RealChatSeenRepository @Inject constructor(
    private val dao: ChatSeenDao,
) : ChatSeenRepository {
    override suspend fun saveReceivedSeen(chatId: ChatId, messageId: ChatMessageId, timestamp: Long) {
        val order = dao.getOutgoingOrder(chatId.toLocal(), messageId)
            ?: dao.getHighestSentOutgoingOrderUpTo(chatId.toLocal(), timestamp)
            ?: return

        dao.raisePeerSeenOrder(chatId.toLocal(), order)
    }

    override suspend fun saveDeliveredSeen(chatId: ChatId, messageId: ChatMessageId, timestamp: Long) {
        val order = dao.getIncomingOrder(chatId.toLocal(), messageId) ?: return

        dao.raiseLastSentSeen(chatId.toLocal(), order, timestamp)
    }

    override suspend fun getLastSentSeen(chatId: ChatId): LastSentSeen? {
        return dao.getSeenState(chatId.toLocal())?.let { LastSentSeen(it.lastSentSeenOrder, it.lastSentSeenTimestamp) }
    }

    override fun subscribeLastReadIncomingMessage(chatId: ChatId): Flow<LastReadMessage?> {
        return dao.subscribeLastReadIncomingMessage(chatId.toLocal(), DISPLAYABLE_MESSAGE_TYPES).map { message ->
            message?.let { LastReadMessage(it.id, it.sortOrder, it.timestamp) }
        }
    }

    override fun subscribePeerSeenOrder(chatId: ChatId): Flow<Long> {
        return dao.subscribePeerSeenOrder(chatId.toLocal()).map { it ?: 0 }
    }
}
