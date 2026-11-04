package io.paritytech.polkadotapp.feature_chats_impl.domain.sessions

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatSeenRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.TransientMessage
import javax.inject.Inject

class TransientMessageProcessor @Inject constructor(
    private val chatSeenRepository: ChatSeenRepository,
) {
    suspend fun processReceived(contact: Contact, messages: List<TransientMessage>) {
        if (contact.isBlocked) return

        val chatId = ChatId.fromContact(contact.accountId)
        for (message in messages) {
            when (message) {
                is TransientMessage.Seen -> runCancellableCatching { chatSeenRepository.saveReceivedSeen(chatId, message.messageId, message.timestamp) }
                    .logFailure("Failed to save a seen message from ${contact.accountId}")
            }
        }
    }

    suspend fun processDelivered(contactAccountId: AccountId, messages: List<TransientMessage>) {
        val chatId = ChatId.fromContact(contactAccountId)
        for (message in messages) {
            when (message) {
                is TransientMessage.Seen -> runCancellableCatching { chatSeenRepository.saveDeliveredSeen(chatId, message.messageId, message.timestamp) }
                    .logFailure("Failed to save a delivered seen message to $contactAccountId")
            }
        }
    }
}
