package io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.cancel

import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ChainConnectionRefCounter
import io.paritytech.polkadotapp.chains.multiNetwork.connection.withConnectionEnabled
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.exponentialRetryDelay
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.flattenResult
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequestId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_api.domain.model.isCancellingOutgoing
import io.paritytech.polkadotapp.feature_chats_api.domain.model.pendingChatRequestIdOrThrow
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.getByIdOrThrow
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.getContactOrThrow
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.OutgoingChatRequestService
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestDeliveryKeypairDerivation
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestDeliverySigner
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestDeliverySigners
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestPublisher
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.transport.StoredChatRequestCopy
import io.paritytech.polkadotapp.feature_chats_impl.domain.error.OutgoingChatRequestNotPending
import io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.signer.ContactChatSigners
import io.paritytech.polkadotapp.feature_chats_impl.domain.usecase.DeleteLocalChatUseCase
import io.paritytech.polkadotapp.feature_statement_store_api.data.StatementSubmitError
import io.paritytech.polkadotapp.feature_statement_store_api.domain.StatementStoreMessageProver
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.StatementExpiry
import io.paritytech.polkadotapp.feature_statement_store_api.domain.notificationAllocator.NotificationStatementAccountAllocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

private const val CANCEL_ATTEMPTS = 3
private val CANCEL_RETRY_DELAY = exponentialRetryDelay(initialDelay = 1.seconds, maxDelay = 4.seconds)
private const val CANCEL_CONNECTION_TAG = "ChatRequestCancel"

