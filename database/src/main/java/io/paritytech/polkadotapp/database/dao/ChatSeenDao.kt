package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import io.paritytech.polkadotapp.database.model.ChatMessageLocal
import io.paritytech.polkadotapp.database.model.ChatSeenStateLocal
import kotlinx.coroutines.flow.Flow

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
    abstract suspend fun getOutgoingOrderAtOrBefore(chatId: ByteArray, timestamp: Long): Long?

    @Query("SELECT sortOrder FROM chat_messages WHERE chatId = :chatId AND origintype != 'USER' AND LOWER(id) = LOWER(:messageId)")
    abstract suspend fun getIncomingOrder(chatId: ByteArray, messageId: String): Long?

    @Query(
        """
        SELECT id, sortOrder, timestamp FROM chat_messages
        WHERE chatId = :chatId AND origintype != 'USER' AND status = 'IS_READ' AND type IN (:types)
        ORDER BY sortOrder DESC, timestamp DESC
        LIMIT 1
        """
    )
    abstract fun subscribeSeenWatermarkCandidate(chatId: ByteArray, types: List<ChatMessageLocal.Type>): Flow<SeenWatermarkCandidate?>

    @Query(
        """
        SELECT id FROM chat_messages
        WHERE chatId = :chatId AND origintype = 'USER' AND status IN ('IS_SENT', 'IS_READ') AND sortOrder > 0
        AND sortOrder <= (SELECT peerSeenOrder FROM chat_seen_states WHERE contactAccountId = :chatId)
        """
    )
    abstract fun subscribeSeenOutgoingMessageIds(chatId: ByteArray): Flow<List<String>>

    @Query("SELECT * FROM chat_seen_states WHERE contactAccountId = :contactAccountId")
    abstract suspend fun getSeenState(contactAccountId: ByteArray): ChatSeenStateLocal?

    @Transaction
    open suspend fun raisePeerSeenOrder(contactAccountId: ByteArray, order: Long) {
        insertIfAbsent(contactAccountId)
        updatePeerSeenOrderIfHigher(contactAccountId, order)
    }

    @Transaction
    open suspend fun raiseLastSentSeen(contactAccountId: ByteArray, order: Long, timestamp: Long) {
        insertIfAbsent(contactAccountId)
        updateLastSentSeenIfHigher(contactAccountId, order, timestamp)
    }

    @Query(
        """
        INSERT OR IGNORE INTO chat_seen_states (contactAccountId, peerSeenOrder, lastSentSeenOrder, lastSentSeenTimestamp)
        VALUES (:contactAccountId, 0, 0, 0)
        """
    )
    protected abstract suspend fun insertIfAbsent(contactAccountId: ByteArray)

    @Query("UPDATE chat_seen_states SET peerSeenOrder = :order WHERE contactAccountId = :contactAccountId AND peerSeenOrder < :order")
    protected abstract suspend fun updatePeerSeenOrderIfHigher(contactAccountId: ByteArray, order: Long)

    @Query(
        """
        UPDATE chat_seen_states SET lastSentSeenOrder = :order, lastSentSeenTimestamp = :timestamp
        WHERE contactAccountId = :contactAccountId
        AND (lastSentSeenOrder < :order OR (lastSentSeenOrder = :order AND lastSentSeenTimestamp < :timestamp))
        """
    )
    protected abstract suspend fun updateLastSentSeenIfHigher(contactAccountId: ByteArray, order: Long, timestamp: Long)

    data class SeenWatermarkCandidate(
        val id: String,
        val sortOrder: Long,
        val timestamp: Long
    )
}
