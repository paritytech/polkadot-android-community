package io.paritytech.polkadotapp.feature_chats_impl.data.repository

import io.paritytech.polkadotapp.database.dao.ChatSeenDao
import io.paritytech.polkadotapp.database.model.ChatMessageLocal
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.LastReadMessage
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
    suspend fun saveReceivedSeen(chatId: ChatId, messageId: ChatMessageId)

    fun subscribeLastReadIncomingMessage(chatId: ChatId): Flow<LastReadMessage?>

    fun subscribeSeenOutgoingMessageIds(chatId: ChatId): Flow<Set<ChatMessageId>>
}

class RealChatSeenRepository @Inject constructor(
    private val dao: ChatSeenDao,
) : ChatSeenRepository {
    override suspend fun saveReceivedSeen(chatId: ChatId, messageId: ChatMessageId) {
        val order = dao.getOutgoingOrder(chatId.toLocal(), messageId) ?: return

        dao.raisePeerSeenOrder(chatId.toLocal(), order)
    }

    override fun subscribeLastReadIncomingMessage(chatId: ChatId): Flow<LastReadMessage?> {
        return dao.subscribeLastReadIncomingMessage(chatId.toLocal(), DISPLAYABLE_MESSAGE_TYPES).map { message ->
            message?.let { LastReadMessage(it.id, it.timestamp) }
        }
    }

    override fun subscribeSeenOutgoingMessageIds(chatId: ChatId): Flow<Set<ChatMessageId>> {
        return dao.subscribeSeenOutgoingMessageIds(chatId.toLocal()).map { it.toSet() }
    }
}
