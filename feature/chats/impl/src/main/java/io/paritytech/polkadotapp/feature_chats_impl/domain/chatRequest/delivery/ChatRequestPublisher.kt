package io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery

import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequestId
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.ChatEngine
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.OutgoingChatRequestPayload
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.OutgoingChatRequestService
import io.paritytech.polkadotapp.feature_statement_store_api.domain.notificationAllocator.NOTIFICATION_STATEMENT_MAX_SIZE
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class OutgoingChatRequestNotPending(requestId: ChatRequestId) : Throwable("Chat request $requestId is no longer pending")

/** Puts a recorded outgoing chat request on the statement store and records how it got there. */
@Singleton
class ChatRequestPublisher @Inject constructor(
    private val outgoingChatRequestService: OutgoingChatRequestService,
    private val chatRequestRepository: ChatRequestRepository,
    private val contactsRepository: ContactsRepository,
    private val chatEngine: ChatEngine,
) {
    private val publishLocks = ConcurrentHashMap<ChatRequestId, Mutex>()

    suspend fun fitsNotificationStatement(request: ChatRequest, payload: OutgoingChatRequestPayload): Result<Boolean> {
        return outgoingChatRequestService.fitsStatementSize(request, payload, NOTIFICATION_STATEMENT_MAX_SIZE)
            .onSuccess { fits -> Timber.d("chatRequestPublisher: request ${request.id} fits $NOTIFICATION_STATEMENT_MAX_SIZE: $fits") }
    }

    suspend fun publishFirstDelivery(
        request: ChatRequest,
        payload: OutgoingChatRequestPayload,
        signer: ChatRequestDeliverySigner,
    ): Result<Unit> {
        return submitWhilePending(request, payload, signer) {
            contactsRepository.withTransaction {
                chatRequestRepository.updateDelivery(request.id, signer.delivery)
                chatEngine.updateMessageStatus(request.id, ChatMessage.Status.IS_SENT)
            }
        }
    }

    suspend fun republish(
        request: ChatRequest,
        payload: OutgoingChatRequestPayload,
        signer: ChatRequestDeliverySigner,
    ): Result<Unit> {
        return submitWhilePending(request, payload, signer) {
            chatRequestRepository.updateDelivery(request.id, signer.delivery)
        }
    }

    suspend fun markUndeliverable(request: ChatRequest): Result<Unit> {
        Timber.w("Chat request ${request.id} exceeds $NOTIFICATION_STATEMENT_MAX_SIZE; marking undeliverable")
        return markFailed(request)
    }

    suspend fun markFailed(request: ChatRequest): Result<Unit> = runCancellableCatching {
        contactsRepository.withTransaction {
            chatRequestRepository.updateDelivery(request.id, ChatRequest.Delivery.Failed)
            chatEngine.updateMessageStatus(request.id, ChatMessage.Status.DELIVERY_FAILED)
        }
    }

    suspend fun awaitPublishing(requestId: ChatRequestId) {
        publishLocks.getOrPut(requestId) { Mutex() }.withLock { }
    }

    private suspend fun submitWhilePending(
        request: ChatRequest,
        payload: OutgoingChatRequestPayload,
        signer: ChatRequestDeliverySigner,
        recordDelivery: suspend () -> Unit,
    ): Result<Unit> {
        return publishLocks.getOrPut(request.id) { Mutex() }.withLock {
            ensurePending(request.id)
                .flatMap { submit(request, payload, signer) }
                .flatMap { runCancellableCatching { recordDelivery() } }
        }
    }

    private suspend fun ensurePending(requestId: ChatRequestId): Result<Unit> {
        return runCancellableCatching { chatRequestRepository.getById(requestId)?.status }.flatMap { status ->
            if (status == ChatRequest.Status.PENDING) Result.success(Unit) else Result.failure(OutgoingChatRequestNotPending(requestId))
        }
    }

    private suspend fun submit(
        request: ChatRequest,
        payload: OutgoingChatRequestPayload,
        signer: ChatRequestDeliverySigner,
    ): Result<Unit> {
        Timber.i("chatRequestPublisher: submitting request ${request.id} as ${signer.delivery}")

        return outgoingChatRequestService.deliverChatRequest(request, payload, signer.prover)
    }
}