@Singleton
class OutgoingChatRequestCancelService @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val chatRequestRepository: ChatRequestRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val deleteLocalChat: DeleteLocalChatUseCase,
    private val chatSigners: ContactChatSigners,
    private val publisher: ChatRequestPublisher,
    private val outgoingChatRequestService: OutgoingChatRequestService,
    private val keypairDerivation: ChatRequestDeliveryKeypairDerivation,
    private val signers: ChatRequestDeliverySigners,
    private val allocator: NotificationStatementAccountAllocator,
    private val chainConnectionRefCounter: ChainConnectionRefCounter,
    private val knownChains: KnownChains,
    dispatchers: CoroutineDispatchers,
) : CoroutineScope {
    override val coroutineContext = dispatchers.io + SupervisorJob()

    suspend fun cancel(contactAccountId: AccountId): Result<Unit> {
        return runCancellableCatching {
            val contact = contactsRepository.getContactOrThrow(contactAccountId)
            contact to contact.pendingChatRequestIdOrThrow()
        }.flatMap { (contact, requestId) ->
            markCancelling(requestId).map { launch { cancelInBackground(contact, requestId) } }
        }.coerceToUnit()
    }

    private suspend fun markCancelling(requestId: ChatRequestId): Result<Unit> {
        return runCancellableCatching { chatRequestRepository.markCancelling(requestId) }.flatMap { marked ->
            if (marked) Result.success(Unit) else Result.failure(OutgoingChatRequestNotPending(requestId))
        }
    }

    private suspend fun cancelInBackground(contact: Contact, requestId: ChatRequestId) {
        chainConnectionRefCounter.withConnectionEnabled(knownChains.people, CANCEL_CONNECTION_TAG) {
            publisher.awaitPublishing(requestId)

            publishCancel(contact, requestId)
                .onSuccess { removeLocally(contact.accountId, requestId) }
                .onFailure { error -> revert(requestId, error) }
        }
    }

    private suspend fun publishCancel(contact: Contact, requestId: ChatRequestId): Result<Unit> {
        return runCancellableCatching { chatRequestRepository.getByIdOrThrow(requestId) }.flatMap { request ->
            if (!request.isCancellingOutgoing()) return@flatMap Result.failure(OutgoingChatRequestNotPending(requestId))

            findOwnCopies(contact, request).flatMap { copies -> cancelCopies(contact, request, copies) }
        }
    }

    private suspend fun findOwnCopies(contact: Contact, request: ChatRequest): Result<List<OwnRequestCopy>> {
        return outgoingChatRequestService.fetchStoredCopies(contact).flatMap { copies ->
            candidateSigners(contact, request).map { candidates ->
                copies.mapNotNull { copy ->
                    val signer = candidates.find { it.accountId == copy.signerAccountId }
                    if (signer == null) Timber.w("chatRequestCancel: skipping a copy of request ${request.id} from an unknown account")

                    signer?.let { OwnRequestCopy(copy, it.prover) }
                }
            }
        }
    }

    private suspend fun candidateSigners(contact: Contact, request: ChatRequest): Result<List<ChatRequestDeliverySigner>> {
        val period = allocator.currentPeriod()
        val oneShotPeriods = if (period > 0u) listOf(period, period - 1u) else listOf(period)
        val oneShotSigners = oneShotPeriods.map { oneShotPeriod ->
            keypairDerivation.deliveryAccount(request.id, oneShotPeriod).map(signers::anonymousSigner)
        }

        return (oneShotSigners + signers.usernameSigner(contact, period)).flattenResult()
    }

    private suspend fun cancelCopies(contact: Contact, request: ChatRequest, copies: List<OwnRequestCopy>): Result<Unit> {
        var isAnyAccepted = false
        var lastFailure: Throwable? = null

        for (copy in copies) {
            submitCancelWithRetries(contact, request, copy)
                .onSuccess { isAnyAccepted = true }
                .onFailure { error ->
                    if (error is StatementSubmitError.NoAllowance) {
                        Timber.i("chatRequestCancel: a copy of request ${request.id} has no allowance left; the store drops it")
                    } else {
                        Timber.w(error, "chatRequestCancel: a copy of request ${request.id} was not cancelled")
                        lastFailure = error
                    }
                }
        }

        val failure = lastFailure
        return if (failure != null && !isAnyAccepted) Result.failure(failure) else Result.success(Unit)
    }

    private suspend fun submitCancelWithRetries(contact: Contact, request: ChatRequest, copy: OwnRequestCopy): Result<Unit> {
        var attempt = 1

        while (true) {
            val expiry = StatementExpiry.nextAfter(copy.stored.expiry)
            val result = outgoingChatRequestService.deliverChatRequestCancel(request, contact, copy.prover, expiry)
            val error = result.exceptionOrNull()
            if (error == null || !error.allowsCancelRetry() || attempt == CANCEL_ATTEMPTS) return result

            delay(CANCEL_RETRY_DELAY.delayFor(attempt))
            attempt++
        }
    }

    private fun Throwable.allowsCancelRetry(): Boolean {
        return when (this) {
            is StatementSubmitError.ChannelPriorityTooLow -> true
            is StatementSubmitError -> false
            else -> true
        }
    }

    private suspend fun removeLocally(contactAccountId: AccountId, requestId: ChatRequestId) {
        val chatId = ChatId.fromContact(contactAccountId)

        runCancellableCatching {
            contactsRepository.withTransaction {
                if (!canRemoveChat(requestId, chatId)) return@withTransaction false

                deleteLocalChat(chatId)
                chatRequestRepository.delete(requestId)
                true
            }
        }
            .flatMap { removed ->
                if (!removed) {
                    Timber.i("chatRequestCancel: request $requestId was accepted and its chat has messages; keeping it")
                    return@flatMap Result.success(Unit)
                }

                chatSigners.release(contactAccountId).onSuccess { Timber.i("chatRequestCancel: request $requestId cancelled") }
            }
            .logFailure("Chat request $requestId was cancelled on the store but not removed locally")
    }

    private suspend fun canRemoveChat(requestId: ChatRequestId, chatId: ChatId): Boolean {
        val request = chatRequestRepository.getById(requestId) ?: return false

        return request.isCancellingOutgoing() || !chatMessageRepository.hasConversationMessages(chatId)
    }

    private suspend fun revert(requestId: ChatRequestId, error: Throwable) {
        Timber.w(error, "chatRequestCancel: cancel of request $requestId failed; the request is back")

        runCancellableCatching { chatRequestRepository.revertCancelling(requestId) }
            .logFailure("Chat request $requestId could not be reverted from cancelling")
    }
}

private class OwnRequestCopy(val stored: StoredChatRequestCopy, val prover: StatementStoreMessageProver)
