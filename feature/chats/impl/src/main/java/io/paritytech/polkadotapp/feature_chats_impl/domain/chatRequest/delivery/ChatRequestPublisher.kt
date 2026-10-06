package io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery

import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.ChatEngine
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.OutgoingChatRequestPayload
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.OutgoingChatRequestService
import io.paritytech.polkadotapp.feature_statement_store_api.data.Statement
import io.paritytech.polkadotapp.feature_statement_store_api.domain.notificationAllocator.NOTIFICATION_STATEMENT_MAX_SIZE_BYTES
import timber.log.Timber
import javax.inject.Inject

// Generous upper bound on what a chat request statement adds around its data: proof, expiry, topics, length prefixes.
private const val STATEMENT_ENVELOPE_BYTES = 512

/** Puts a recorded outgoing chat request on the statement store and records how it got there. */
class ChatRequestPublisher @Inject constructor(
    private val outgoingChatRequestService: OutgoingChatRequestService,
    private val chatRequestRepository: ChatRequestRepository,
    private val contactsRepository: ContactsRepository,
    private val chatEngine: ChatEngine,
    private val signers: ChatRequestDeliverySigners,
) {
    /** Signs with our own account: identical in size to the anonymously signed statement, and never submitted. */
    suspend fun fitsNotificationStatement(request: ChatRequest, payload: OutgoingChatRequestPayload): Result<Boolean> {
        return signers.usernameSigner(payload.contact)
            .flatMap { signer -> outgoingChatRequestService.prepareStatement(request, payload, signer.prover) }
            .map { statement -> statement.encodedSizeUpperBound() <= NOTIFICATION_STATEMENT_MAX_SIZE_BYTES }
    }

    suspend fun publishFirstDelivery(
        request: ChatRequest,
        payload: OutgoingChatRequestPayload,
        signer: ChatRequestDeliverySigner,
    ): Result<Unit> {
        return submit(request, payload, signer).flatMap {
            runCancellableCatching {
                contactsRepository.withTransaction {
                    chatRequestRepository.updateDelivery(request.id, signer.delivery)
                    chatEngine.updateMessageStatus(request.id, ChatMessage.Status.IS_SENT)
                }
            }
        }
    }

    suspend fun republish(
        request: ChatRequest,
        payload: OutgoingChatRequestPayload,
        signer: ChatRequestDeliverySigner,
    ): Result<Unit> {
        return submit(request, payload, signer).flatMap {
            runCancellableCatching { chatRequestRepository.updateDelivery(request.id, signer.delivery) }
        }
    }

    suspend fun markUndeliverable(request: ChatRequest): Result<Unit> = runCancellableCatching {
        Timber.w("Chat request ${request.id} exceeds $NOTIFICATION_STATEMENT_MAX_SIZE_BYTES bytes; marking undeliverable")
        contactsRepository.withTransaction {
            chatRequestRepository.updateDelivery(request.id, ChatRequest.Delivery.Failed)
            chatEngine.updateMessageStatus(request.id, ChatMessage.Status.DELIVERY_FAILED)
        }
    }

    private suspend fun submit(
        request: ChatRequest,
        payload: OutgoingChatRequestPayload,
        signer: ChatRequestDeliverySigner,
    ): Result<Unit> {
        return outgoingChatRequestService.prepareStatement(request, payload, signer.prover)
            .flatMap { statement -> outgoingChatRequestService.submitStatement(statement) }
    }

    private fun Statement.encodedSizeUpperBound(): Int = body.data.size + STATEMENT_ENVELOPE_BYTES
}
