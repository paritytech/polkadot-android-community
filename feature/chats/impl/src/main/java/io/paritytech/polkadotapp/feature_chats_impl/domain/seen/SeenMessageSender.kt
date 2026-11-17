package io.paritytech.polkadotapp.feature_chats_impl.domain.seen

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.common.utils.throttleLatest
import io.paritytech.polkadotapp.feature_chats_api.domain.ChatActiveTracker
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toEncodedSeenMessage
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatSeenRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.LastSeenMessage
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.LastSentSeen
import io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.isActiveChat
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private val SEND_WINDOW = 1.seconds

class SeenMessageSender @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val chatSeenRepository: ChatSeenRepository,
    private val chatActiveTracker: ChatActiveTracker,
) {
    fun sendSeenMessages(contactAccountId: AccountId, send: (EncodedMessage) -> Unit): Flow<*> {
        val chatId = ChatId.fromContact(contactAccountId)

        return chatActiveTracker.subscribe()
            .map { activeChatId -> activeChatId == chatId }
            .distinctUntilChanged()
            .flatMapLatest { isOpen -> if (isOpen) lastSeenChanges(chatId) else emptyFlow() }
            .throttleLatest(SEND_WINDOW)
            .onEach { sendIfNotDelivered(contactAccountId, send) }
    }

    private fun lastSeenChanges(chatId: ChatId): Flow<LastSeenMessage?> {
        return chatSeenRepository.subscribeLastSeenIncomingMessage(chatId).distinctUntilChangedBy { it?.id }
    }

    private suspend fun sendIfNotDelivered(contactAccountId: AccountId, send: (EncodedMessage) -> Unit) {
        runCancellableCatching { findMessageToSendSeen(contactAccountId) }
            .flatMap { message -> message?.toEncodedSeenMessage()?.map(send) ?: Result.success(Unit) }
            .logFailure("Failed to send a seen message to $contactAccountId")
    }

    private suspend fun findMessageToSendSeen(contactAccountId: AccountId): LastSeenMessage? {
        val contact = contactsRepository.getContact(contactAccountId) ?: return null
        if (!contact.isActiveChat()) return null

        val chatId = ChatId.fromContact(contactAccountId)
        val message = chatSeenRepository.getLastSeenIncomingMessage(chatId) ?: return null
        val lastSentSeen = chatSeenRepository.getLastSentSeen(chatId)
        return message.takeIf { it.isNewerThan(lastSentSeen) }
    }

    private fun LastSeenMessage.isNewerThan(lastSentSeen: LastSentSeen?): Boolean {
        if (lastSentSeen == null) return true

        return sortOrder > lastSentSeen.sortOrder || (sortOrder == lastSentSeen.sortOrder && timestamp > lastSentSeen.timestamp)
    }
}
