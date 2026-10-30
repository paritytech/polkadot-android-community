package io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.cancel

import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ChainConnectionRefCounter
import io.paritytech.polkadotapp.chains.multiNetwork.connection.withConnectionEnabled
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.flatRecover
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.RemoveContactUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequestId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_api.domain.model.pendingChatRequestIdOrThrow
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatDraftRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.getByIdOrThrow
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.getContactOrThrow
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.OutgoingChatRequestService
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestDeliveryKeypairDerivation
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestDeliverySigners
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestPublisher
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.OutgoingChatRequestNotPending
import io.paritytech.polkadotapp.feature_chats_impl.domain.identitySigning.IdentitySigningGate
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.renewalPeriod
import io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.signer.ContactChatSigners
import io.paritytech.polkadotapp.feature_statement_store_api.data.StatementSubmitError
import io.paritytech.polkadotapp.feature_statement_store_api.domain.StatementStoreMessageProver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

private val CHANNEL_PRIORITY_RETRY_DELAY = 1.seconds
private const val CANCEL_CONNECTION_TAG = "ChatRequestCancel"

@Singleton
class OutgoingChatRequestCancelService @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val chatRequestRepository: ChatRequestRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatDraftRepository: ChatDraftRepository,
    private val removeContactUseCase: RemoveContactUseCase,
    private val chatSigners: ContactChatSigners,
    private val publisher: ChatRequestPublisher,
    private val outgoingChatRequestService: OutgoingChatRequestService,
    private val keypairDerivation: ChatRequestDeliveryKeypairDerivation,
    private val signers: ChatRequestDeliverySigners,
    private val identitySigningGate: IdentitySigningGate,
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
            val period = request.delivery.renewalPeriod ?: return@flatMap Result.success(Unit)

            publishFromNotificationAccount(contact, request, period)
                .flatMap { publishFromOurAccountIfAllowed(contact, request, period) }
        }
    }

    private suspend fun publishFromNotificationAccount(contact: Contact, request: ChatRequest, period: UInt): Result<Unit> {
        return keypairDerivation.deliveryAccount(request.id, period)
            .flatMap { account -> submitCancel(contact, request, signers.anonymousSigner(account).prover) }
            .flatRecover { error ->
                if (error !is StatementSubmitError.NoAllowance) return@flatRecover Result.failure(error)

                Timber.i("chatRequestCancel: the period $period account of request ${request.id} has no allowance left; skipping it")
                Result.success(Unit)
            }
    }

    private suspend fun publishFromOurAccountIfAllowed(contact: Contact, request: ChatRequest, period: UInt): Result<Unit> {
        if (!identitySigningGate.hasConsent(contact.accountId)) return Result.success(Unit)

        return signers.usernameSigner(contact, period).flatMap { signer -> submitCancel(contact, request, signer.prover) }
    }

    private suspend fun submitCancel(contact: Contact, request: ChatRequest, prover: StatementStoreMessageProver): Result<Unit> {
        return outgoingChatRequestService.deliverChatRequestCancel(request, contact, prover).flatRecover { error ->
            if (error !is StatementSubmitError.ChannelPriorityTooLow) return@flatRecover Result.failure(error)

            delay(CHANNEL_PRIORITY_RETRY_DELAY)
            outgoingChatRequestService.deliverChatRequestCancel(request, contact, prover)
        }
    }

    private suspend fun removeLocally(contactAccountId: AccountId, requestId: ChatRequestId) {
        val chatId = ChatId.fromContact(contactAccountId)

        runCancellableCatching {
            contactsRepository.withTransaction {
                chatMessageRepository.deleteAllChatMessages(chatId)
                removeContactUseCase.removeContact(contactAccountId)
                chatDraftRepository.deleteDraft(chatId)
                chatRequestRepository.delete(requestId)
            }
        }
            .flatMap { chatSigners.release(contactAccountId) }
            .onSuccess { Timber.i("chatRequestCancel: request $requestId cancelled") }
            .logFailure("Chat request $requestId was cancelled on the store but not removed locally")
    }

    private suspend fun revert(requestId: ChatRequestId, error: Throwable) {
        Timber.w(error, "chatRequestCancel: cancel of request $requestId failed; the request is back")

        runCancellableCatching { chatRequestRepository.revertCancelling(requestId) }
            .logFailure("Chat request $requestId could not be reverted from cancelling")
    }
}
