package io.paritytech.polkadotapp.feature_chats_impl.data.repository

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.database.dao.ChatSeenDao
import io.paritytech.polkadotapp.database.model.ChatMessageLocal
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.toLocal
import io.paritytech.polkadotapp.feature_chats_impl.domain.seen.LastSeenIncomingMessage
import io.paritytech.polkadotapp.feature_chats_impl.domain.seen.MessagePosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val COUNTED_MESSAGE_TYPES = listOf(
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
    suspend fun getOutgoingSortOrder(chatId: ChatId, messageId: ChatMessageId): Long?

    suspend fun getLatestPostedOutgoingSortOrder(chatId: ChatId, upTo: Timestamp): Long?

    suspend fun raisePeerSeenSortOrder(contactAccountId: AccountId, sortOrder: Long)

    suspend fun getIncomingSortOrder(chatId: ChatId, messageId: ChatMessageId): Long?

    fun subscribeLastSeenIncomingMessage(chatId: ChatId): Flow<LastSeenIncomingMessage?>

    suspend fun getLastSentSeen(contactAccountId: AccountId): MessagePosition?

    suspend fun raiseLastSentSeen(contactAccountId: AccountId, position: MessagePosition)

    fun subscribeSeenOutgoingMessageIds(chatId: ChatId): Flow<Set<ChatMessageId>>
}

class RealChatSeenRepository @Inject constructor(
    private val dao: ChatSeenDao,
) : ChatSeenRepository {
    override suspend fun getOutgoingSortOrder(chatId: ChatId, messageId: ChatMessageId): Long? {
        return dao.getOutgoingSortOrder(chatId.toLocal(), messageId)
    }

    override suspend fun getLatestPostedOutgoingSortOrder(chatId: ChatId, upTo: Timestamp): Long? {
        return dao.getLatestPostedOutgoingSortOrder(chatId.toLocal(), upTo)
    }

    override suspend fun raisePeerSeenSortOrder(contactAccountId: AccountId, sortOrder: Long) {
        dao.raisePeerSeenSortOrder(contactAccountId.value, sortOrder)
    }

    override suspend fun getIncomingSortOrder(chatId: ChatId, messageId: ChatMessageId): Long? {
        return dao.getIncomingSortOrder(chatId.toLocal(), messageId)
    }

    override fun subscribeLastSeenIncomingMessage(chatId: ChatId): Flow<LastSeenIncomingMessage?> {
        return dao.subscribeLastSeenIncomingMessage(chatId.toLocal(), COUNTED_MESSAGE_TYPES).map { message ->
            message?.let { LastSeenIncomingMessage(it.id, MessagePosition(it.sortOrder, it.timestamp)) }
        }
    }

    override suspend fun getLastSentSeen(contactAccountId: AccountId): MessagePosition? {
        return dao.getSeenState(contactAccountId.value)?.let {
            MessagePosition(it.lastSentSeenSortOrder, it.lastSentSeenTimestamp)
        }
    }

    override suspend fun raiseLastSentSeen(contactAccountId: AccountId, position: MessagePosition) {
        dao.raiseLastSentSeen(contactAccountId.value, position.sortOrder, position.timestamp)
    }

    override fun subscribeSeenOutgoingMessageIds(chatId: ChatId): Flow<Set<ChatMessageId>> {
        return dao.subscribeSeenOutgoingMessageIds(chatId.toLocal()).map { it.toSet() }
    }
}
