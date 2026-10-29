package io.paritytech.polkadotapp.feature_chats_impl.domain.seen

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.ChatActiveTracker
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_impl.data.model.decodeSeenMessageId
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toEncodedSeenMessage
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatSeenRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.LastReadMessage
import io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.isActiveChat
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
        messages.mapNotNull { it.decodeSeenMessageId() }.forEach { messageId ->
            runCancellableCatching { chatSeenRepository.saveReceivedSeen(chatId, messageId) }
                .logFailure("Failed to save a seen message from $contactAccountId")
        }
    }

    fun saveDelivered(messages: List<EncodedMessage>) {
        messages.mapNotNull { it.decodeSeenMessageId() }.lastOrNull()?.let { deliveredSeenMessageId = it }
    }

    fun sendSeenMessages(contactAccountId: AccountId, send: (EncodedMessage) -> Unit): Flow<*> {
        val chatId = ChatId.fromContact(contactAccountId)
        val lastReadChanges = chatSeenRepository.subscribeLastReadIncomingMessage(chatId).distinctUntilChangedBy { it?.id }
        val chatOpenings = chatActiveTracker.subscribe().filter { it == chatId }

        return merge(lastReadChanges, chatOpenings)
            .debounce(SEND_DELAY)
            .onEach { sendIfNotDelivered(contactAccountId, send) }
    }

    private suspend fun sendIfNotDelivered(contactAccountId: AccountId, send: (EncodedMessage) -> Unit) {
        runCancellableCatching { findMessageToSendSeen(contactAccountId) }
            .flatMap { message -> message?.toEncodedSeenMessage()?.map(send) ?: Result.success(Unit) }
            .logFailure("Failed to send a seen message to $contactAccountId")
    }

    private suspend fun findMessageToSendSeen(contactAccountId: AccountId): LastReadMessage? {
        val contact = contactsRepository.getContact(contactAccountId) ?: return null
        if (!contact.isActiveChat()) return null

        val message = chatSeenRepository.subscribeLastReadIncomingMessage(ChatId.fromContact(contactAccountId)).first()
        return message?.takeIf { !it.id.equals(deliveredSeenMessageId, ignoreCase = true) }
    }
}
