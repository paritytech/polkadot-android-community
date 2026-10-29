package io.paritytech.polkadotapp.feature_chats_impl.domain.seen

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_impl.data.model.decodeSeenContent
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatSeenRepository
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.ChatMessageStatementContent
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import javax.inject.Inject

class SeenWatermarkUpdater @Inject constructor(
    private val chatSeenRepository: ChatSeenRepository,
) {
    suspend fun process(messages: List<EncodedMessage>, contact: Contact) {
        val watermarks = receivedWatermarks(messages, contact)
        if (watermarks.isEmpty()) return

        runCancellableCatching { apply(watermarks, contact.accountId) }
            .logFailure("Failed to apply seen watermark from ${contact.username}")
    }

    private fun receivedWatermarks(messages: List<EncodedMessage>, contact: Contact): List<ChatMessageStatementContent.Seen> {
        if (contact.isBlocked) return emptyList()

        return messages.mapNotNull { it.decodeSeenContent() }
    }

    private suspend fun apply(watermarks: List<ChatMessageStatementContent.Seen>, accountId: AccountId) {
        val chatId = ChatId.fromContact(accountId)
        val watermarkOrder = watermarks.mapNotNull { watermarkOrder(it, chatId) }.maxOrNull() ?: return

        chatSeenRepository.raisePeerSeenOrder(chatId, watermarkOrder)
    }

    private suspend fun watermarkOrder(watermark: ChatMessageStatementContent.Seen, chatId: ChatId): Long? {
        return chatSeenRepository.getOutgoingOrder(chatId, watermark.messageId)
            ?: chatSeenRepository.getOutgoingOrderAtOrBefore(chatId, watermark.timestamp.toLong())
    }
}
