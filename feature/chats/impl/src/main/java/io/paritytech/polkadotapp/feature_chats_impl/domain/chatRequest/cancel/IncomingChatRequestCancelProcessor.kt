package io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.cancel

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequestId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.isIncoming
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatAcceptanceRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.IncomingChatRequestStatement
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.IncomingChatRequestVerifier
import io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.signer.ContactChatSigners
import io.paritytech.polkadotapp.feature_chats_impl.domain.usecase.DeleteLocalChatUseCase
import timber.log.Timber
import javax.inject.Inject

class IncomingChatRequestCancelProcessor @Inject constructor(
    private val verifier: IncomingChatRequestVerifier,
    private val chatRequestRepository: ChatRequestRepository,
    private val contactsRepository: ContactsRepository,
    private val chatAcceptanceRepository: ChatAcceptanceRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val deleteLocalChat: DeleteLocalChatUseCase,
    private val chatSigners: ContactChatSigners,
) {
    suspend fun process(cancel: IncomingChatRequestStatement.Cancel): Result<Unit> {
        return verifier.verifyCancel(cancel).flatMap {
            runCancellableCatching { applyCancel(cancel) }
        }
    }

    private suspend fun applyCancel(cancel: IncomingChatRequestStatement.Cancel) {
        val senderAccountId = cancel.identityProof.identityAccountId
        val request = chatRequestRepository.getById(cancel.requestId)
            ?: return rememberCancelledRequest(cancel)

        if (!request.isIncoming() || cancel.timestamp <= request.timestamp || !isSentBy(request, senderAccountId)) {
            Timber.d("chatRequestCancel: cancel of request ${cancel.requestId} does not apply (status=${request.status}, direction=${request.direction})")
            return
        }

        removeRequestChat(senderAccountId, cancel.requestId)
    }

    private suspend fun rememberCancelledRequest(cancel: IncomingChatRequestStatement.Cancel) {
        val request = ChatRequest(
            welcomeMessageId = cancel.requestId,
            timestamp = cancel.timestamp,
            direction = ChatRequest.Direction.INCOMING,
            status = ChatRequest.Status.CANCELLED,
            delivery = ChatRequest.Delivery.DeliveredUntracked,
        )
        chatRequestRepository.save(request)

        Timber.i("chatRequestCancel: remembered cancelled request ${cancel.requestId} before its copy arrived")
    }

    private suspend fun isSentBy(request: ChatRequest, senderAccountId: AccountId): Boolean {
        return when (request.status) {
            ChatRequest.Status.PENDING -> contactsRepository.getContact(senderAccountId)?.pendingChatRequestId == request.id
            ChatRequest.Status.ACCEPTED -> chatAcceptanceRepository.get(senderAccountId)?.requestId == request.id
            ChatRequest.Status.DECLINED,
            ChatRequest.Status.CANCELLED -> false
        }
    }

    private suspend fun removeRequestChat(senderAccountId: AccountId, requestId: ChatRequestId) {
        val chatId = ChatId.fromContact(senderAccountId)

        val removed = contactsRepository.withTransaction {
            if (chatMessageRepository.hasConversationMessages(chatId)) return@withTransaction false

            chatRequestRepository.updateStatus(requestId, ChatRequest.Status.CANCELLED)
            deleteLocalChat(chatId)
            true
        }
        if (!removed) {
            Timber.i("chatRequestCancel: chat of request $requestId already has messages; keeping it")
            return
        }

        Timber.i("chatRequestCancel: request $requestId was cancelled by its sender")
        chatSigners.release(senderAccountId).logFailure("Failed to release chat signer after request $requestId was cancelled")
    }
}
