package io.paritytech.polkadotapp.feature_chats_impl.domain.seen

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.ChatActiveTracker
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_impl.data.model.decodeSeenContent
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toEncodedMessage
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatSeenRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.isActiveChat
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.ChatMessageStatementContent
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private val SEND_DELAY = 1.seconds

class SeenMessageSender @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val chatSeenRepository: ChatSeenRepository,
    private val chatActiveTracker: ChatActiveTracker,
) {
    fun sendOnAdvance(contactAccountId: AccountId, send: (EncodedMessage) -> Unit): Flow<*> {
        val chatId = ChatId.fromContact(contactAccountId)
        val lastSeenChanges = chatSeenRepository.subscribeLastSeenIncomingMessage(chatId).distinctUntilChanged()
        val chatOpenings = chatActiveTracker.subscribe().filter { it == chatId }

        return merge(lastSeenChanges, chatOpenings)
            .debounce(SEND_DELAY)
            .onEach { sendIfAdvanced(contactAccountId, send) }
    }

    suspend fun recordDelivered(contactAccountId: AccountId, messages: List<EncodedMessage>) {
        if (messages.isEmpty()) return

        runCancellableCatching { raiseLastSentSeen(contactAccountId, messages) }
            .logFailure("Failed to record delivered seen messages to $contactAccountId")
    }

    private suspend fun sendIfAdvanced(contactAccountId: AccountId, send: (EncodedMessage) -> Unit) {
        runCancellableCatching { findUnsentSeen(contactAccountId) }
            .flatMap { seen -> seen?.toEncodedMessage()?.map(send) ?: Result.success(Unit) }
            .logFailure("Failed to send a seen message to $contactAccountId")
    }

    private suspend fun findUnsentSeen(contactAccountId: AccountId): ChatMessageStatementContent.Seen? {
        val contact = contactsRepository.getContact(contactAccountId) ?: return null
        if (!contact.isActiveChat()) return null

        val chatId = ChatId.fromContact(contactAccountId)
        val lastSeen = chatSeenRepository.subscribeLastSeenIncomingMessage(chatId).first() ?: return null
        val lastSent = chatSeenRepository.getLastSentSeen(contactAccountId)
        if (lastSent != null && lastSeen.position <= lastSent) return null

        return ChatMessageStatementContent.Seen(lastSeen.id.lowercase(), lastSeen.position.timestamp.toULong())
    }

    private suspend fun raiseLastSentSeen(contactAccountId: AccountId, messages: List<EncodedMessage>) {
        val chatId = ChatId.fromContact(contactAccountId)

        messages.mapNotNull { it.decodeSeenContent() }.forEach { seen ->
            val sortOrder = chatSeenRepository.getIncomingSortOrder(chatId, seen.messageId) ?: return@forEach
            chatSeenRepository.raiseLastSentSeen(contactAccountId, MessagePosition(sortOrder, seen.timestamp.toLong()))
        }
    }
}
