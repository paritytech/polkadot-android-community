package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Query
import io.paritytech.polkadotapp.database.model.ChatMessageLocal
import io.paritytech.polkadotapp.database.model.ChatSeenStateLocal
import kotlinx.coroutines.flow.Flow

private const val LAST_SEEN_INCOMING_MESSAGE_QUERY = """
    SELECT id, sortOrder, timestamp FROM chat_messages
    WHERE chatId = :chatId AND origintype != 'USER' AND status = 'IS_READ' AND type IN (:types)
    ORDER BY sortOrder DESC, timestamp DESC
    LIMIT 1
"""

@Dao
abstract class ChatSeenDao {
    @Query("SELECT sortOrder FROM chat_messages WHERE chatId = :chatId AND origintype = 'USER' AND LOWER(id) = LOWER(:messageId)")
    abstract suspend fun getOutgoingOrder(chatId: ByteArray, messageId: String): Long?

    @Query(
        """
        SELECT MAX(sortOrder) FROM chat_messages
        WHERE chatId = :chatId AND origintype = 'USER' AND status IN ('IS_SENT', 'IS_READ') AND timestamp <= :timestamp
        """
    )
    abstract suspend fun getHighestSentOutgoingOrderUpTo(chatId: ByteArray, timestamp: Long): Long?

    @Query("SELECT sortOrder FROM chat_messages WHERE chatId = :chatId AND origintype != 'USER' AND LOWER(id) = LOWER(:messageId)")
    abstract suspend fun getIncomingOrder(chatId: ByteArray, messageId: String): Long?

    @Query(
        """
        INSERT OR REPLACE INTO chat_seen_states (contactAccountId, peerSeenOrder, lastSentSeenOrder, lastSentSeenTimestamp)
        VALUES (
            :contactAccountId,
            MAX(:order, COALESCE((SELECT peerSeenOrder FROM chat_seen_states WHERE contactAccountId = :contactAccountId), 0)),
            COALESCE((SELECT lastSentSeenOrder FROM chat_seen_states WHERE contactAccountId = :contactAccountId), 0),
            COALESCE((SELECT lastSentSeenTimestamp FROM chat_seen_states WHERE contactAccountId = :contactAccountId), 0)
        )
        """
    )
    abstract suspend fun raisePeerSeenOrder(contactAccountId: ByteArray, order: Long)

    @Query(
        """
        INSERT OR REPLACE INTO chat_seen_states (contactAccountId, peerSeenOrder, lastSentSeenOrder, lastSentSeenTimestamp)
        SELECT
            :contactAccountId,
            COALESCE((SELECT peerSeenOrder FROM chat_seen_states WHERE contactAccountId = :contactAccountId), 0),
            :order,
            :timestamp
        WHERE NOT EXISTS (
            SELECT 1 FROM chat_seen_states
            WHERE contactAccountId = :contactAccountId
            AND (lastSentSeenOrder > :order OR (lastSentSeenOrder = :order AND lastSentSeenTimestamp >= :timestamp))
        )
        """
    )
    abstract suspend fun raiseLastSentSeen(contactAccountId: ByteArray, order: Long, timestamp: Long)

    @Query("SELECT * FROM chat_seen_states WHERE contactAccountId = :contactAccountId")
    abstract suspend fun getSeenState(contactAccountId: ByteArray): ChatSeenStateLocal?

    @Query(LAST_SEEN_INCOMING_MESSAGE_QUERY)
    abstract suspend fun getLastSeenIncomingMessage(chatId: ByteArray, types: List<ChatMessageLocal.Type>): LastSeenMessageProjection?

    @Query(LAST_SEEN_INCOMING_MESSAGE_QUERY)
    abstract fun subscribeLastSeenIncomingMessage(chatId: ByteArray, types: List<ChatMessageLocal.Type>): Flow<LastSeenMessageProjection?>

    @Query("SELECT peerSeenOrder FROM chat_seen_states WHERE contactAccountId = :contactAccountId")
    abstract fun subscribePeerSeenOrder(contactAccountId: ByteArray): Flow<Long?>

    data class LastSeenMessageProjection(
        val id: String,
        val sortOrder: Long,
        val timestamp: Long
    )
}
