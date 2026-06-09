package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.paritytech.polkadotapp.database.model.ChatMessageLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(local: ChatMessageLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(local: List<ChatMessageLocal>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfNotExists(local: List<ChatMessageLocal>): List<Long>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp DESC")
    abstract fun subscribeMessages(chatId: ByteArray): Flow<List<ChatMessageLocal>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    abstract suspend fun getMessages(chatId: ByteArray): List<ChatMessageLocal>

    @Query("SELECT * FROM chat_messages WHERE updatedAt > :after ORDER BY updatedAt ASC")
    abstract suspend fun getMessagesUpdatedAfter(after: Long): List<ChatMessageLocal>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    abstract suspend fun getMessage(messageId: String): ChatMessageLocal?

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId AND status = :status AND origintype == 'USER'")
    abstract fun subscribeOutgoingMessagesByStatus(
        chatId: ByteArray,
        status: ChatMessageLocal.Status
    ): Flow<List<ChatMessageLocal>>

    @Query("""
        SELECT * FROM chat_messages
        WHERE chatId = :chatId AND type = :type
        AND status = :status
        AND ((:isOutgoing = 1 AND origintype = 'USER') OR (:isOutgoing = 0 AND origintype != 'USER'))
    """)
    abstract fun subscribeMessages(chatId: ByteArray, type: ChatMessageLocal.Type, status: ChatMessageLocal.Status, isOutgoing: Boolean): Flow<List<ChatMessageLocal>>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    abstract fun subscribeMessageById(messageId: String): Flow<ChatMessageLocal?>

    @Query("SELECT * FROM chat_messages WHERE type IN (:types)")
    abstract fun subscribeMessagesByTypes(types: List<ChatMessageLocal.Type>): Flow<List<ChatMessageLocal>>

    @Query("UPDATE chat_messages SET status = :newStatus, updatedAt = :updatedAt WHERE id = :messageId")
    abstract suspend fun updateStatus(messageId: String, newStatus: ChatMessageLocal.Status, updatedAt: Long): Int

    @Update(entity = ChatMessageLocal::class)
    abstract suspend fun updateMessagesContents(updates: List<MessageContentUpdateLocal>)

    @Query("UPDATE chat_messages SET status = :newStatus, updatedAt = :updatedAt WHERE id IN (:messageIds)")
    abstract suspend fun updateMessagesStatus(messageIds: List<String>, newStatus: ChatMessageLocal.Status, updatedAt: Long): Int

    @Query("UPDATE chat_messages SET status = :toStatus WHERE chatId = :chatId AND status = :fromStatus AND origintype == 'USER'")
    abstract suspend fun updateOutgoingMessagesStatusForChat(chatId: ByteArray, fromStatus: ChatMessageLocal.Status, toStatus: ChatMessageLocal.Status)

    @Query("UPDATE chat_messages SET status = :toStatus WHERE chatId = :chatId AND status = :fromStatus AND origintype == 'USER' AND type IN (:types)")
    abstract suspend fun updateOutgoingMessagesStatusForChatWithTypes(chatId: ByteArray, fromStatus: ChatMessageLocal.Status, toStatus: ChatMessageLocal.Status, types: List<ChatMessageLocal.Type>)

    @Query("UPDATE chat_messages SET status = :toStatus WHERE chatId = :chatId AND status = :fromStatus AND origintype == 'USER' AND type NOT IN (:types)")
    abstract suspend fun updateOutgoingMessagesStatusForChatExcludingTypes(chatId: ByteArray, fromStatus: ChatMessageLocal.Status, toStatus: ChatMessageLocal.Status, types: List<ChatMessageLocal.Type>)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    abstract suspend fun remove(messageId: String)

    @Query("UPDATE chat_messages SET status = 'IS_READ' WHERE chatId = :chatId AND timestamp <= :timestamp AND status = 'NEW' AND origintype != 'USER'")
    abstract suspend fun markMessagesAsReadUpToTimestamp(chatId: ByteArray, timestamp: Long)

    @Query("UPDATE chat_messages SET status = 'IS_READ' WHERE chatId = :chatId AND status = 'NEW' AND origintype != 'USER' AND type IN (:types)")
    abstract suspend fun markMessagesByTypesAsRead(chatId: ByteArray, types: List<String>)

    @Query("UPDATE chat_messages SET status = 'IS_READ' WHERE id = :messageId")
    abstract suspend fun markMessageAsRead(messageId: String)

    @Query("""
        WITH per_chat AS (
          SELECT
            chatId,
            COUNT(CASE WHEN status = 'NEW' AND origintype != 'USER' THEN 1 END) AS unseenCount,
            MAX(timestamp) AS lastTimestamp,
            id
          FROM chat_messages
          WHERE (:includeInternal = 1 OR isInternal = 0)
          GROUP BY chatId
        )
        SELECT
          pc.chatId as chatId,
          pc.unseenCount as unseenCount,
          m.*
        FROM per_chat pc
        JOIN chat_messages m ON m.id = pc.id
        WHERE (:includeInternal = 1 OR m.isInternal = 0)
        ORDER BY m.timestamp DESC
    """)
    abstract fun subscribeChatSummaries(includeInternal: Boolean = false): Flow<List<ChatSummaryLocal>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId AND type = 'UNSUPPORTED'")
    abstract suspend fun getUnsupportedMessages(chatId: ByteArray): List<ChatMessageLocal>

    @Query("DELETE FROM chat_messages WHERE chatId = :chatId")
    abstract suspend fun deleteAllMessages(chatId: ByteArray)

    @Query("SELECT MAX(timestamp) FROM chat_messages WHERE chatId = :chatId")
    abstract suspend fun getLatestTimestamp(chatId: ByteArray): Long?

    @Query("UPDATE chat_messages SET timestamp = timestamp + :delta WHERE chatId = :chatId")
    abstract suspend fun shiftTimestamps(chatId: ByteArray, delta: Long)

    @Transaction
    open suspend fun updateTimestamps(chatId: ByteArray, targetTimestamp: Long) {
        val latest = getLatestTimestamp(chatId) ?: return
        val delta = targetTimestamp - latest
        if (delta != 0L) {
            shiftTimestamps(chatId, delta)
        }
    }

    @Query("SELECT id, status FROM chat_messages WHERE id IN (:messageIds)")
    abstract suspend fun getMessageStatuses(messageIds: List<String>): List<MessageStatusProjection>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfNotExists(local: ChatMessageLocal): Long

    data class MessageStatusProjection(
        val id: String,
        val status: ChatMessageLocal.Status
    )

    data class ChatSummaryLocal(
        @Embedded
        val lastMessage: ChatMessageLocal,
        val unseenCount: Int
    )

    class MessageContentUpdateLocal(
        val id: String,
        val content: ByteArray?,
        val type: ChatMessageLocal.Type,
        val updatedAt: Long
    )
}
