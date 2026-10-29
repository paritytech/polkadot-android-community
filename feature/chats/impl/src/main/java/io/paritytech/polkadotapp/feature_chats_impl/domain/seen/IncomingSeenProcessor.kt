package io.paritytech.polkadotapp.feature_chats_impl.domain.seen

import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_impl.data.model.decodeSeenContent
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatSeenRepository
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.ChatMessageStatementContent
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import javax.inject.Inject

class IncomingSeenProcessor @Inject constructor(
    private val chatSeenRepository: ChatSeenRepository,
) {
    suspend fun process(contact: Contact, messages: List<EncodedMessage>) {
        if (contact.isBlocked || messages.isEmpty()) return

        runCancellableCatching { raisePeerSeenSortOrder(contact, messages) }
            .logFailure("Failed to process ${messages.size} seen messages from ${contact.username}")
    }

    private suspend fun raisePeerSeenSortOrder(contact: Contact, messages: List<EncodedMessage>) {
        val chatId = ChatId.fromContact(contact.accountId)

        val sortOrder = messages
            .mapNotNull { it.decodeSeenContent() }
            .mapNotNull { resolveSortOrder(chatId, it) }
            .maxOrNull()
            ?: return

        chatSeenRepository.raisePeerSeenSortOrder(contact.accountId, sortOrder)
    }

    private suspend fun resolveSortOrder(chatId: ChatId, seen: ChatMessageStatementContent.Seen): Long? {
        return chatSeenRepository.getOutgoingSortOrder(chatId, seen.messageId)
            ?: chatSeenRepository.getLatestPostedOutgoingSortOrder(chatId, upTo = seen.timestamp.toLong())
    }
}
