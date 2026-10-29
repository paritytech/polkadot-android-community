package io.paritytech.polkadotapp.feature_chats_impl.data.repository

import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.database.dao.ChatSeenDao
import io.paritytech.polkadotapp.database.model.ChatMessageLocal
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.toLocal
import io.paritytech.polkadotapp.feature_chats_impl.domain.seen.SeenWatermarkState
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

    suspend fun getOutgoingOrderAtOrBefore(chatId: ChatId, timestamp: Timestamp): Long?

    suspend fun raisePeerSeenOrder(chatId: ChatId, order: Long)

    fun subscribeSeenWatermarkState(chatId: ChatId): Flow<SeenWatermarkState?>

    suspend fun getIncomingOrder(chatId: ChatId, messageId: ChatMessageId): Long?

    suspend fun raiseLastSentSeen(chatId: ChatId, order: Long, timestamp: Timestamp)

    fun subscribeSeenOutgoingMessageIds(chatId: ChatId): Flow<Set<ChatMessageId>>
}

class RealChatSeenRepository @Inject constructor(
    private val dao: ChatSeenDao,
) : ChatSeenRepository {
    override suspend fun getOutgoingOrder(chatId: ChatId, messageId: ChatMessageId): Long? {
        return dao.getOutgoingOrder(chatId.toLocal(), messageId)
    }

    override suspend fun getOutgoingOrderAtOrBefore(chatId: ChatId, timestamp: Timestamp): Long? {
        return dao.getOutgoingOrderAtOrBefore(chatId.toLocal(), timestamp)
    }

    override suspend fun raisePeerSeenOrder(chatId: ChatId, order: Long) {
        dao.raisePeerSeenOrder(chatId.toLocal(), order)
    }

    override fun subscribeSeenWatermarkState(chatId: ChatId): Flow<SeenWatermarkState?> {
        return dao.subscribeSeenWatermarkCandidate(chatId.toLocal(), DISPLAYABLE_MESSAGE_TYPES).map { candidate ->
            candidate?.let {
                val seenState = dao.getSeenState(chatId.toLocal())

                SeenWatermarkState(
                    messageId = it.id,
                    order = it.sortOrder,
                    timestamp = it.timestamp,
                    lastSentOrder = seenState?.lastSentSeenOrder ?: 0,
                    lastSentTimestamp = seenState?.lastSentSeenTimestamp ?: 0,
                )
            }
        }
    }

    override suspend fun getIncomingOrder(chatId: ChatId, messageId: ChatMessageId): Long? {
        return dao.getIncomingOrder(chatId.toLocal(), messageId)
    }

    override suspend fun raiseLastSentSeen(chatId: ChatId, order: Long, timestamp: Timestamp) {
        dao.raiseLastSentSeen(chatId.toLocal(), order, timestamp)
    }

    override fun subscribeSeenOutgoingMessageIds(chatId: ChatId): Flow<Set<ChatMessageId>> {
        return dao.subscribeSeenOutgoingMessageIds(chatId.toLocal()).map { it.toSet() }
    }
}
