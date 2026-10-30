package io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.cancel

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequestId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.isIncoming
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatAcceptanceRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatDraftRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.IncomingRequestProcessingResult
import io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.signer.ContactChatSigners
import timber.log.Timber
import javax.inject.Inject

class IncomingChatRequestCancelProcessor @Inject constructor(
    private val chatRequestRepository: ChatRequestRepository,
    private val contactsRepository: ContactsRepository,
    private val chatAcceptanceRepository: ChatAcceptanceRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatDraftRepository: ChatDraftRepository,
    private val chatSigners: ContactChatSigners,
) {
    suspend fun process(
        senderAccountId: AccountId,
        requestId: ChatRequestId,
        cancelTimestamp: Long,
    ): IncomingRequestProcessingResult {
        val request = chatRequestRepository.getById(requestId)
            ?: return rememberCancelledRequest(requestId, cancelTimestamp)

        if (!request.isIncoming() || cancelTimestamp <= request.timestamp || !isSentBy(request, senderAccountId)) {
            Timber.d("chatRequestCancel: cancel of request $requestId does not apply (status=${request.status}, direction=${request.direction})")
            return IncomingRequestProcessingResult.Ignored.CancelNotApplicable
        }

        return removeRequestChat(senderAccountId, requestId)
    }

    private suspend fun rememberCancelledRequest(requestId: ChatRequestId, cancelTimestamp: Long): IncomingRequestProcessingResult {
        val request = ChatRequest(
            welcomeMessageId = requestId,
            timestamp = cancelTimestamp,
            direction = ChatRequest.Direction.INCOMING,
            status = ChatRequest.Status.CANCELLED,
            delivery = ChatRequest.Delivery.DeliveredUntracked,
        )
        chatRequestRepository.save(request)

        return IncomingRequestProcessingResult.RequestCancelled(requestId)
    }

    private suspend fun isSentBy(request: ChatRequest, senderAccountId: AccountId): Boolean {
        return when (request.status) {
            ChatRequest.Status.PENDING -> contactsRepository.getContact(senderAccountId)?.pendingChatRequestId == request.id
            ChatRequest.Status.ACCEPTED -> chatAcceptanceRepository.get(senderAccountId)?.requestId == request.id
            ChatRequest.Status.DECLINED,
            ChatRequest.Status.CANCELLED,
            ChatRequest.Status.CANCELLING -> false
        }
    }

    private suspend fun removeRequestChat(senderAccountId: AccountId, requestId: ChatRequestId): IncomingRequestProcessingResult {
        val chatId = ChatId.fromContact(senderAccountId)

        val removed = contactsRepository.withTransaction {
            if (chatMessageRepository.hasConversationMessages(chatId)) return@withTransaction false

            chatRequestRepository.updateStatus(requestId, ChatRequest.Status.CANCELLED)
            chatMessageRepository.deleteAllChatMessages(chatId)
            contactsRepository.deleteContact(senderAccountId)
            chatDraftRepository.deleteDraft(chatId)
            true
        }
        if (!removed) {
            Timber.i("chatRequestCancel: chat of request $requestId already has messages; keeping it")
            return IncomingRequestProcessingResult.Ignored.CancelNotApplicable
        }

        chatSigners.release(senderAccountId).logFailure("Failed to release chat signer after request $requestId was cancelled")
        return IncomingRequestProcessingResult.RequestCancelled(requestId)
    }
}
