package io.paritytech.polkadotapp.feature_chats_impl.domain.seen

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_impl.data.model.decodeSeenContent
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatSeenRepository
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.ChatMessageStatementContent
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import javax.inject.Inject

class SeenWatermarkDeliveryRecorder @Inject constructor(
    private val chatSeenRepository: ChatSeenRepository,
) {
    suspend fun recordDelivered(messages: List<EncodedMessage>, contactAccountId: AccountId) {
        val watermarks = messages.mapNotNull { it.decodeSeenContent() }
        if (watermarks.isEmpty()) return

        runCancellableCatching { record(watermarks, ChatId.fromContact(contactAccountId)) }
            .logFailure("Failed to record delivered seen watermark to $contactAccountId")
    }

    private suspend fun record(watermarks: List<ChatMessageStatementContent.Seen>, chatId: ChatId) {
        val newest = deliveredMarks(watermarks, chatId).maxWithOrNull(compareBy(DeliveredMark::order, DeliveredMark::timestamp)) ?: return

        chatSeenRepository.raiseLastSentSeen(chatId, newest.order, newest.timestamp)
    }

    private suspend fun deliveredMarks(watermarks: List<ChatMessageStatementContent.Seen>, chatId: ChatId): List<DeliveredMark> {
        return watermarks.mapNotNull { watermark ->
            chatSeenRepository.getIncomingOrder(chatId, watermark.messageId)?.let { order ->
                DeliveredMark(order, watermark.timestamp.toLong())
            }
        }
    }

    private data class DeliveredMark(val order: Long, val timestamp: Timestamp)
}
