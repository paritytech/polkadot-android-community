package io.paritytech.polkadotapp.feature_chats_api.domain.interactors

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageSyncCursor
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface ApplyRemoteChatMessageUseCase {
    data class MessageForSync(
        val encoded: ByteArray,
        val peerAccountId: AccountId,
        val isOutgoing: Boolean,
        val status: ChatMessage.Status,
        val timestamp: Long,
    )

    class MessagePageForSync(
        val messages: List<MessageForSync>,
        val nextCursor: ChatMessageSyncCursor?,
    )

    suspend fun apply(
        encoded: ByteArray,
        peerAccountId: AccountId,
        isOutgoing: Boolean,
        status: ChatMessage.Status,
    )

    suspend fun getMessagesUpdatedAfter(after: Instant): List<MessageForSync>

    suspend fun getMessagePageForSync(
        updatedAfter: Instant,
        updatedUpTo: Instant?,
        after: ChatMessageSyncCursor?,
        limit: Int,
    ): MessagePageForSync

    suspend fun countMessagesForSync(updatedAfter: Instant, updatedUpTo: Instant?): Int

    /** Ticks whenever a message is saved locally — drives active device-sync re-pushes. */
    fun observeLocalMessageChanges(): Flow<Unit>
}
