package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Query
import io.paritytech.polkadotapp.database.model.ChatMessageLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChatSeenDao {
    @Query("SELECT sortOrder FROM chat_messages WHERE chatId = :chatId AND origintype = 'USER' AND LOWER(id) = LOWER(:messageId)")
    abstract suspend fun getOutgoingOrder(chatId: ByteArray, messageId: String): Long?

    @Query(
        """
        INSERT OR REPLACE INTO chat_seen_states (contactAccountId, peerSeenOrder, deliveredSeenMessageId)
        VALUES (
            :contactAccountId,
            MAX(:order, COALESCE((SELECT peerSeenOrder FROM chat_seen_states WHERE contactAccountId = :contactAccountId), 0)),
            (SELECT deliveredSeenMessageId FROM chat_seen_states WHERE contactAccountId = :contactAccountId)
        )
        """
    )
    abstract suspend fun raisePeerSeenOrder(contactAccountId: ByteArray, order: Long)

    @Query(
        """
        INSERT OR REPLACE INTO chat_seen_states (contactAccountId, peerSeenOrder, deliveredSeenMessageId)
        VALUES (
            :contactAccountId,
            COALESCE((SELECT peerSeenOrder FROM chat_seen_states WHERE contactAccountId = :contactAccountId), 0),
            :messageId
        )
        """
    )
    abstract suspend fun saveDeliveredSeenMessageId(contactAccountId: ByteArray, messageId: String)

    @Query("SELECT deliveredSeenMessageId FROM chat_seen_states WHERE contactAccountId = :contactAccountId")
    abstract suspend fun getDeliveredSeenMessageId(contactAccountId: ByteArray): String?

    @Query(
        """
        SELECT id, timestamp FROM chat_messages
        WHERE chatId = :chatId AND origintype != 'USER' AND status = 'IS_READ' AND type IN (:types)
        ORDER BY sortOrder DESC, timestamp DESC
        LIMIT 1
        """
    )
    abstract fun subscribeLastReadIncomingMessage(chatId: ByteArray, types: List<ChatMessageLocal.Type>): Flow<LastReadMessageProjection?>

    @Query(
        """
        SELECT id FROM chat_messages
        WHERE chatId = :chatId AND origintype = 'USER' AND status IN ('IS_SENT', 'IS_READ') AND sortOrder > 0
        AND sortOrder <= (SELECT peerSeenOrder FROM chat_seen_states WHERE contactAccountId = :chatId)
        """
    )
    abstract fun subscribeSeenOutgoingMessageIds(chatId: ByteArray): Flow<List<String>>

    data class LastReadMessageProjection(
        val id: String,
        val timestamp: Long
    )
}
