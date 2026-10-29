package io.paritytech.polkadotapp.feature_chats_impl.domain.seen

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.ChatActiveTracker
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_impl.data.model.decodeSeenContent
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toEncodedMessage
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatSeenRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.isActiveChat
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.ChatMessageStatementContent
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private val SEND_DELAY = 1.seconds

class SeenMessageHandler @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val chatSeenRepository: ChatSeenRepository,
    private val chatActiveTracker: ChatActiveTracker,
) {
    @Volatile
    private var deliveredSeenMessageId: ChatMessageId? = null

    suspend fun saveReceived(messages: List<EncodedMessage>, contactAccountId: AccountId) {
        val contact = contactsRepository.getContact(contactAccountId) ?: return
        if (contact.isBlocked) return

        val chatId = ChatId.fromContact(contactAccountId)
        messages.mapNotNull { it.decodeSeenContent() }.forEach { seen ->
            runCancellableCatching { chatSeenRepository.saveReceivedSeen(chatId, seen) }
                .logFailure("Failed to save a seen message from $contactAccountId")
        }
    }

    fun saveDelivered(messages: List<EncodedMessage>) {
        messages.mapNotNull { it.decodeSeenContent() }.lastOrNull()?.let { deliveredSeenMessageId = it.messageId }
    }

    fun sendSeenMessages(contactAccountId: AccountId, send: (EncodedMessage) -> Unit): Flow<*> {
        val chatId = ChatId.fromContact(contactAccountId)
        val seenChanges = chatSeenRepository.subscribeSeenToSend(chatId).distinctUntilChangedBy { it?.messageId }
        val chatOpenings = chatActiveTracker.subscribe().filter { it == chatId }

        return merge(seenChanges, chatOpenings)
            .debounce(SEND_DELAY)
            .onEach { sendIfNotDelivered(contactAccountId, send) }
    }

    private suspend fun sendIfNotDelivered(contactAccountId: AccountId, send: (EncodedMessage) -> Unit) {
        runCancellableCatching { findSeenToSend(contactAccountId) }
            .flatMap { seen -> seen?.toEncodedMessage()?.map(send) ?: Result.success(Unit) }
            .logFailure("Failed to send a seen message to $contactAccountId")
    }

    private suspend fun findSeenToSend(contactAccountId: AccountId): ChatMessageStatementContent.Seen? {
        val contact = contactsRepository.getContact(contactAccountId) ?: return null
        if (!contact.isActiveChat()) return null

        val seen = chatSeenRepository.subscribeSeenToSend(ChatId.fromContact(contactAccountId)).first()
        return seen?.takeIf { it.messageId != deliveredSeenMessageId }
    }
}
