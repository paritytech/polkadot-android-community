package io.paritytech.polkadotapp.feature_chats_impl.domain.seen

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.ChatActiveTracker
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
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

private val DEBOUNCE_DELAY = 1.seconds

class SeenWatermarkService @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val chatSeenRepository: ChatSeenRepository,
    private val chatActiveTracker: ChatActiveTracker,
) {
    fun scheduleWatermarks(contactAccountId: AccountId, send: (EncodedMessage) -> Unit): Flow<*> {
        val chatId = ChatId.fromContact(contactAccountId)
        val stateChanges = chatSeenRepository.subscribeSeenWatermarkState(chatId).distinctUntilChanged()
        val chatOpenings = chatActiveTracker.subscribe().filter { it == chatId }

        return merge(stateChanges, chatOpenings)
            .debounce(DEBOUNCE_DELAY)
            .onEach { sendIfAdvanced(contactAccountId, send) }
    }

    private suspend fun sendIfAdvanced(contactAccountId: AccountId, send: (EncodedMessage) -> Unit) {
        runCancellableCatching { fetchState(contactAccountId) }
            .flatMap { state ->
                state?.takeIf { it.isAdvanced() }?.toWatermark()?.toEncodedMessage()?.map(send) ?: Result.success(Unit)
            }
            .logFailure("Failed to send seen watermark to $contactAccountId")
    }

    private suspend fun fetchState(contactAccountId: AccountId): SeenWatermarkState? {
        val contact = contactsRepository.getContact(contactAccountId) ?: return null
        if (!contact.isActiveChat()) return null

        return chatSeenRepository.subscribeSeenWatermarkState(ChatId.fromContact(contactAccountId)).first()
    }

    private fun SeenWatermarkState.isAdvanced(): Boolean {
        return order > lastSentOrder || order == lastSentOrder && timestamp > lastSentTimestamp
    }

    private fun SeenWatermarkState.toWatermark(): ChatMessageStatementContent.Seen {
        return ChatMessageStatementContent.Seen(messageId.lowercase(), timestamp.toULong())
    }
}
