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
    abstract suspend fun getOutgoingSortOrder(chatId: ByteArray, messageId: String): Long?

    @Query(
        """
        SELECT MAX(sortOrder) FROM chat_messages
        WHERE chatId = :chatId AND origintype = 'USER' AND status IN ('IS_SENT', 'IS_READ') AND timestamp <= :timestamp
        """
    )
    abstract suspend fun getLatestPostedOutgoingSortOrder(chatId: ByteArray, timestamp: Long): Long?

    @Query("SELECT sortOrder FROM chat_messages WHERE chatId = :chatId AND origintype != 'USER' AND LOWER(id) = LOWER(:messageId)")
    abstract suspend fun getIncomingSortOrder(chatId: ByteArray, messageId: String): Long?

    @Query(
        """
        SELECT id, sortOrder, timestamp FROM chat_messages
        WHERE chatId = :chatId AND origintype != 'USER' AND status = 'IS_READ' AND type IN (:types)
        ORDER BY sortOrder DESC, timestamp DESC
        LIMIT 1
        """
    )
    abstract fun subscribeLastSeenIncomingMessage(chatId: ByteArray, types: List<ChatMessageLocal.Type>): Flow<SeenMessageProjection?>

    @Query(
        """
        SELECT id FROM chat_messages
        WHERE chatId = :chatId AND origintype = 'USER' AND status IN ('IS_SENT', 'IS_READ') AND sortOrder > 0
        AND sortOrder <= (SELECT peerSeenSortOrder FROM chat_seen_states WHERE contactAccountId = :chatId)
        """
    )
    abstract fun subscribeSeenOutgoingMessageIds(chatId: ByteArray): Flow<List<String>>

    @Query("SELECT * FROM chat_seen_states WHERE contactAccountId = :contactAccountId")
    abstract suspend fun getSeenState(contactAccountId: ByteArray): ChatSeenStateLocal?

    @Transaction
    open suspend fun raisePeerSeenSortOrder(contactAccountId: ByteArray, sortOrder: Long) {
        insertIfAbsent(contactAccountId)
        updatePeerSeenSortOrderIfHigher(contactAccountId, sortOrder)
    }

    @Transaction
    open suspend fun raiseLastSentSeen(contactAccountId: ByteArray, sortOrder: Long, timestamp: Long) {
        insertIfAbsent(contactAccountId)
        updateLastSentSeenIfHigher(contactAccountId, sortOrder, timestamp)
    }

    @Query(
        """
        INSERT OR IGNORE INTO chat_seen_states (contactAccountId, peerSeenSortOrder, lastSentSeenSortOrder, lastSentSeenTimestamp)
        VALUES (:contactAccountId, 0, 0, 0)
        """
    )
    protected abstract suspend fun insertIfAbsent(contactAccountId: ByteArray)

    @Query("UPDATE chat_seen_states SET peerSeenSortOrder = :sortOrder WHERE contactAccountId = :contactAccountId AND peerSeenSortOrder < :sortOrder")
    protected abstract suspend fun updatePeerSeenSortOrderIfHigher(contactAccountId: ByteArray, sortOrder: Long)

    @Query(
        """
        UPDATE chat_seen_states SET lastSentSeenSortOrder = :sortOrder, lastSentSeenTimestamp = :timestamp
        WHERE contactAccountId = :contactAccountId
        AND (lastSentSeenSortOrder < :sortOrder OR (lastSentSeenSortOrder = :sortOrder AND lastSentSeenTimestamp < :timestamp))
        """
    )
    protected abstract suspend fun updateLastSentSeenIfHigher(contactAccountId: ByteArray, sortOrder: Long, timestamp: Long)

    data class SeenMessageProjection(
        val id: String,
        val sortOrder: Long,
        val timestamp: Long
    )
}
